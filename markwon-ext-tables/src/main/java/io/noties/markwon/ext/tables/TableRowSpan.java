package io.noties.markwon.ext.tables;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.core.spans.TextLayoutSpan;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableSpan;

public class TableRowSpan extends ReplacementSpan {

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    @IntDef(value = {ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Alignment {
    }

    public interface Invalidator {
        void invalidate();
    }

    public static class Cell {
        final int alignment;
        final CharSequence text;

        public Cell(@Alignment int alignment, CharSequence text) {
            this.alignment = alignment;
            this.text = text;
        }

        @Alignment
        public int alignment() {
            return alignment;
        }

        public CharSequence text() {
            return text;
        }
    }

    private final TableTheme theme;
    private final List<Cell> cells;
    private final List<Layout> layouts;
    private final TextPaint textPaint;
    private final boolean header;
    private final boolean odd;

    private final Rect rect = new Rect();
    private final RectF rectF = new RectF();
    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int width; // TextView width (display window)
    private int tableTotalWidth; // Actual content width
    private int[] columnWidths; // Each column's width
    private int height;
    private Invalidator invalidator;
    private final TableSpan tableSpan;

    // @since 4.6.3 — frozen position of this row inside the table, published
    // by TableSpan#ensureLayouts once all rows are registered. -1 until then.
    private int rowIndex = -1;
    private int rowCount;

    public TableRowSpan(
            @NonNull TableTheme theme,
            @NonNull TableSpan tableSpan,
            @NonNull List<Cell> cells,
            boolean header,
            boolean odd) {
        this.theme = theme;
        this.tableSpan = tableSpan;
        this.cells = cells;
        this.layouts = new ArrayList<>(cells.size());
        this.textPaint = new TextPaint();
        this.header = header;
        this.odd = odd;
        tableSpan.addRow(this);
    }

    @NonNull
    TableSpan getTableSpan() {
        return tableSpan;
    }

    @Override
    public int getSize(
            @NonNull Paint paint,
            CharSequence text,
            @IntRange(from = 0) int start,
            @IntRange(from = 0) int end,
            @Nullable Paint.FontMetricsInt fm) {

        // getSize receives the TextView's available width. All rows delegate their
        // geometry to the same TableSpan so corresponding columns and row heights match.
        width = tableSpan.resolveWidth(SpanUtils.width(null, text));
        if (paint instanceof TextPaint) {
            textPaint.set((TextPaint) paint);
        } else {
            textPaint.set(paint);
        }
        tableSpan.ensureLayouts(width, textPaint, theme);

        height = tableSpan.rowContentHeight();
        if (fm != null && height > 0) {
            final int verticalPadding = theme.tableCellPadding() * 2;
            fm.ascent = -(height + verticalPadding);
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }

        // Return minimal width so the \n between rows always fits within the
        // available width. If the reported width exceeds the layout width the \n
        // wraps to its own blank line, creating a visible gap between rows.
        return 1;
    }

    @Override
    public void draw(
            @NonNull Canvas canvas,
            CharSequence text,
            @IntRange(from = 0) int start,
            @IntRange(from = 0) int end,
            float x,
            int top,
            int y,
            int bottom,
            @NonNull Paint p) {
        final boolean scrollEnabled = tableSpan.isScrollEnabled();
        final int scrollX = scrollEnabled ? tableSpan.getScrollX() : 0;

        // Detect the real viewport width from the canvas, which may differ from
        // the width reported by SpanUtils during getSize(). This is essential for
        // correct scroll-range calculation.
        canvas.getClipBounds(rect);
        final int viewportWidth = rect.width();
        if (viewportWidth > 0) {
            final boolean widthChanged = (viewportWidth != tableSpan.getTextViewWidth());
            tableSpan.setTextViewWidth(viewportWidth);

            if (!scrollEnabled && widthChanged && tableSpan.getTableWidth() != viewportWidth) {
                tableSpan.committedWidth = viewportWidth;
                tableSpan.invalidateLayouts();
                if (invalidator != null) {
                    invalidator.invalidate();
                }
                return;
            }
        }

        final int padding = theme.tableCellPadding();
        final int size = layouts.size();
        final int rowHeight = bottom - top;

        // @since 4.6.3 — rounded outer corners, drawn directly (no clipping):
        // the first row rounds its top corners, the last row the bottom ones.
        // Clipping was abandoned on purpose — a clip edge cuts the border
        // lines at the corners and is never anti-aliased. Instead:
        //   background  → drawn as a rounded path (anti-aliased)
        //   top/bottom lines and edge columns → indented by the radius
        //   the corner arc itself → drawn as a stroked arc segment
        int radius = theme.tableCornerRadius();
        float corner = 0F;
        if (radius > 0 && rowHeight > 0 && tableTotalWidth > 0) {
            corner = Math.min(radius, Math.min(tableTotalWidth, rowHeight) / 2F);
        }
        final boolean first = corner > 0F && rowIndex == 0;
        final boolean last = corner > 0F && rowCount > 0 && rowIndex == rowCount - 1;
        // geometric last row (independent of rounding) — see the bottom line below
        final boolean lastRow = rowCount > 0 && rowIndex == rowCount - 1;

        canvas.save();
        // Clip to the actual visible area and apply scroll translation
        canvas.clipRect(x, top, x + viewportWidth, bottom);
        canvas.translate(x - scrollX, top);

        // 1. Background
        if (header) theme.applyTableHeaderRowStyle(paint);
        else if (odd) theme.applyTableOddRowStyle(paint);
        else theme.applyTableEvenRowStyle(paint);

        if (paint.getColor() != 0) {
            rect.set(0, 0, tableTotalWidth, rowHeight);
            if (first || last) {
                rectF.set(rect);
                roundedRect(path, rectF, corner, first, first, last, last);
                paint.setAntiAlias(true);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawRect(rect, paint);
            }
        }

        // 2. Borders
        paint.set(p);
        theme.applyTableBorderStyle(paint);
        final int borderWidth = theme.tableBorderWidth(paint);
        paint.setAntiAlias(true);

        // horizontal lines: indented by the radius on rounded sides so they
        // meet the corner arcs instead of sticking out of the rounded shape.
        // The bottom line is drawn by the LAST row only — the row below
        // provides its own top line, drawing both doubles the thickness of
        // every inner separator.
        final float topLineStart = first ? corner : 0F;
        final float topLineEnd = first ? tableTotalWidth - corner : tableTotalWidth;
        canvas.drawRect(topLineStart, 0F, topLineEnd, borderWidth, paint);

        if (lastRow) {
            final float bottomLineStart = last ? corner : 0F;
            final float bottomLineEnd = last ? tableTotalWidth - corner : tableTotalWidth;
            canvas.drawRect(bottomLineStart, rowHeight - borderWidth, bottomLineEnd, rowHeight, paint);
        }

        // corner arcs: stroked segments whose centerline radius is
        // (corner - borderWidth/2), so the stroke sits exactly on the inner
        // side of the rounded background and joins the straight lines
        if (first || last) {
            final float bw2 = borderWidth / 2F;
            final float r = corner - bw2;
            final Paint.Style previousStyle = paint.getStyle();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderWidth);
            if (first) {
                rectF.set(bw2, bw2, r * 2F + bw2, r * 2F + bw2);
                canvas.drawArc(rectF, 180F, 90F, false, paint);
                rectF.set(tableTotalWidth - r * 2F - bw2, bw2, tableTotalWidth - bw2, r * 2F + bw2);
                canvas.drawArc(rectF, 270F, 90F, false, paint);
            }
            if (last) {
                rectF.set(tableTotalWidth - r * 2F - bw2, rowHeight - r * 2F - bw2, tableTotalWidth - bw2, rowHeight - bw2);
                canvas.drawArc(rectF, 0F, 90F, false, paint);
                rectF.set(bw2, rowHeight - r * 2F - bw2, r * 2F + bw2, rowHeight - bw2);
                canvas.drawArc(rectF, 90F, 90F, false, paint);
            }
            paint.setStyle(previousStyle);
        }

        // 3. Cells and vertical borders
        int currentX = 0;
        int maxHeight = 0;

        // edge columns are shortened on rounded sides to meet the arcs
        final float edgeTop = first ? corner : 0F;
        final float edgeBottom = last ? rowHeight - corner : rowHeight;

        for (int i = 0; i < size; i++) {
            Layout layout = layouts.get(i);
            final int save = canvas.save();
            try {
                int colW = columnWidths[i];

                if (i == 0) {
                    canvas.drawRect(currentX, edgeTop, currentX + borderWidth, edgeBottom, paint);
                } else {
                    canvas.drawRect(currentX, 0, currentX + borderWidth, rowHeight, paint);
                }
                if (i == size - 1) {
                    canvas.drawRect(currentX + colW - borderWidth, edgeTop, currentX + colW, edgeBottom, paint);
                }

                final int contentAreaHeight = rowHeight - padding * 2;
                final int heightDiff = Math.max(0, (contentAreaHeight - layout.getHeight()) / 2);

                canvas.translate(currentX + padding, padding + heightDiff);
                layout.draw(canvas);

                currentX += colW;

                if (layout.getHeight() > maxHeight) {
                    maxHeight = layout.getHeight();
                }

            } finally {
                canvas.restoreToCount(save);
            }
        }

        // 4. Scrollbar (only when scrolling is enabled and content exceeds viewport)
        if (scrollEnabled) {
            final int drawableWidth = tableSpan.getTextViewWidth();
            if (tableTotalWidth > drawableWidth) {
                final Spanned spanned = (Spanned) text;
                TableSpan[] allTableSpans = spanned.getSpans(start, end, TableSpan.class);
                if (allTableSpans.length > 0 && spanned.getSpanEnd(allTableSpans[0]) == end) {
                    drawScrollbar(canvas, bottom - top, scrollX, drawableWidth);
                }
            }
        }

        canvas.restore();

        if (height != maxHeight) {
//            if (invalidator != null) {
//                invalidator.invalidate();
//            }
        }
    }

    /**
     * @since 4.6.3 — called by TableSpan#ensureLayouts with the stable position
     * of this row (index 0 = first row) and the total row count.
     */
    void rowPosition(int index, int count) {
        this.rowIndex = index;
        this.rowCount = count;
    }

    /**
     * Builds a rectangle with independently rounded corners (a compatible
     * replacement for {@code Path#addRoundRect(float[], ...)} which requires API 21).
     */
    private static void roundedRect(
            @NonNull Path path,
            @NonNull RectF bounds,
            float radius,
            boolean topLeft,
            boolean topRight,
            boolean bottomRight,
            boolean bottomLeft) {

        final float r = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2F);

        path.reset();
        path.moveTo(bounds.left + (topLeft ? r : 0F), bounds.top);

        if (topRight) {
            path.lineTo(bounds.right - r, bounds.top);
            path.arcTo(bounds.right - r * 2, bounds.top, bounds.right, bounds.top + r * 2, -90F, 90F, false);
        } else {
            path.lineTo(bounds.right, bounds.top);
        }

        if (bottomRight) {
            path.lineTo(bounds.right, bounds.bottom - r);
            path.arcTo(bounds.right - r * 2, bounds.bottom - r * 2, bounds.right, bounds.bottom, 0F, 90F, false);
        } else {
            path.lineTo(bounds.right, bounds.bottom);
        }

        if (bottomLeft) {
            path.lineTo(bounds.left + r, bounds.bottom);
            path.arcTo(bounds.left, bounds.bottom - r * 2, bounds.left + r * 2, bounds.bottom, 90F, 90F, false);
        } else {
            path.lineTo(bounds.left, bounds.bottom);
        }

        if (topLeft) {
            path.lineTo(bounds.left, bounds.top + r);
            path.arcTo(bounds.left, bounds.top, bounds.left + r * 2, bounds.top + r * 2, 180F, 90F, false);
        } else {
            path.lineTo(bounds.left, bounds.top);
        }

        path.close();
    }

