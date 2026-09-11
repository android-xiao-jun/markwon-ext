package io.noties.markwon.ext.tables;

import android.content.Context;
import android.graphics.Paint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Px;

import io.noties.markwon.utils.ColorUtils;
import io.noties.markwon.utils.Dip;

@SuppressWarnings("WeakerAccess")
public class TableTheme {

    @NonNull
    public static TableTheme create(@NonNull Context context) {
        return buildWithDefaults(context).build();
    }

    @NonNull
    public static Builder buildWithDefaults(@NonNull Context context) {
        final Dip dip = Dip.create(context);
        return emptyBuilder()
                .tableCellPadding(dip.toPx(4))
                .tableBorderWidth(dip.toPx(1))
                .tableMaxColumnWidth(dip.toPx(200));
    }

    @NonNull
    public static Builder emptyBuilder() {
        return new Builder();
    }


    protected static final int TABLE_BORDER_DEF_ALPHA = 75;

    protected static final int TABLE_ODD_ROW_DEF_ALPHA = 22;

    // by default 0
    protected final int tableCellPadding;

    // by default paint.color * TABLE_BORDER_DEF_ALPHA
    protected final int tableBorderColor;

    protected final int tableBorderWidth;

    // by default paint.color * TABLE_ODD_ROW_DEF_ALPHA
    protected final int tableOddRowBackgroundColor;

    // by default TABLE_ODD_ROW_DEF_ALPHA
    protected int tableOddRowBackgroundColorAlpha;

    // @since 1.1.1
    // by default no background
    protected final int tableEvenRowBackgroundColor;

    // @since 1.1.1
    // by default no background
    protected final int tableHeaderRowBackgroundColor;

    // Maximum width of a single column. A non-positive value means no limit.
    protected final int tableMaxColumnWidth;

    // @since 6.4.3 — whether the table can scroll horizontally.
    // When false the table is constrained to the viewport width and touch-scrolling is disabled.
    protected final boolean tableScrollEnabled;

    // @since 4.6.3
    // Radius (in pixels) applied to the four outer corners of a table.
    // The radius is only applied to the first (top-left/top-right) and the
    // last (bottom-left/bottom-right) row, so inner rows keep square corners.
    // A non-positive value means no rounding (default, backward compatible).
    protected final int tableCornerRadius;

    // @since 4.6.3
    // Height reserved at the bottom of the last row for the horizontal scrollbar.
    // `0` (default) = not configured = no scrollbar at all: nothing is painted and no room
    // is reserved. Same opt-in rule as the code block's scrollbar.
    protected final int tableScrollbarHeight;

    // @since 4.6.3
    // Color of the scrollbar track. `0` (default) = not configured = the track is not painted.
    protected final int tableScrollbarTrackColor;

    // @since 4.6.3
    // Color of the scrollbar thumb. `0` (default) = not configured = the thumb is not painted.
    protected final int tableScrollbarThumbColor;

    protected TableTheme(@NonNull Builder builder) {
        this.tableCellPadding = builder.tableCellPadding;
        this.tableBorderColor = builder.tableBorderColor;
        this.tableBorderWidth = builder.tableBorderWidth;
        this.tableOddRowBackgroundColor = builder.tableOddRowBackgroundColor;
        this.tableEvenRowBackgroundColor = builder.tableEvenRowBackgroundColor;
        this.tableHeaderRowBackgroundColor = builder.tableHeaderRowBackgroundColor;
        this.tableMaxColumnWidth = builder.tableMaxColumnWidth;
        this.tableScrollEnabled = builder.tableScrollEnabled;
        this.tableCornerRadius = builder.tableCornerRadius;
        // NB: no `!= 0 ? … : <hardcoded color>` fallback — `0` means "this property does not
        // exist" and the corresponding part of the scrollbar is simply not painted.
        this.tableScrollbarHeight = builder.tableScrollbarHeight;
        this.tableScrollbarTrackColor = builder.tableScrollbarTrackColor;
        this.tableScrollbarThumbColor = builder.tableScrollbarThumbColor;

        if(builder.tableOddRowBackgroundColorAlpha == -1) {
            this.tableOddRowBackgroundColorAlpha = TABLE_ODD_ROW_DEF_ALPHA;
        } else {
            this.tableOddRowBackgroundColorAlpha = builder.tableOddRowBackgroundColorAlpha;
        }
    }

