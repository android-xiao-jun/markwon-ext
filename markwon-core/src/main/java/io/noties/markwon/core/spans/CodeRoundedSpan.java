package io.noties.markwon.core.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
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

    private final MarkwonTheme theme;
    private final RectF rectF = new RectF();
    private final Path path = new Path();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

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

    /**
     * The framework never calls {@link #updateMeasureState(TextPaint)} /
     * {@link #updateDrawState(TextPaint)} on the span that <em>is</em> the replacement
     * ({@code TextLine#handleRun} only applies the other {@code MetricAffectingSpan}s of the
     * range), so the inline-code typeface/size has to be applied by us — otherwise the code is
     * measured and drawn with the surrounding text style and stops matching
     * {@link CodeSpan}, which is where the theme's normal code styling lives.
     *
     * @return the paint the range must be measured/drawn with
     */
    @NonNull
    private TextPaint resolvePaint(@NonNull Paint paint) {
        textPaint.set(paint);
        apply(textPaint);
        return textPaint;
    }

    /**
     * Horizontal inset between the inline-code text and the edge of its background.
     * Comes from the theme ({@link MarkwonTheme#getCodeHorizontalPadding()}) so the host
     * app can match its own design spec instead of a hardcoded value.
     */
    private float horizontalPadding() {
        return theme.getCodeHorizontalPadding();
    }

    @Override
    public int getSize(
            @NonNull Paint paint,
            CharSequence text,
            int start,
            int end,
            Paint.FontMetricsInt fm) {

        final TextPaint codePaint = resolvePaint(paint);

        if (fm != null) {
            codePaint.getFontMetricsInt(fm);
        }

        return (int) Math.ceil(measureTextWidth(codePaint, text, start, end)
                + horizontalPadding() * 2F);
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

        final TextPaint codePaint = resolvePaint(paint);

        final float totalWidth = measureTextWidth(codePaint, text, start, end)
                + horizontalPadding() * 2F;

        backgroundPaint.setColor(theme.getCodeBackgroundColor(codePaint));
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

        // Now draw the actual glyphs.
        // NB: `Canvas#drawText(CharSequence, ...)` — what this used to do — silently drops
        // EVERY span: a SpannableStringBuilder goes through its char[] branch and even the
        // SpannedString branch hands a plain String to the native layer. Nested formatting
        // (emphasis, a link inside the inline code, syntax highlighting) must therefore be
        // walked explicitly. SpannedTextRenderer also measures through char[], which is what
        // keeps getSize out of an infinite recursion — StaticLayout#getDesiredWidth would
        // call getSize right back on the very range this span covers.
        SpannedTextRenderer.INSTANCE.render(
                canvas,
                text,
                start,
                end,
                x + horizontalPadding(),
                y,
                codePaint);
    }

    private float measureTextWidth(@NonNull Paint paint, @NonNull CharSequence text, int start, int end) {
        return SpannedTextRenderer.INSTANCE.render(null, text, start, end, 0F, 0F, paint);
    }
}
