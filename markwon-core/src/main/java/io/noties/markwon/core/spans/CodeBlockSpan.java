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

import io.noties.markwon.core.MarkwonTheme;

/**
 * @since 3.0.0 split inline and block spans
 */
public class CodeBlockSpan extends MetricAffectingSpan implements LeadingMarginSpan {

    private final MarkwonTheme theme;
    private final Rect rect = ObjectsPool.rect();
    private final RectF rectF = ObjectsPool.rectF();
    private final Path path = ObjectsPool.path();
    private final Paint paint = ObjectsPool.paint();

    public CodeBlockSpan(@NonNull MarkwonTheme theme) {
        this.theme = theme;
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

        final int radius = theme.getCodeBlockBackgroundRadius();
        // drawLeadingMargin is called once per line of the block, so rounding
        // every call would leave a wavy left/right edge. Round the top corners
        // on the first line and the bottom corners on the last line only; the
        // span range in `text` tells us which line is being drawn.
        final boolean rounded = radius > 0
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && text instanceof Spanned;
        final boolean roundTop;
        final boolean roundBottom;
        if (rounded) {
            final Spanned spanned = (Spanned) text;
            final int spanStart = spanned.getSpanStart(this);
            final int spanEnd = spanned.getSpanEnd(this);
            roundTop = start <= spanStart && spanStart < end;
            roundBottom = start < spanEnd && spanEnd <= end;
            if (!roundTop && !roundBottom) {
                rect.set(left, top, right, bottom);
                c.drawRect(rect, paint);
                return;
            }
        } else {
            roundTop = false;
            roundBottom = false;
        }

        if (rounded) {
            // Draw the rounded shape directly instead of clipPath + drawRect:
            // anti-aliasing applies to drawing only, a clip edge is always
            // hard/jagged. drawPath honours ANTI_ALIAS_FLAG.
            rectF.set(left, top, right, bottom);
            RoundedRectPath.build(path, rectF, radius, roundTop, roundTop, roundBottom, roundBottom);
            paint.setAntiAlias(true);
            c.drawPath(path, paint);
        } else {
            rect.set(left, top, right, bottom);
            c.drawRect(rect, paint);
        }
    }
}