    /**
     * @since 3.0.0
     */
    @NonNull
    public Builder asBuilder() {
        return new Builder()
                .tableCellPadding(tableCellPadding)
                .tableBorderColor(tableBorderColor)
                .tableBorderWidth(tableBorderWidth)
                .tableOddRowBackgroundColor(tableOddRowBackgroundColor)
                .tableEvenRowBackgroundColor(tableEvenRowBackgroundColor)
                .tableHeaderRowBackgroundColor(tableHeaderRowBackgroundColor)
                .tableMaxColumnWidth(tableMaxColumnWidth)
                .tableScrollEnabled(tableScrollEnabled)
                .tableOddRowBackgroundColorAlpha(tableOddRowBackgroundColorAlpha)
                .tableCornerRadius(tableCornerRadius)
                .tableScrollbarHeight(tableScrollbarHeight)
                .tableScrollbarTrackColor(tableScrollbarTrackColor)
                .tableScrollbarThumbColor(tableScrollbarThumbColor);
    }

    public int tableCellPadding() {
        return tableCellPadding;
    }

    /**
     * Returns the maximum width of one table column in pixels.
     * A non-positive value disables the limit.
     */
    public int tableMaxColumnWidth() {
        return tableMaxColumnWidth;
    }

    /**
     * Returns whether horizontal scrolling is enabled for this table.
     * When false the table width is constrained to the viewport.
     */
    public boolean isTableScrollEnabled() {
        return tableScrollEnabled;
    }

    /**
     * Returns the radius (in pixels) used for the four outer corners of a table.
     * A non-positive value means the table is drawn with square corners.
     *
     * @since 4.6.3
     */
    @Px
    public int tableCornerRadius() {
        return tableCornerRadius;
    }

    /**
     * Thickness the horizontal scrollbar is derived from, in pixels — the bar itself is drawn
     * {@code 0.3 ×} this tall, the same recipe as the code block's.
     * {@code 0} (default) = <b>not configured</b> = no scrollbar at all.
     *
     * <p><b>Nothing is reserved for it.</b> Unlike the code block — whose footer row carries the
     * bar — a table row cannot give up any height, or the last row would be taller than all the
     * others. The bar is drawn as an overlay resting on the inside of the card's bottom border.
     *
     * @since 4.6.3
     * @see #isTableScrollbarEnabled()
     */
    @Px
    public int tableScrollbarHeight() {
        return tableScrollbarHeight;
    }

    /**
     * Whether the horizontal scrollbar must be painted at all.
     *
     * <p>The scrollbar is an <em>opt-in</em> decoration, on exactly the same terms as the code
     * block's: it exists only when it was given a height <b>and</b> at least one of its two
     * colors. A table that can be scrolled without a scrollbar is a legitimate configuration —
     * dragging it still works.
     *
     * @since 4.6.3
     */
    public boolean isTableScrollbarEnabled() {
        return tableScrollbarHeight > 0
                && (tableScrollbarTrackColor != 0 || tableScrollbarThumbColor != 0);
    }

    /**
     * Color of the scrollbar track, or {@code 0} when it is not configured — in that case the
     * track is not painted (a thumb-only scrollbar is fine).
     *
     * @since 4.6.3
     */
    @ColorInt
    public int tableScrollbarTrackColor() {
        return tableScrollbarTrackColor;
    }

    /**
     * Color of the scrollbar thumb, or {@code 0} when it is not configured — in that case the
     * thumb is not painted (a track-only scrollbar is fine).
     *
     * @since 4.6.3
     */
    @ColorInt
    public int tableScrollbarThumbColor() {
        return tableScrollbarThumbColor;
    }

    public int tableBorderWidth(@NonNull Paint paint) {
        final int out;
        if (tableBorderWidth == -1) {
            out = (int) (paint.getStrokeWidth() + .5F);
        } else {
            out = tableBorderWidth;
        }
        return out;
    }

    public void applyTableBorderStyle(@NonNull Paint paint) {

        final int color;
        if (tableBorderColor == 0) {
            color = ColorUtils.applyAlpha(paint.getColor(), TABLE_BORDER_DEF_ALPHA);
        } else {
            color = tableBorderColor;
        }

        paint.setColor(color);
        // @since 4.3.1 before it was STROKE... change to FILL as we draw border differently
        paint.setStyle(Paint.Style.FILL);
    }

