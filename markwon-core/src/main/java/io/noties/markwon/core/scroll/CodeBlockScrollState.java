package io.noties.markwon.core.scroll;

import androidx.annotation.NonNull;

/**
 * Horizontal scroll state shared by all the lines of a <em>single</em> code block.
 *
 * <p>A scrollable code block is laid out line-by-line: every code line carries its own
 * {@link io.noties.markwon.core.spans.CodeBlockLineSpan} (a {@code ReplacementSpan} so that
 * the framework never wraps it), but they must scroll together. This object is the single
 * source of truth for the current {@link #getScrollX() scroll position}, the measured
 * {@link #getContentWidth() content width} (widest line) and the
 * {@link #getViewportWidth() viewport width} (width actually available for the text).
 *
 * <p>Lifecycle:
 * <ol>
 *     <li>{@link #beginMeasure(float)} is called once per measure pass (from
 *     {@code CodeBlockScrollPlugin#beforeSetText}) and resets the accumulated content width;</li>
 *     <li>every {@code CodeBlockLineSpan#getSize} reports its own width via
 *     {@link #reportLineWidth(float)};</li>
 *     <li>from that point on {@link #getContentWidth()} is stable until the next
 *     {@link #beginMeasure(float)} — which is what makes it safe to use while drawing
 *     and while the user drags the block (a plain {@code invalidate()} does not re-measure).</li>
 * </ol>
 *
 * @see CodeBlockScrollPlugin
 * @since 4.6.3
 */
public class CodeBlockScrollState {

    private float viewportWidth;
    private float contentWidth;
    private float scrollX;

    /**
     * Starts a new measure pass. Must be called <em>before</em> the text is laid out,
     * otherwise {@link #getContentWidth()} would keep growing across passes.
     *
     * @param viewportWidth width (in pixels) available for the code text, paddings excluded
     */
    public void beginMeasure(float viewportWidth) {
        this.viewportWidth = Math.max(0F, viewportWidth);
        this.contentWidth = 0F;
    }

    /**
     * Called by every line of the block during measurement.
     */
    public void reportLineWidth(float width) {
        if (width > contentWidth) {
            contentWidth = width;
        }
    }

    /**
     * Width available for the code text, paddings excluded.
     */
    public float getViewportWidth() {
        return viewportWidth;
    }

    /**
     * Width of the widest line of this block.
     */
    public float getContentWidth() {
        return contentWidth;
    }

    /**
     * Maximum value {@link #getScrollX()} can take.
     */
    public float getMaxScroll() {
        return Math.max(0F, contentWidth - viewportWidth);
    }

    public float getScrollX() {
        return scrollX;
    }

    public boolean canScroll() {
        return getMaxScroll() > 0F;
    }

    /**
     * @return {@code true} if the scroll position actually changed
     */
    public boolean scrollBy(float dx) {
        final float max = getMaxScroll();
        float value = scrollX + dx;
        if (value < 0F) {
            value = 0F;
        } else if (value > max) {
            value = max;
        }
        final boolean changed = Math.abs(value - scrollX) >= 0.5F;
        scrollX = value;
        return changed;
    }

    public void setScrollX(float scrollX) {
        final float max = getMaxScroll();
        if (scrollX < 0F) {
            this.scrollX = 0F;
        } else if (scrollX > max) {
            this.scrollX = max;
        } else {
            this.scrollX = scrollX;
        }
    }

    /**
     * Scroll position normalized to {@code [0, 1]}, used to place the scrollbar thumb.
     */
    public float getScrollRatio() {
        final float max = getMaxScroll();
        return max <= 0F ? 0F : scrollX / max;
    }
}