    private void drawScrollbar(Canvas canvas, int rowHeight, int scrollX, int drawableWidth) {
        int scrollbarHeight = 6;
        int scrollbarMargin = 2;
        float ratio = (float) drawableWidth / tableTotalWidth;
        int scrollbarWidth = (int) (drawableWidth * ratio);
        int scrollbarPos = (int) (scrollX * ratio);

        paint.setColor(0x88888888);
        rect.set(scrollX + scrollbarPos, rowHeight - scrollbarHeight - scrollbarMargin,
                scrollX + scrollbarPos + scrollbarWidth, rowHeight - scrollbarMargin);
        canvas.drawRect(rect, paint);
    }

    int cellCount() {
        return cells.size();
    }

    int[] desiredColumnWidths(@NonNull TextPaint baseTextPaint, int horizontalPadding) {
        textPaint.set(baseTextPaint);
        textPaint.setFakeBoldText(header);

        final int[] desiredWidths = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            desiredWidths[i] = (int) Math.ceil(
                    StaticLayout.getDesiredWidth(cells.get(i).text, textPaint))
                    + horizontalPadding;
        }
        return desiredWidths;
    }

    int makeLayouts(
            @NonNull TextPaint baseTextPaint,
            @NonNull int[] sharedColumnWidths,
            int horizontalPadding) {

        textPaint.set(baseTextPaint);
        textPaint.setFakeBoldText(header);
        columnWidths = new int[sharedColumnWidths.length];
        System.arraycopy(sharedColumnWidths, 0, columnWidths, 0, sharedColumnWidths.length);

        tableTotalWidth = 0;
        for (int columnWidth : columnWidths) {
            tableTotalWidth += columnWidth;
        }

        int maxHeight = 0;
        layouts.clear();
        for (int i = 0; i < cells.size(); i++) {
            final Cell cell = cells.get(i);
            final Spannable spannable = cell.text instanceof Spannable
                    ? (Spannable) cell.text
                    : new SpannableString(cell.text);
            final int contentWidth = Math.max(1, columnWidths[i] - horizontalPadding);
            final StaticLayout layout = new StaticLayout(
                    spannable,
                    textPaint,
                    contentWidth,
                    alignment(cell.alignment),
                    1.0F,
                    0.0F,
                    false);
            TextLayoutSpan.applyTo(spannable, layout);
            scheduleAsyncDrawables(spannable, new Runnable() {
                @Override
                public void run() {
                    tableSpan.invalidateLayouts();
                    if (invalidator != null) invalidator.invalidate();
                }
            });
            layouts.add(layout);
            maxHeight = Math.max(maxHeight, layout.getHeight());
        }
        return maxHeight;
    }

    private void scheduleAsyncDrawables(@NonNull Spannable spannable, @NonNull final Runnable recreate) {
        final AsyncDrawableSpan[] spans = spannable.getSpans(0, spannable.length(), AsyncDrawableSpan.class);
        if (spans != null && spans.length > 0) {
            for (AsyncDrawableSpan span : spans) {
                final AsyncDrawable drawable = span.getDrawable();
                if (drawable.isAttached()) continue;
                drawable.setCallback2(new CallbackAdapter() {
                    @Override
                    public void invalidateDrawable(@NonNull Drawable who) {
                        recreate.run();
                    }
                });
            }
        }
    }

    public int getColumnIndexAt(int x) {
        if (columnWidths == null) return -1;
        int currentX = 0;
        for (int i = 0; i < columnWidths.length; i++) {
            if (x >= currentX && x < currentX + columnWidths[i]) {
                return i;
            }
            currentX += columnWidths[i];
        }
        return -1;
    }

    public int getColumnStartX(int index) {
        if (columnWidths == null || index < 0 || index >= columnWidths.length) return 0;
        int startX = 0;
        for (int i = 0; i < index; i++) {
            startX += columnWidths[i];
        }
        return startX;
    }

    public Layout getLayout(int index) {
        return (index >= 0 && index < layouts.size()) ? layouts.get(index) : null;
    }

    @SuppressLint("SwitchIntDef")
    private static Layout.Alignment alignment(@Alignment int alignment) {
        switch (alignment) {
            case ALIGN_CENTER: return Layout.Alignment.ALIGN_CENTER;
            case ALIGN_RIGHT: return Layout.Alignment.ALIGN_OPPOSITE;
            default: return Layout.Alignment.ALIGN_NORMAL;
        }
    }

    public void invalidator(@Nullable Invalidator invalidator) {
        this.invalidator = invalidator;
    }

    private static abstract class CallbackAdapter implements Drawable.Callback {
        @Override public void invalidateDrawable(@NonNull Drawable who) {}
        @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {}
        @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {}
    }

    public String copyText(){
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cells.size(); i++) {

            CharSequence text = cells.get(i).text();

            if (text instanceof Spanned) {
                sb.append(text.toString());
            } else {
                sb.append(text);
            }

            if (i != cells.size() - 1) {
                sb.append('\t');      // 或 " | "
            }
        }

        return sb.toString();    }
}