    public void applyTableOddRowStyle(@NonNull Paint paint) {
        final int color;
        if (tableOddRowBackgroundColor == 0) {
            color = ColorUtils.applyAlpha(paint.getColor(), tableOddRowBackgroundColorAlpha);
        } else {
            color = tableOddRowBackgroundColor;
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * @since 1.1.1
     */
    public void applyTableEvenRowStyle(@NonNull Paint paint) {
        // by default to background to even row
        paint.setColor(tableEvenRowBackgroundColor);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * @since 1.1.1
     */
    public void applyTableHeaderRowStyle(@NonNull Paint paint) {
        paint.setColor(tableHeaderRowBackgroundColor);
        paint.setStyle(Paint.Style.FILL);
    }

    public static class Builder {

        private int tableCellPadding;
        private int tableBorderColor;
        private int tableBorderWidth = -1;
        private int tableOddRowBackgroundColor;
        private int tableOddRowBackgroundColorAlpha = -1;
        private int tableEvenRowBackgroundColor; // @since 1.1.1
        private int tableHeaderRowBackgroundColor; // @since 1.1.1
        private int tableMaxColumnWidth;
        private boolean tableScrollEnabled = false;
        private int tableCornerRadius; // @since 4.6.3
        private int tableScrollbarHeight; // @since 4.6.3
        private int tableScrollbarTrackColor; // @since 4.6.3
        private int tableScrollbarThumbColor; // @since 4.6.3

        @NonNull
        public Builder tableCellPadding(@Px int tableCellPadding) {
            this.tableCellPadding = tableCellPadding;
            return this;
        }

        @NonNull
        public Builder tableBorderColor(@ColorInt int tableBorderColor) {
            this.tableBorderColor = tableBorderColor;
            return this;
        }

        @NonNull
        public Builder tableBorderWidth(@Px int tableBorderWidth) {
            this.tableBorderWidth = tableBorderWidth;
            return this;
        }

        @NonNull
        public Builder tableOddRowBackgroundColor(@ColorInt int tableOddRowBackgroundColor) {
            this.tableOddRowBackgroundColor = tableOddRowBackgroundColor;
            return this;
        }

        @NonNull
        public Builder tableOddRowBackgroundColorAlpha(int tableOddRowBackgroundColorAlpha) {
            this.tableOddRowBackgroundColorAlpha = tableOddRowBackgroundColorAlpha;
            return this;
        }

        @NonNull
        public Builder tableEvenRowBackgroundColor(@ColorInt int tableEvenRowBackgroundColor) {
            this.tableEvenRowBackgroundColor = tableEvenRowBackgroundColor;
            return this;
        }

        @NonNull
        public Builder tableHeaderRowBackgroundColor(@ColorInt int tableHeaderRowBackgroundColor) {
            this.tableHeaderRowBackgroundColor = tableHeaderRowBackgroundColor;
            return this;
        }

        /**
         * Sets the maximum width of one table column in pixels.
         * Pass a non-positive value to disable the limit.
         */
        @NonNull
        public Builder tableMaxColumnWidth(@Px int tableMaxColumnWidth) {
            this.tableMaxColumnWidth = tableMaxColumnWidth;
            return this;
        }

        @NonNull
        public Builder tableScrollEnabled(boolean enabled) {
            this.tableScrollEnabled = enabled;
            return this;
        }

        /**
         * Sets the radius (in pixels) for the four outer corners of a table. Only the
         * first row (top corners) and the last row (bottom corners) are rounded, inner
         * rows and inner grid lines are not affected.
         * <p>
         * Pass a non-positive value to draw square corners (default).
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder tableCornerRadius(@Px int tableCornerRadius) {
            this.tableCornerRadius = tableCornerRadius;
            return this;
        }

        /**
         * Thickness the horizontal scrollbar is derived from, in pixels (the bar is painted
         * {@code 0.3 ×} this tall). Pass {@code 0} (default) to drop the scrollbar entirely —
         * the table still scrolls by dragging, it just no longer shows where it is.
         *
         * <p>It reserves <b>no</b> row height: the last row stays exactly as tall as the others
         * and the bar is drawn on top of the card's bottom border. Only meaningful together with
         * {@link #tableScrollEnabled(boolean)}: a table that cannot scroll never draws one.
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder tableScrollbarHeight(@Px int tableScrollbarHeight) {
            this.tableScrollbarHeight = tableScrollbarHeight;
            return this;
        }

        /**
         * Color of the scrollbar track. Default {@code 0} = <b>not configured</b> = the track
         * is not painted. Configure it (and/or the thumb color) to opt into a scrollbar.
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder tableScrollbarTrackColor(@ColorInt int color) {
            this.tableScrollbarTrackColor = color;
            return this;
        }

        /**
         * Color of the scrollbar thumb. Default {@code 0} = <b>not configured</b> = the thumb
         * is not painted. Configure it (and/or the track color) to opt into a scrollbar.
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder tableScrollbarThumbColor(@ColorInt int color) {
            this.tableScrollbarThumbColor = color;
            return this;
        }

        @NonNull
        public TableTheme build() {
            return new TableTheme(this);
        }
    }
}
