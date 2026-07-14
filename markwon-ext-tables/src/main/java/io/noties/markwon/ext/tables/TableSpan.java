package io.noties.markwon.ext.tables;

import android.text.TextPaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the shared geometry and scrolling state for all rows of one table.
 */
public class TableSpan {

    private final List<TableRowSpan> rows = new ArrayList<>();

    private int scrollX;
    private int tableWidth;
    private int textViewWidth;
    private int rowContentHeight;
    private int layoutWidth = -1;
    private boolean layoutsDirty = true;
    int committedWidth = -1;
    private boolean scrollEnabled = true;

    void reset() {
        scrollX = 0;
    }

    void addRow(TableRowSpan row) {
        if (!rows.contains(row)) {
            rows.add(row);
            layoutsDirty = true;
        }
    }

    void invalidateLayouts() {
        layoutsDirty = true;
    }

    int resolveWidth(int detectedWidth) {
        if (committedWidth <= 0) {
            committedWidth = detectedWidth;
        }
        return committedWidth;
    }

    void ensureLayouts(int availableWidth, TextPaint textPaint, TableTheme theme) {
        if (!layoutsDirty && layoutWidth == availableWidth) {
            return;
        }

        layoutWidth = availableWidth;
        layoutsDirty = false;

        int columnCount = 0;
        for (TableRowSpan row : rows) {
            columnCount = Math.max(columnCount, row.cellCount());
        }

        if (columnCount == 0) {
            tableWidth = 0;
            rowContentHeight = 0;
            return;
        }

        scrollEnabled = theme.isTableScrollEnabled();
        final int padding = theme.tableCellPadding() * 2;

        final int[] sharedColumnWidths;
        int totalWidth;

        if (!scrollEnabled) {
            // Equal-width columns: each cell gets an equal share of the viewport.
            // tableMaxColumnWidth has no effect when scrolling is disabled.
            sharedColumnWidths = new int[columnCount];
            final int baseWidth = availableWidth / columnCount;
            int remainder = availableWidth - baseWidth * columnCount;
            for (int i = 0; i < columnCount; i++) {
                sharedColumnWidths[i] = baseWidth + (i < remainder ? 1 : 0);
            }
            totalWidth = availableWidth;
        } else {
            final int configuredMax = theme.tableMaxColumnWidth();
            final int minColumnWidth = Math.max(1, availableWidth / 4);
            final int effectiveMinColumnWidth = configuredMax > 0
                    ? Math.min(minColumnWidth, configuredMax)
                    : minColumnWidth;
            sharedColumnWidths = new int[columnCount];
            Arrays.fill(sharedColumnWidths, effectiveMinColumnWidth);

            for (TableRowSpan row : rows) {
                final int[] desiredWidths = row.desiredColumnWidths(textPaint, padding);
                for (int i = 0; i < desiredWidths.length; i++) {
                    int desired = Math.max(desiredWidths[i], effectiveMinColumnWidth);
                    if (configuredMax > 0) {
                        desired = Math.min(desired, configuredMax);
                    }
                    sharedColumnWidths[i] = Math.max(sharedColumnWidths[i], desired);
                }
            }

            totalWidth = sum(sharedColumnWidths);
            if (totalWidth < availableWidth) {
                totalWidth = expandToAvailableWidth(
                        sharedColumnWidths,
                        availableWidth,
                        configuredMax);
            }
        }

        tableWidth = totalWidth;
        textViewWidth = availableWidth;
        setScrollX(scrollX);

        int maxContentHeight = 0;
        for (TableRowSpan row : rows) {
            maxContentHeight = Math.max(
                    maxContentHeight,
                    row.makeLayouts(textPaint, sharedColumnWidths, padding));
        }
        rowContentHeight = maxContentHeight;
    }

    private static int expandToAvailableWidth(
            int[] widths,
            int availableWidth,
            int maxColumnWidth) {

        int totalWidth = sum(widths);
        int remaining = availableWidth - totalWidth;

        while (remaining > 0) {
            int expandableColumns = 0;
            for (int width : widths) {
                if (maxColumnWidth <= 0 || width < maxColumnWidth) {
                    expandableColumns++;
                }
            }

            if (expandableColumns == 0) {
                break;
            }

            final int share = Math.max(1, remaining / expandableColumns);
            boolean expanded = false;
            for (int i = 0; i < widths.length && remaining > 0; i++) {
                if (maxColumnWidth > 0 && widths[i] >= maxColumnWidth) {
                    continue;
                }

                final int capacity = maxColumnWidth > 0
                        ? maxColumnWidth - widths[i]
                        : remaining;
                final int delta = Math.min(remaining, Math.min(share, capacity));
                if (delta > 0) {
                    widths[i] += delta;
                    remaining -= delta;
                    expanded = true;
                }
            }

            if (!expanded) {
                break;
            }
        }

        return sum(widths);
    }

    private static int sum(int[] values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }

    int rowContentHeight() {
        return rowContentHeight;
    }

    public int getScrollX() {
        return scrollX;
    }

    public void setScrollX(int scrollX) {
        if (!scrollEnabled) {
            this.scrollX = 0;
            return;
        }
        int maxScroll = Math.max(0, tableWidth - textViewWidth);
        this.scrollX = Math.min(Math.max(0, scrollX), maxScroll);
    }

    public boolean isScrollEnabled() {
        return scrollEnabled;
    }

    public int getTableWidth() {
        return tableWidth;
    }

    public void setTableWidth(int tableWidth) {
        this.tableWidth = tableWidth;
        setScrollX(scrollX);
    }

    public int getTextViewWidth() {
        return textViewWidth;
    }

    public void setTextViewWidth(int textViewWidth) {
        this.textViewWidth = textViewWidth;
        setScrollX(scrollX);
    }
}
