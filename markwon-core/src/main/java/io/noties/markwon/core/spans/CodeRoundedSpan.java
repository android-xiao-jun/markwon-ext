package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

import io.noties.markwon.core.MarkwonTheme;

/**
 * Inline-code span that owns both the background and the glyph rendering so
 * the background can be drawn with rounded corners. Used by
 * {@link io.noties.markwon.core.factory.CodeSpanFactory} when the theme
 * requests a positive {@link MarkwonTheme#getCodeBackgroundRadius() radius};
 * the plain {@link CodeSpan} keeps the original TextView-managed
 * {@code TextPaint#bgColor} background for radius == 0.
 *
 * @since 4.6.3
 */
public class CodeRoundedSpan extends ReplacementSpan {

    private static final float HORIZONTAL_PADDING_PX = 4F;

    private final MarkwonTheme theme;
    private final RectF rectF = new RectF();
    private final Path path = new Path();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint measurePaint = new TextPaint();

    // Re-entrancy guard: our span covers the measured range, so
    // StaticLayout.getDesiredWidth on a sub-sequence containing this span
    // will call getSize again (getDesiredWidth -> getSize -> getDesiredWidth
    // -> ...). When the guard is up we fall back to the char[] overload of
    // Paint#measureText, which never resolves spans and breaks the cycle.
    private boolean measuring;

    public CodeRoundedSpan(@NonNull MarkwonTheme theme) {
        this.theme = theme;
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint p) {
        apply(p);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        apply(ds);
    }

    private void apply(@NonNull TextPaint p) {
        theme.applyCodeTextStyle(p);
    }

    @Override
    public int getSize(
            @NonNull Paint paint,
            CharSequence text,
            int start,
            int end,
            android.graphics.Paint.FontMetricsInt fm) {

        if (fm != null) {
            paint.getFontMetricsInt(fm);
        }

        return measureTextWidth(paint, text, start, end) + (int) Math.ceil(HORIZONTAL_PADDING_PX * 2F);
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

        final int totalWidth = measureTextWidth(paint, text, start, end)
                + (int) Math.ceil(HORIZONTAL_PADDING_PX * 2F);

        backgroundPaint.setColor(theme.getCodeBackgroundColor(paint));
        backgroundPaint.setStyle(Paint.Style.FILL);

        final int radius = theme.getCodeBackgroundRadius();
        if (radius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // Draw the rounded shape directly (not clipPath + drawRect):
            // anti-aliasing applies to drawing only, a clip edge is always
            // hard/jagged. drawPath honours ANTI_ALIAS_FLAG.
            rectF.set(x, top, x + totalWidth, bottom);
            RoundedRectPath.build(path, rectF, radius, true, true, true, true);
            backgroundPaint.setAntiAlias(true);
            canvas.drawPath(path, backgroundPaint);
        } else {
            canvas.drawRect(x, top, x + totalWidth, bottom, backgroundPaint);
        }

        // Now draw the actual glyphs (text drawing itself honours nested spans).
        canvas.drawText(text, start, end, x + HORIZONTAL_PADDING_PX, y, paint);
    }

    /**
     * Measures the width of the [start, end) range. Preferred path is
     * {@link StaticLayout#getDesiredWidth(CharSequence, TextPaint)} which walks
     * the CharSequence and handles nested formatting; a re-entrant call falls
     * back to the span-unaware char[] measurement.
     */
    private int measureTextWidth(@NonNull Paint paint, @NonNull CharSequence text, int start, int end) {
        measurePaint.set(paint);
        if (measuring) {
            return (int) Math.ceil(rawMeasure(text, start, end));
        }
        measuring = true;
        try {
            return (int) Math.ceil(StaticLayout.getDesiredWidth(text.subSequence(start, end), measurePaint));
        } finally {
            measuring = false;
        }
    }

    private float rawMeasure(@NonNull CharSequence text, int start, int end) {
        final char[] buffer = new char[end - start];
        TextUtils.getChars(text, start, end, buffer, 0);
        return measurePaint.measureText(buffer, 0, buffer.length);
    }
}
