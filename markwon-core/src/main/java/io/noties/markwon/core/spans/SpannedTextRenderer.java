package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Measures and draws a text range for a {@link ReplacementSpan} that has taken over the
 * rendering of its own range.
 *
 * <p>Two things the framework does <em>not</em> do for such a range, both of them covered here:
 *
 * <ol>
 *     <li><b>{@link Canvas#drawText(CharSequence, int, int, float, float, Paint)} drops every
 *     span.</b> A {@code SpannableStringBuilder} goes through its {@code char[]} branch and even
 *     the {@code SpannedString} branch hands a plain {@code String} to the native layer — so the
 *     range has to be split on the span boundaries and drawn piece by piece, otherwise the whole
 *     line comes out in a single colour (syntax highlighting, links, emphasis all lost).</li>
 *     <li><b>Plain {@link CharacterStyle}s are never applied.</b> TextLine only walks
 *     {@code MetricAffectingSpan}s before handing the paint to a replacement span
 *     ({@code TextLine#handleRun} → {@code handleReplacement}), so a {@code ForegroundColorSpan}
 *     would simply not be there.</li>
 * </ol>
 *
 * <p><b>{@link MetricAffectingSpan}s are deliberately NOT re-applied.</b> Both the measure path
 * ({@code MeasuredParagraph#applyMetricsAffectingSpan}) and the draw path start from the pristine
 * paint and then call {@code updateMeasureState}/{@code updateDrawState} for every metric
 * affecting span of the range — the single exception being the replacement span itself, which the
 * framework skips. The paint handed to {@code getSize}/{@code draw} therefore already carries
 * them. Applying them a second time compounds relative changes: the theme styles code text as
 * {@code currentSize * 0.87}, so doing it twice silently shrinks the text to {@code 0.757x} and
 * leaves it floating inside line metrics computed for the bigger size.
 *
 * <p>NB: everything goes through the {@code char[]} overloads. The {@code CharSequence} variants
 * would resolve spans again and, because the calling span covers the very range being measured,
 * recurse infinitely (that is also why {@code StaticLayout#getDesiredWidth} must not be used).
 *
 * <p>Not thread-safe: like {@link ObjectsPool} it assumes all the drawing happens on the main
 * thread.
 *
 * @since 4.6.3
 */
final class SpannedTextRenderer {

    static final SpannedTextRenderer INSTANCE = new SpannedTextRenderer();

    private final TextPaint workPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * @param canvas when {@code null} the range is only measured
     * @return the total width of the range
     */
    float render(
            @Nullable Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            float y,
            @NonNull Paint paint) {

        final char[] buffer = new char[end - start];
        TextUtils.getChars(text, start, end, buffer, 0);

        final Spanned spanned = text instanceof Spanned ? (Spanned) text : null;
        final CharacterStyle[] styles = spanned != null
                ? spanned.getSpans(start, end, CharacterStyle.class)
                : null;

        if (!hasAppearanceStyle(styles)) {
            return draw(canvas, buffer, 0, buffer.length, x, y, paint);
        }

        // split the range on every appearance style boundary
        final List<Integer> bounds = new ArrayList<>(styles.length * 2 + 2);
        bounds.add(start);
        bounds.add(end);
        for (CharacterStyle style : styles) {
            if (!isAppearanceStyle(style)) {
                continue;
            }
            final int s = spanned.getSpanStart(style);
            final int e = spanned.getSpanEnd(style);
            if (s > start && s < end) {
                bounds.add(s);
            }
            if (e > start && e < end) {
                bounds.add(e);
            }
        }
        Collections.sort(bounds);

        float offset = 0F;
        for (int i = 0, size = bounds.size() - 1; i < size; i++) {
            final int a = bounds.get(i);
            final int b = bounds.get(i + 1);
            if (a >= b) {
                continue;
            }

            workPaint.set(paint);
            for (CharacterStyle style : styles) {
                if (isAppearanceStyle(style)
                        && spanned.getSpanStart(style) <= a
                        && spanned.getSpanEnd(style) >= b) {
                    style.updateDrawState(workPaint);
                }
            }

            final int from = a - start;
            final int count = b - a;
            draw(canvas, buffer, from, count, x + offset, y, workPaint);
            offset += workPaint.measureText(buffer, from, count);
        }

        return offset;
    }

    private static boolean hasAppearanceStyle(@Nullable CharacterStyle[] styles) {
        if (styles != null) {
            for (CharacterStyle style : styles) {
                if (isAppearanceStyle(style)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A style whose only effect is on the appearance of the glyphs. {@link MetricAffectingSpan}s
     * (and therefore {@link ReplacementSpan}s) are excluded — see the class documentation.
     */
    private static boolean isAppearanceStyle(@NonNull CharacterStyle style) {
        return !(style instanceof MetricAffectingSpan);
    }

    private static float draw(
            @Nullable Canvas canvas,
            @NonNull char[] buffer,
            int index,
            int count,
            float x,
            float y,
            @NonNull Paint paint) {
        if (canvas != null) {
            canvas.drawText(buffer, index, count, x, y, paint);
        }
        return paint.measureText(buffer, index, count);
    }

    private SpannedTextRenderer() {
    }
}
