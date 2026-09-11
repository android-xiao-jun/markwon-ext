package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.core.CodeBlockCopyTheme;
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
 *     (the language label on the left, the copy button on the right). Nothing is drawn here,
 *     {@link CodeBlockSpan} paints the header itself.
 *     A block <b>without</b> an info string and with the copy button disabled has nothing to
 *     put in that row, so it reserves <b>no height at all</b> — see
 *     {@link #CodeBlockLineSpan(MarkwonTheme, CodeBlockScrollState, int, String)};</li>
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

    /**
     * Copy-button style of the header row, or {@code null} for "no button".
     *
     * <p>Not a constructor parameter: it is pushed in by {@code CodeBlockScrollPlugin}
     * ({@code CodeBlockScrollHelper#injectCopy}) right before the text is laid out, because the
     * row is created deep inside {@code CorePlugin}, which knows nothing about the plugin. The
     * value is what decides whether a language-less block still gets a header row — see
     * {@link #headerHeight(MarkwonTheme, String, CodeBlockCopyTheme, Paint)}.
     *
     * @since 4.6.3
     */
    @Nullable
    private CodeBlockCopyTheme copyTheme;

    private float lineWidth = -1F;

    /**
     * @param language the language label a {@link #TYPE_HEADER} row is reserving room for.
     *                 When it is {@code null} or empty <b>and</b> no copy button is configured
     *                 the row has no content to show and therefore <b>reserves no height at
     *                 all</b> — otherwise every language-less block (including all indented
     *                 code blocks) would carry an empty strip at the top. Ignored by the other
     *                 two types.
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
     * Hands in the copy-button style of this block. Called once per {@code setText} (by
     * {@code CodeBlockScrollHelper#injectCopy}, on behalf of {@code CodeBlockScrollPlugin}),
     * before the text is measured — the row height depends on it.
     *
     * @since 4.6.3
     */
    public void setCopyTheme(@Nullable CodeBlockCopyTheme copyTheme) {
        this.copyTheme = copyTheme;
    }

    /**
     * Height of the header row, resolved against {@code paint}. <b>The single source of truth
     * for that number</b>: both the measurement here and the painting in
     * {@link CodeBlockSpan#drawHeader} call it, so a row and its content can never disagree
     * about their own height.
     *
     * <ul>
     *     <li>a height that was <b>not configured</b> ({@code UNSET}) as well as an explicit
     *     {@code 0} mean "no header row" — no room is reserved and nothing is painted. The row
     *     is opt-in, same rule as {@code isCodeBlockScrollbarEnabled()};</li>
     *     <li>any positive height is honoured, but raised to the height of a line of text when
     *     it is too short for its content — a row that clips its own content is never
     *     useful;</li>
     *     <li><b>exception:</b> an <em>unconfigured</em> height still resolves to one line of
     *     text when a copy button is enabled — the button needs a row to live in, and turning
     *     the feature on should not require configuring the row by hand. An explicit {@code 0}
     *     still wins and means "no row".</li>
     * </ul>
     *
     * @since 4.6.3
     */
    static int headerHeight(
            @NonNull MarkwonTheme theme,
            @Nullable String language,
            @Nullable CodeBlockCopyTheme copyTheme,
            @NonNull Paint paint) {

        final boolean hasLanguage = language != null && language.length() > 0;
        final boolean hasCopy = copyTheme != null && copyTheme.isEnabled();
        final int lineHeight = Math.round(paint.descent() - paint.ascent());
        final int configured = theme.getCodeBlockHeaderHeight();

        if (configured > 0) {
            // a configured row exists for its content only — but when there is none it stays
            // collapsed instead of becoming an empty strip
            return (hasLanguage || hasCopy) ? Math.max(configured, lineHeight) : 0;
        }

        // not configured: only the copy button can conjure the row up on its own, the language
        // label keeps following the original opt-in rule
        return hasCopy ? lineHeight : 0;
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
                // contributes nothing.
                // NB: the header height comes from the shared resolver so that this measurement
                // and CodeBlockSpan#drawHeader can never drift apart (they used to resolve the
                // same number twice, with their own ideas about the copy button).
                final int height = type == TYPE_HEADER
                        ? headerHeight(theme, language, copyTheme, paint)
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
