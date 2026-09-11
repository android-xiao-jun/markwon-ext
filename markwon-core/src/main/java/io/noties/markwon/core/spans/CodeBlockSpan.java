package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.core.CodeBlockCopyTheme;
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
     * Original source of the block (the literal, before syntax highlighting) — the payload of
     * the header's copy button.
     *
     * @since 4.6.3
     */
    @Nullable
    private final String code;

    /**
     * Copy-button style of the header row, or {@code null} for "no button".
     *
     * <p>Handed over by {@code CodeBlockScrollPlugin} through the span factory — the button is
     * that plugin's feature, so its style belongs to the plugin and never travels through the
     * global {@link MarkwonTheme}.
     *
     * @since 4.6.3
     */
    @Nullable
    private final CodeBlockCopyTheme copyTheme;

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
    private final Paint copyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Touch target of the copy button, in <b>Layout</b> coordinates (the same space
     * {@code CodeBlockScrollHelper} converts a {@code MotionEvent} into). Unlike the visual
     * button it spans the whole height of the header row, so a finger does not have to land on
     * the glyph itself.
     *
     * <p>It is (re)computed while the header is painted — the drawing pass and the touch that
     * follows run on the same thread, and a touch can only reach a button that has been drawn.
     * Width {@code 0} means "no button", i.e. {@link #hitCopy(float, float)} never matches.
     *
     * @since 4.6.3
     */
    private final RectF copyBounds = new RectF();

    /**
     * {@link SystemClock#uptimeMillis()} until which the button shows
     * {@link CodeBlockCopyTheme#getSuccessText()} instead of its normal content.
     *
     * @since 4.6.3
     */
    private long copyFeedbackUntil;

    public CodeBlockSpan(@NonNull MarkwonTheme theme) {
        this(theme, null, null, null, null);
    }

    /**
     * @param code      original source of the block — what the copy button hands to the host,
     *                  see {@link #getCode()}. May be {@code null} when the caller does not
     *                  have it (the button then does nothing).
     * @param copyTheme style of the copy button, or {@code null} for "no button".
     * @since 4.6.3
     */
    public CodeBlockSpan(
            @NonNull MarkwonTheme theme,
            @Nullable String language,
            @Nullable String code,
            @Nullable CodeBlockCopyTheme copyTheme,
            @Nullable CodeBlockScrollState scrollState) {
        this.theme = theme;
        this.language = language;
        this.code = code;
        this.copyTheme = copyTheme;
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

    /**
     * <b>Original</b> source of the block — the fenced/indented literal as the author wrote
     * it, not the highlighted {@code Spanned}. This is what the copy button hands to the host,
     * which then decides what to do with it.
     *
     * @since 4.6.3
     */
    @Nullable
    public String getCode() {
        return code;
    }

    /**
     * Whether {@code (x, y)} — in <b>Layout</b> coordinates, the space
     * {@code CodeBlockScrollHelper} converts a touch into — lands on the copy button of this
     * block. {@code false} for every block that has the button disabled or not drawn yet.
     *
     * @since 4.6.3
     */
    public boolean hitCopy(float x, float y) {
        return copyBounds.width() > 0F
                && copyBounds.height() > 0F
                && copyBounds.contains(x, y);
    }

    /**
     * Makes the copy button show {@link CodeBlockCopyTheme#getSuccessText()} for the next
     * {@code durationMs} milliseconds. The caller owns the repaint (and the one that ends the
     * feedback) — see {@code CodeBlockScrollHelper}.
     *
     * @since 4.6.3
     */
    public void showCopyFeedback(long durationMs) {
        copyFeedbackUntil = SystemClock.uptimeMillis() + durationMs;
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
        //
        // In Layout coordinates the text area is exactly `[0, width]` and, for a
        // left-to-right paragraph, the left edge of the block is where `x` is. The two are
        // *not* interchangeable: `x` is the position **after** the leading margins of the
        // line it sits on — not only ours, but every one accumulated on that line, an
        // enclosing list item's included. So `x + width` overshoots the text area by however
        // much those margins add up to, which pushes the block — and the copy button pinned
        // to its right end — past the view edge, cutting the label in half. The far edge is
        // simply the far edge of the text area, i.e. `width` itself.
        final int width = layout != null ? layout.getWidth() : c.getWidth();
        if (dir > 0) {
            left = x;
            right = width;
        } else {
            left = 0;
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
            drawHeader(c, p, left, right, top);
        }

        if (lastLine) {
            drawScrollbar(c, left, right, top, bottom);
        }
    }

    /**
     * Content of the header row: the language label on the left, the copy button on the right.
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
     * <p><b>No row, no content.</b> A height that was not configured (or was set to {@code 0})
     * resolves to {@code 0} — there is no header row at all, the same opt-in rule as the
     * scrollbar. The only exception is an enabled copy button, which brings the row into
     * existence on its own. The row then collapses to zero height, so anything painted here
     * would not have a background to sit on: it is centred on a zero-height row, which puts
     * half of the glyphs
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
            int right,
            int top) {

        // seed from the block paint → an unconfigured label falls back to the code block
        // text color/size instead of to a hardcoded value
        headerPaint.set(p);
        theme.applyCodeBlockHeaderStyle(headerPaint);

        // NB: the height comes from the resolver shared with CodeBlockLineSpan#getSize, so the
        // row and its content can never disagree. 0 means "no row" (or "row with nothing in
        // it") and there is nothing to paint at all.
        final float height = CodeBlockLineSpan.headerHeight(theme, language, copyTheme, headerPaint);
        if (height <= 0F) {
            return;
        }

        final float padding = theme.getCodeBlockPadding();

        final boolean hasLanguage = language != null && language.length() > 0;
        if (hasLanguage) {
            final Paint.FontMetrics fm = headerPaint.getFontMetrics();
            final float baseline = top + height / 2F - (fm.ascent + fm.descent) / 2F;
            c.drawText(language, left + padding, baseline, headerPaint);
        }

        if (copyTheme != null && copyTheme.isEnabled()) {
            drawCopy(c, copyTheme, right - padding, top, height);
        }
    }

    /**
     * Copy button, pinned to the right end of the header row and vertically centred in it.
     *
     * <p>What is drawn, in order of precedence:
     * <ol>
     *     <li>the <b>success label</b> ({@link CodeBlockCopyTheme#getSuccessText()}) — for a
     *     moment right after a tap, see {@link #showCopyFeedback(long)};</li>
     *     <li>the configured {@link CodeBlockCopyTheme#getIcon() icon};</li>
     *     <li>the configured {@link CodeBlockCopyTheme#getText() label};</li>
     *     <li>the <b>built-in</b> vector — the common "two stacked sheets" copy glyph, drawn
     *     with the paint only, so the library needs neither a drawable resource nor an
     *     {@code appcompat} dependency (it is {@code compileOnly} here).</li>
     * </ol>
     *
     * <p>Whatever ends up being drawn, the touch target recorded in {@link #copyBounds} spans
     * the full height of the row and is widened by {@code codeBlockPadding} on both sides.
     *
     * @since 4.6.3
     */
    private void drawCopy(
            @NonNull Canvas c,
            @Nullable CodeBlockCopyTheme copyTheme,
            float rightEdge,
            int top,
            float height) {

        if (copyTheme == null) {
            return;
        }

        final String successText = copyTheme.getSuccessText();
        final boolean feedback = copyFeedbackUntil > SystemClock.uptimeMillis()
                && successText != null
                && successText.length() > 0;

        Drawable icon = null;
        String label = null;
        if (feedback) {
            label = successText;
        } else {
            icon = copyTheme.getIcon();
            if (icon != null
                    && (icon.getIntrinsicWidth() <= 0 || icon.getIntrinsicHeight() <= 0)) {
                // a drawable that cannot size itself would be laid out as 0x0
                icon = null;
            }
            if (icon == null) {
                label = copyTheme.getText();
                if (label != null && label.length() == 0) {
                    label = null;
                }
            }
        }

        copyPaint.set(headerPaint);
        copyTheme.applyTextStyle(copyPaint);

        final Paint.FontMetrics fm = copyPaint.getFontMetrics();
        // the label sets the size of the button; the icon and the built-in vector follow it,
        // so switching between text and icon never changes the visual weight of the button
        final float contentHeight = Math.min(fm.descent - fm.ascent, height);
        final float contentWidth = label != null
                ? copyPaint.measureText(label)
                : Math.max(1F, contentHeight);

        final float centerY = top + height / 2F;
        final float contentRight = rightEdge;
        final float contentLeft = contentRight - contentWidth;
        final float contentTop = centerY - contentHeight / 2F;
        final float contentBottom = centerY + contentHeight / 2F;

        if (label != null) {
            copyPaint.setStyle(Paint.Style.FILL);
            final float baseline = centerY - (fm.ascent + fm.descent) / 2F;
            c.drawText(label, contentLeft, baseline, copyPaint);
        } else if (icon != null) {
            icon.setBounds(
                    Math.round(contentLeft),
                    Math.round(contentTop),
                    Math.round(contentRight),
                    Math.round(contentBottom));
            icon.draw(c);
        } else {
            drawCopyIcon(c, contentLeft, contentTop, contentRight, contentBottom);
        }

        // NB: the whole row on the right is the target, not just the glyph — and it stops at
        // the right edge of the block (rightEdge is already inset by the padding, adding it
        // back lands exactly on the block edge).
        final float inset = theme.getCodeBlockPadding();
        copyBounds.set(contentLeft - inset, top, contentRight + inset, top + height);
    }

    /**
     * Built-in copy glyph: two rounded sheets, the back one (bottom-right) drawn whole and the
     * front one (top-left) drawn <b>open</b>, skipping the two edges the back sheet already
     * covers.
     *
     * <p>Why not just fill the front sheet with the block color instead: that color is usually
     * translucent (default = text color × 25%), so painting it twice in the overlap would show
     * as a darker patch. Leaving the hidden edges out also keeps the glyph readable at the
     * small size a header row can afford.
     *
     * @since 4.6.3
     */
    private void drawCopyIcon(
            @NonNull Canvas c,
            float left,
            float top,
            float right,
            float bottom) {

        final float side = Math.min(right - left, bottom - top);
        if (side <= 0F) {
            return;
        }

        final float stroke = Math.max(1F, side * 0.1F);
        final float half = stroke / 2F;
        final float offset = side * 0.26F;
        final float radius = side * 0.16F;

        // ObjectsPool's path is free by now: the background of this line was drawn with it
        // before the header was
        path.reset();
        path.addRoundRect(
                left + half + offset, top + half,
                right - half, bottom - half - offset,
                radius, radius, Path.Direction.CW);

        copyPaint.setStyle(Paint.Style.STROKE);
        copyPaint.setStrokeWidth(stroke);
        copyPaint.setStrokeCap(Paint.Cap.ROUND);
        copyPaint.setStrokeJoin(Paint.Join.ROUND);
        copyPaint.setPathEffect(null);
        c.drawPath(path, copyPaint);

        // front sheet — open path: start on the (visible) sliver of the top edge, go down the
        // left side, along the bottom and up the right side, stopping where the back sheet
        // begins
        final float l = left + half;
        final float t = top + half + offset;
        final float r = right - half - offset;
        final float b = bottom - half;

        path.reset();
        path.moveTo(l + offset, t);
        path.lineTo(l + radius, t);
        path.quadTo(l, t, l, t + radius);
        path.lineTo(l, b - radius);
        path.quadTo(l, b, l + radius, b);
        path.lineTo(r - radius, b);
        path.quadTo(r, b, r, b - radius);
        path.lineTo(r, b - offset);
        c.drawPath(path, copyPaint);
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
