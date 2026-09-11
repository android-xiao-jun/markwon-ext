package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.scroll.CodeBlockScrollState;

/**
 * Takes over the rendering of a <em>single</em> line of a scrollable code block.
 *
 * <p>Why one span per line: a {@code ReplacementSpan} can never cover more than one line
 * (the framework treats it as an unbreakable unit), so the whole block cannot be replaced
 * by a single span without removing the code text from the {@code Spanned}. Per-line spans
 * keep the text intact — selection, copy and the byte-identical requirement of
 * {@code Markwon#appendMarkdown} all keep working — while still giving us full control over
 * the drawing so the line can be clipped and translated.
 *
 * <ul>
 *     <li>{@link #TYPE_HEADER} — the leading sentinel line, reserves room for the header
 *     (language label). Nothing is drawn here, {@link CodeBlockSpan} paints the header itself.
 *     A block <b>without</b> an info string has nothing to put in that row, so it reserves
 *     <b>no height at all</b> — see {@link #CodeBlockLineSpan(MarkwonTheme, CodeBlockScrollState, int, String)};</li>
 *     <li>{@link #TYPE_CODE} — an actual code line: never wrapped, clipped to the viewport
 *     and translated by {@code -scrollX};</li>
 *     <li>{@link #TYPE_FOOTER} — the trailing sentinel line, reserves room for the
 *     scrollbar + bottom padding.</li>
 * </ul>
 *
 * @see CodeBlockSpan
 * @since 4.6.3
 */
public class CodeBlockLineSpan extends ReplacementSpan {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_CODE = 1;
    public static final int TYPE_FOOTER = 2;

    private final MarkwonTheme theme;
    private final CodeBlockScrollState state;
    private final int type;

    /**
     * Content of the header row (the language label of the fenced code block), or {@code null}
     * when the block has no info string. Only read by {@link #TYPE_HEADER}.
     *
     * @since 4.6.3
     */
    @Nullable
    private final String language;

    private float lineWidth = -1F;

    /**
     * @param language the language label a {@link #TYPE_HEADER} row is reserving room for.
     *                 When it is {@code null} or empty the row has no content to show and
     *                 therefore <b>reserves no height at all</b> — otherwise every
     *                 language-less block (including all indented code blocks) would carry an
     *                 empty strip at the top. Ignored by the other two types.
     * @since 4.6.3
     */
    public CodeBlockLineSpan(
            @NonNull MarkwonTheme theme,
            @NonNull CodeBlockScrollState state,
            int type,
            @Nullable String language) {
        this.theme = theme;
        this.state = state;
        this.type = type;
        this.language = language;
    }

    public int getType() {
        return type;
    }

    /**
     * Whether the header row has something to show — the very same condition
     * {@link CodeBlockSpan} uses to decide if it paints the language label, so that a row is
     * never reserved for a label that is not going to be drawn.
     *
     * @since 4.6.3
     */
    private boolean hasHeaderContent() {
        return language != null && language.length() > 0;
    }

    /**
     * Height of the footer row: the bottom padding, plus the scrollbar — but only when a
     * scrollbar <em>can</em> appear.
     *
     * <p>The footer is the <b>last</b> line of the block, so by the time it is measured every
     * code line has already reported its width and
     * {@link CodeBlockScrollState#canScroll()} is final for this pass. Reserving the scrollbar
     * track for a block whose content fits the viewport anyway is what used to leave a
     * permanent empty strip at the bottom of every short code block: {@code drawScrollbar}
     * (rightly) refuses to paint in that case, but the room had already been taken.
     *
     * @since 4.6.3
     */
    private int footerHeight() {
        final int scrollbar = theme.isCodeBlockScrollbarEnabled() && state.canScroll()
                ? theme.getCodeBlockScrollbarHeight()
                : 0;
        return scrollbar + theme.getCodeBlockPadding();
    }

    @NonNull
    public CodeBlockScrollState getState() {
        return state;
    }

    /**
     * Injects the width of the text area (Layout coordinates, {@code 0} is the left edge of
     * the text area). Called by {@code CodeBlockScrollPlugin} before every {@code setText}
     * and whenever the TextView is re-laid out.
     */
    public void setViewport(float textAreaWidth) {
        state.beginMeasure(textAreaWidth
                - theme.getCodeBlockMargin()
                - theme.getCodeBlockPadding() * 2F);
        lineWidth = -1F;
    }

    @Override
    public int getSize(
            @NonNull Paint paint,
            CharSequence text,
            int start,
            int end,
            @Nullable Paint.FontMetricsInt fm) {

        if (type != TYPE_CODE) {
            // header / footer rows: no width, but a very specific height
            if (fm != null) {
                // NB: no hardcoded pixel defaults anywhere — a size that resolves to 0 means
                // "this row does not exist": it collapses (ascent == descent == 0) and
                // contributes nothing. For the header that is the case when the height was not
                // configured (or was set to 0) and when there is no language to display.
                // NB: the header height is resolved *here*, at measure time, and
                // CodeBlockSpan#drawHeader resolves it again with the same (code block styled)
                // paint — both must agree, otherwise the row and the label drift apart.
                final int height = type == TYPE_HEADER
                        ? (hasHeaderContent() ? theme.getCodeBlockHeaderHeight(paint) : 0)
                        : footerHeight();
                fm.ascent = -height;
                fm.descent = 0;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return 0;
        }

        lineWidth = SpannedTextRenderer.INSTANCE.render(null, text, start, end, 0F, 0F, paint);
        state.reportLineWidth(lineWidth);

        final float padding = theme.getCodeBlockPadding();
        final float width = lineWidth + padding * 2F;
        final float viewport = state.getViewportWidth();

        // NB: never return more than what is available — that is what prevents the line
        // from being wrapped in the first place
        return (int) Math.ceil(viewport > 0F ? Math.min(width, viewport + padding * 2F) : width);
    }

    @Override
    public void draw(
            @NonNull Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            @NonNull Paint paint) {

        if (type != TYPE_CODE) {
            return;
        }

        if (lineWidth < 0F) {
            lineWidth = SpannedTextRenderer.INSTANCE.render(null, text, start, end, 0F, 0F, paint);
        }

        final float padding = theme.getCodeBlockPadding();
        final float viewport = state.getViewportWidth();
        final float size = viewport > 0F
                ? Math.min(lineWidth, viewport) + padding * 2F
                : lineWidth + padding * 2F;

        canvas.save();
        // clip to the content box: while scrolling the text must disappear under the
        // padding instead of bleeding to the very edge of the background
        canvas.clipRect(x + padding, top, x + size - padding, bottom);
        SpannedTextRenderer.INSTANCE.render(
                canvas, text, start, end, x + padding - state.getScrollX(), y, paint);
        canvas.restore();
    }

}
