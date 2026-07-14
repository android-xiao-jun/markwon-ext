package io.noties.markwon.ext.tables;

import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class TableAwareMovementMethod implements MovementMethod {

    @NonNull
    public static TableAwareMovementMethod wrap(@NonNull MovementMethod movementMethod) {
        return new TableAwareMovementMethod(movementMethod);
    }

    @NonNull
    public static TableAwareMovementMethod create() {
        return new TableAwareMovementMethod(LinkMovementMethod.getInstance());
    }

    private final MovementMethod wrapped;
    private TableSpan activeTableSpan;
    private float downX, downY;
    private float prevX;
    private boolean isScrolling;
    private int touchSlop;

    public TableAwareMovementMethod(@NonNull MovementMethod wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void initialize(final TextView widget, Spannable text) {
        wrapped.initialize(widget, text);
        touchSlop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
        resetState();
    }

    private void resetState() {
        activeTableSpan = null;
        isScrolling = false;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        final int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                updateActiveTableSpan(widget, buffer, event);
                downX = event.getX();
                downY = event.getY();
                prevX = downX;
                isScrolling = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (activeTableSpan != null && activeTableSpan.isScrollEnabled()) {
                    final float dx = Math.abs(event.getX() - downX);
                    final float dy = Math.abs(event.getY() - downY);

                    if (!isScrolling && dx > touchSlop && dx > dy) {
                        isScrolling = true;
                        widget.getParent().requestDisallowInterceptTouchEvent(true);
                    }

                    if (isScrolling) {
                        final float distanceX = prevX - event.getX();
                        activeTableSpan.setScrollX(activeTableSpan.getScrollX() + (int) distanceX);
                        widget.invalidate();
                        prevX = event.getX();
                        return true;
                    }
                }
                prevX = event.getX();
                break;

            case MotionEvent.ACTION_UP:
                if (!isScrolling) {
                    final float dx = event.getX() - downX;
                    final float dy = event.getY() - downY;
                    if (dx * dx + dy * dy < touchSlop * touchSlop) {
                        if (handleClick(widget, buffer, event)) {
                            resetState();
                            widget.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                        }
                    }
                }
                resetState();
                widget.getParent().requestDisallowInterceptTouchEvent(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                resetState();
                widget.getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }

        return isScrolling || wrapped.onTouchEvent(widget, buffer, event);
    }

    private void updateActiveTableSpan(TextView widget, Spannable buffer, MotionEvent event) {
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

        final Layout layout = widget.getLayout();
        if (layout != null) {
            try {
                final int line = layout.getLineForVertical(y);
                final int off = layout.getOffsetForHorizontal(line, x);
                TableSpan[] spans = buffer.getSpans(off, off, TableSpan.class);
                activeTableSpan = spans.length > 0 ? spans[0] : null;
            } catch (IndexOutOfBoundsException e) {
                // 忽略越界异常
                activeTableSpan = null;
            }
        }
    }

    private boolean handleClick(TextView widget, Spannable buffer, MotionEvent event) {
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

        final Layout layout = widget.getLayout();
        if (layout == null) return false;

        try {
            final int line = layout.getLineForVertical(y);
            final int off = layout.getOffsetForHorizontal(line, x);

            // 检查是否点击在表格行上
            final TableRowSpan[] rowSpans = buffer.getSpans(off, off, TableRowSpan.class);
            if (rowSpans.length == 0) return false;

            final TableRowSpan span = rowSpans[0];
            final int scrollX = activeTableSpan != null ? activeTableSpan.getScrollX() : 0;

            // 考虑滚动偏移后的点击 X 坐标
            int absoluteX = x + scrollX;
            int colIndex = span.getColumnIndexAt(absoluteX);
            Layout cellLayout = span.getLayout(colIndex);

            if (cellLayout != null) {
                final int rowY = layout.getLineTop(line);
                final int rowLine = cellLayout.getLineForVertical(y - rowY);
                // 计算在单元格内部的相对 X 坐标
                int relativeX = absoluteX - span.getColumnStartX(colIndex);
                final int rowOffset = cellLayout.getOffsetForHorizontal(rowLine, relativeX);

                // 确保偏移量在有效范围内
                if (rowOffset >= 0 && rowOffset < cellLayout.getText().length()) {
                    final ClickableSpan[] rowClickableSpans = ((Spanned) cellLayout.getText())
                            .getSpans(rowOffset, rowOffset, ClickableSpan.class);
                    if (rowClickableSpans.length > 0) {
                        rowClickableSpans[0].onClick(widget);
                        return true;
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            // 忽略越界异常
        }
        return false;
    }

    @Override
    public boolean onKeyDown(TextView w, Spannable t, int k, KeyEvent e) {
        return wrapped.onKeyDown(w, t, k, e);
    }

    @Override
    public boolean onKeyUp(TextView w, Spannable t, int k, KeyEvent e) {
        return wrapped.onKeyUp(w, t, k, e);
    }

    @Override
    public boolean onKeyOther(TextView v, Spannable t, KeyEvent e) {
        return wrapped.onKeyOther(v, t, e);
    }

    @Override
    public void onTakeFocus(TextView w, Spannable t, int d) {
        wrapped.onTakeFocus(w, t, d);
    }

    @Override
    public boolean onTrackballEvent(TextView w, Spannable t, MotionEvent e) {
        return wrapped.onTrackballEvent(w, t, e);
    }

    @Override
    public boolean onGenericMotionEvent(TextView w, Spannable t, MotionEvent e) {
        return wrapped.onGenericMotionEvent(w, t, e);
    }

    @Override
    public boolean canSelectArbitrarily() {
        return wrapped.canSelectArbitrarily();
    }
}