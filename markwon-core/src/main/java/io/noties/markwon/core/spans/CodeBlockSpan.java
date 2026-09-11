package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.scroll.CodeBlockScrollState;

/**
 * @since 3.0.0 split inline and block spans
 */
public class CodeBlockSpan extends MetricAffectingSpan implements LeadingMarginSpan {

    private final MarkwonTheme theme;
    private final Rect rect = ObjectsPool.rect();
    private final RectF rectF = ObjectsPool.rectF();
    private final Path path = ObjectsPool.path();
    private final Paint paint = ObjectsPool.paint();

    /**
     * Language (the info string of a fenced code block), drawn in the header row.
     *
     * @since 4.6.3
     */
    @Nullable
    private final String language;

    /**
     * Shared scroll state of this block. {@code null} when the block is not scrollable
     * ({@link MarkwonTheme#isCodeBlockScrollable()}), in which case no header and no
     * scrollbar are drawn.
     *
     * @since 4.6.3
     */
    @Nullable
    private final CodeBlockScrollState scrollState;

    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CodeBlockSpan(@NonNull MarkwonTheme theme) {
        this(theme, null, null);
    }

    /**
     * @since 4.6.3
     */
    public CodeBlockSpan(
            @NonNull MarkwonTheme theme,
            @Nullable String language,
            @Nullable CodeBlockScrollState scrollState) {
        this.theme = theme;
        this.language = language;
        this.scrollState = scrollState;
        this.scrollbarPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Scroll state of this block, or {@code null} when the block is not scrollable. The
     * block — not the individual lines — is what a touch has to hit, so the state is
     * reachable from here: the whole rectangle covered by this span (header, code lines,
     * footer/scrollbar) reacts to the gesture.
     *
     * @since 4.6.3
     */
    @Nullable
    public CodeBlockScrollState getScrollState() {
        return scrollState;
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        apply(p);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        apply(ds);
    }

    private void apply(TextPaint p) {
        theme.applyCodeBlockTextStyle(p);
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return theme.getCodeBlockMargin();
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {

        // NB: a sentinel row that resolves to a height of 0 (a header with
        // `codeBlockHeaderHeight = 0`, or a footer with neither a scrollbar nor a padding)
        // collapses into a zero-height line. Nothing can be painted on it, and — more
        // importantly — it must not claim the rounded corner of the edge it sits on: the
        // line next to it shares the very same top (resp. bottom) and paints it instead.
        if (top >= bottom) {
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.getCodeBlockBackgroundColor(p));

        final int left;
        final int right;
        // NB: use the layout width (the actual text area), not Canvas#getWidth.
        // The canvas is the full device surface here, so a right edge taken from
        // it lands outside the visible TextView area when the view has margins —
        // the rounded top/bottom-right corners would be clipped away entirely.
        final int width = layout != null ? layout.getWidth() : c.getWidth();
        if (dir > 0) {
            left = x;
            right = x + width;
        } else {
            left = x - width;
            right = x;
        }

        // drawLeadingMargin is called once per line of the block, so rounding
        // every call would leave a wavy left/right edge. Round the top corners
        // on the first line and the bottom corners on the last line only; the
        // span range in `text` tells us which line is being drawn.
        //
        // Note that "first/last line of the block" and "sits on the top/bottom edge of the
        // block" are *not* the same thing: a collapsed sentinel row is still the first (or
        // last) line, but it has no height, so the edge belongs to its neighbour.
        final int spanStart;
        final int spanEnd;
        final boolean firstLine;
        final boolean lastLine;
        if (text instanceof Spanned) {
            final Spanned spanned = (Spanned) text;
            spanStart = spanned.getSpanStart(this);
            spanEnd = spanned.getSpanEnd(this);
            firstLine = start <= spanStart && spanStart < end;
            lastLine = start < spanEnd && spanEnd <= end;
        } else {
            spanStart = -1;
            spanEnd = -1;
            firstLine = false;
            lastLine = false;
        }

        final boolean topEdge;
        final boolean bottomEdge;
        if (layout != null && spanStart >= 0 && spanEnd > spanStart) {
            // `top`/`bottom` are exactly `getLineTop(n)` / `getLineTop(n + 1)`, so they can be
            // compared against the bounds of the whole block. A collapsed row and the row next
            // to it both report the same edge — the collapsed one has already returned above.
            topEdge = top == layout.getLineTop(layout.getLineForOffset(spanStart));
            bottomEdge = bottom == layout.getLineTop(
                    layout.getLineForOffset(spanEnd - 1) + 1);
        } else {
            topEdge = firstLine;
            bottomEdge = lastLine;
        }

        final int radius = theme.getCodeBlockBackgroundRadius();
        final boolean rounded = radius > 0
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;

        if (rounded && (topEdge || bottomEdge)) {
            // Draw the rounded shape directly instead of clipPath + drawRect:
            // anti-aliasing applies to drawing only, a clip edge is always
            // hard/jagged. drawPath honours ANTI_ALIAS_FLAG.
            rectF.set(left, top, right, bottom);
            RoundedRectPath.build(path, rectF, radius, topEdge, topEdge, bottomEdge, bottomEdge);
            paint.setAntiAlias(true);
            c.drawPath(path, paint);
        } else {
            rect.set(left, top, right, bottom);
            c.drawRect(rect, paint);
        }

        if (scrollState == null) {
            return;
        }

        if (firstLine) {
            drawHeader(c, p, left, top);
        }

        if (lastLine) {
            drawScrollbar(c, left, right, top, bottom);
        }
    }

    /**
     * Language label of the block, drawn inside the reserved header row.
     *
     * <p>The row carries <b>no fill of its own</b> — the block background
     * ({@code codeBlockBackgroundColor}) already covers it, and a dedicated
     * {@code codeBlockHeaderBackgroundColor} would only have been a duplicate of that value.
     *
     * <p>Likewise the label reuses the code block text style: the paint is seeded from the
     * block paint, then {@link MarkwonTheme#applyCodeBlockHeaderStyle(Paint)} applies
     * {@code codeBlockTextSize} / {@code codeBlockTextColor} when they are configured and
     * leaves both alone otherwise — so an unconfigured label simply follows the code text.
     *
     * <p><b>No row, no label.</b> A height that was not configured (or was set to {@code 0})
     * resolves to {@code 0} — there is no header row at all, the same opt-in rule as the
     * scrollbar. The row then collapses to zero height, so a label painted here would not have
     * a background to sit on: it is centred on a zero-height row, which puts half of the glyphs
     * <em>above</em> the top edge of the block (over the previous paragraph) and the other half
     * <em>under</em> the background of the first code line (which is painted after it). Hence
     * the early return.
     *
     * @since 4.6.3
     */
    private void drawHeader(
            @NonNull Canvas c,
            @NonNull Paint p,
            int left,
            int top) {

        if (language == null || language.length() == 0) {
            return;
        }

        // seed from the block paint → an unconfigured label falls back to the code block
        // text color/size instead of to a hardcoded value
        headerPaint.set(p);
        theme.applyCodeBlockHeaderStyle(headerPaint);

        // NB: the resolved height is never smaller than a line of text, so centring the label
        // in the row can never clip it — and a height of 0 means "no row", in which case there
        // is nothing to paint at all.
        final float height = theme.getCodeBlockHeaderHeight(p);
        if (height <= 0F) {
            return;
        }

        final Paint.FontMetrics fm = headerPaint.getFontMetrics();
        final float baseline = top + height / 2F - (fm.ascent + fm.descent) / 2F;

        c.drawText(language, left + theme.getCodeBlockPadding(), baseline, headerPaint);
    }

    /**
     * Horizontal scrollbar. Belongs to the background layer — it is painted here, by the
     * same {@code drawLeadingMargin} pass that paints the block background and always
     * <em>before</em> any glyph of the block, so the code can never cover it.
     *
     * <p>The footer row only reserves room for it ({@code scrollbarHeight + padding});
     * the bar itself is pinned to the bottom edge of the block — same as the sample
     * ({@code bounds.bottom - scrollbarHeight / 2}) — so it reads as part of the card and
     * not as one more line of the code area.
     *
     * <p>Like the header background, the scrollbar is opt-in: it is painted only when the
     * theme gave it a color ({@link MarkwonTheme#isCodeBlockScrollbarEnabled()}). Each of the
     * two parts is independent — a configured track with no thumb renders a track only, and
     * vice versa.
     *
     * @since 4.6.3
     */
    private void drawScrollbar(@NonNull Canvas c, int left, int right, int top, int bottom) {
        if (!scrollState.canScroll()) {
            return;
        }

        if (!theme.isCodeBlockScrollbarEnabled()) {
            return;
        }

        final float padding = theme.getCodeBlockPadding();
        final float trackLeft = left + padding;
        final float trackRight = right - padding;
        final float trackWidth = trackRight - trackLeft;
        if (trackWidth <= 0F) {
            return;
        }

        final float scrollbarHeight = theme.getCodeBlockScrollbarHeight();
        final float y = bottom - scrollbarHeight / 2F;

        scrollbarPaint.setStyle(Paint.Style.FILL);
        scrollbarPaint.setStrokeWidth(Math.max(1F, scrollbarHeight * 0.3F));

        final int trackColor = theme.getCodeBlockScrollbarTrackColor();
        if (trackColor != 0) {
            scrollbarPaint.setColor(trackColor);
            c.drawLine(trackLeft, y, trackRight, y, scrollbarPaint);
        }

        final int thumbColor = theme.getCodeBlockScrollbarThumbColor();
        if (thumbColor != 0) {
            final float ratio = scrollState.getViewportWidth()
                    / Math.max(scrollState.getViewportWidth(), scrollState.getContentWidth());
            final float thumbWidth = Math.min(
                    trackWidth,
                    Math.max(scrollbarHeight * 1.5F, trackWidth * ratio));
            final float thumbX = trackLeft + (trackWidth - thumbWidth) * scrollState.getScrollRatio();

            scrollbarPaint.setColor(thumbColor);
            c.drawLine(thumbX, y, thumbX + thumbWidth, y, scrollbarPaint);
        }
    }
}
