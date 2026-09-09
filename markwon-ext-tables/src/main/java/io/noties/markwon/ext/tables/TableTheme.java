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
                .tableCornerRadius(tableCornerRadius);
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

        @NonNull
        public TableTheme build() {
            return new TableTheme(this);
        }
    }
}
