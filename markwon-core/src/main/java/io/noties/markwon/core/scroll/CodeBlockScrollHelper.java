package io.noties.markwon.core.scroll;

import android.text.Layout;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.R;
import io.noties.markwon.core.spans.CodeBlockLineSpan;
import io.noties.markwon.core.spans.CodeBlockSpan;

/**
 * Wires the two things a scrollable code block cannot do on its own:
 *
 * <ol>
 *     <li><b>viewport injection</b> — a span has no access to the {@code Layout} while it is
 *     being measured, so the available width is pushed into every
 *     {@link CodeBlockLineSpan} before {@code setText} (and again whenever the TextView
 *     is re-laid out, because at first {@code setText} the view is usually still 0px wide);</li>
 *     <li><b>touch handling</b> — horizontal drags inside a block are translated into
 *     {@link CodeBlockScrollState#scrollBy(float)}.</li>
 * </ol>
 *
 * <p>One instance is attached per TextView (stored in the view tag) and reused for
 * every subsequent {@code setText}.
 *
 * @since 4.6.3
 */
final class CodeBlockScrollHelper implements View.OnTouchListener, View.OnLayoutChangeListener {

    /**
     * Injects the width of the text area into every code block span of {@code markdown}.
     */
    static void injectViewport(@NonNull TextView textView, @NonNull Spanned markdown) {
        final float width = textAreaWidth(textView);
        if (width <= 0F) {
            return;
        }
        final CodeBlockLineSpan[] spans =
                markdown.getSpans(0, markdown.length(), CodeBlockLineSpan.class);
        if (spans == null) {
            return;
        }
        for (CodeBlockLineSpan span : spans) {
            span.setViewport(width);
        }
    }

    /**
     * Attaches the touch/layout handling. Safe to call for every {@code setText}, the
     * listeners are installed only once per TextView.
     */
    static void attach(@NonNull TextView textView) {
        if (textView.getTag(R.id.markwon_code_block_scroll_helper) != null) {
            return;
        }
        final CodeBlockScrollHelper helper = new CodeBlockScrollHelper(textView);
        textView.setTag(R.id.markwon_code_block_scroll_helper, helper);
        // NB: this replaces whatever OnTouchListener was installed before. Everything the
        // helper does not handle is forwarded (it returns false), so MovementMethod based
        // link handling and text selection keep working — except inside a block that can
        // actually be scrolled, where the gesture is consumed.
        textView.setOnTouchListener(helper);
        textView.addOnLayoutChangeListener(helper);
    }

    /**
     * Width available for the text, in Layout coordinates ({@code 0} is the left edge of
     * the text area).
     */
    private static float textAreaWidth(@NonNull TextView textView) {
        return textView.getWidth()
                - textView.getCompoundPaddingLeft()
                - textView.getCompoundPaddingRight();
    }

    private final TextView textView;
    private final int touchSlop;

    @Nullable
    private CodeBlockScrollState target;

    private float lastX;
    private float lastY;
    private boolean dragging;

    private CodeBlockScrollHelper(@NonNull TextView textView) {
        this.textView = textView;
        this.touchSlop = ViewConfiguration.get(textView.getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                target = findState(event);
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                // consume right away, otherwise a plain TextView (not selectable, not
                // clickable) would not report any of the MOVE events to us
                return target != null && target.canScroll();

            case MotionEvent.ACTION_MOVE: {
                if (target == null) {
                    return false;
                }
                final float dx = event.getX() - lastX;
                final float dy = event.getY() - lastY;
                if (!dragging) {
                    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > touchSlop) {
                        dragging = true;
                    } else if (Math.abs(dy) > touchSlop) {
                        // vertical gesture: hand it back to the parent (ScrollView)
                        target = null;
                        return false;
                    } else {
                        return true;
                    }
                }
                final ViewParent parent = v.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                if (target.scrollBy(-dx)) {
                    lastX = event.getX();
                    lastY = event.getY();
                    textView.invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                final boolean handled = dragging;
                dragging = false;
                target = null;
                return handled;
            }

            default:
                return false;
        }
    }

    @Override
    public void onLayoutChange(
            View v,
            int left,
            int top,
            int right,
            int bottom,
            int oldLeft,
            int oldTop,
            int oldRight,
            int oldBottom) {

        if ((right - left) == (oldRight - oldLeft)) {
            return;
        }

        final float width = textAreaWidth(textView);
        if (width <= 0F) {
            return;
        }

        final CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) {
            return;
        }

        final Spanned spanned = (Spanned) text;
        final CodeBlockLineSpan[] spans =
                spanned.getSpans(0, spanned.length(), CodeBlockLineSpan.class);
        if (spans == null) {
            return;
        }
        for (CodeBlockLineSpan span : spans) {
            span.setViewport(width);
        }

        // the spans clamp their width to the viewport, so the Layout has to be rebuilt
        textView.requestLayout();
        textView.invalidate();
    }

    /**
     * Hit-tests the <em>whole</em> code block rectangle, not the glyphs: the gesture belongs
     * to the block (background + scrollbar + code), exactly like the background and the
     * scrollbar are painted by {@link CodeBlockSpan} itself. A touch on the header, on the
     * padding, on the scrollbar or on the empty space to the right of a short line must all
     * scroll the block, which rules out resolving an offset and looking for a line span.
     */
    @Nullable
    private CodeBlockScrollState findState(@NonNull MotionEvent event) {
        final Layout layout = textView.getLayout();
        final CharSequence text = textView.getText();
        if (layout == null || layout.getLineCount() == 0 || !(text instanceof Spanned)) {
            return null;
        }

        final float y = event.getY() + textView.getScrollY() - textView.getCompoundPaddingTop();

        final Spanned spanned = (Spanned) text;
        final CodeBlockSpan[] blocks =
                spanned.getSpans(0, spanned.length(), CodeBlockSpan.class);
        if (blocks == null) {
            return null;
        }

        for (CodeBlockSpan block : blocks) {
            final CodeBlockScrollState state = block.getScrollState();
            if (state == null) {
                continue;
            }
            final int start = spanned.getSpanStart(block);
            final int end = Math.min(spanned.getSpanEnd(block), text.length());
            if (start >= end) {
                continue;
            }
            final int firstLine = lineForOffset(layout, start);
            final int lastLine = lineForOffset(layout, end - 1);
            if (y >= layout.getLineTop(firstLine) && y <= layout.getLineBottom(lastLine)) {
                return state;
            }
        }

        return null;
    }

    private static int lineForOffset(@NonNull Layout layout, int offset) {
        return Math.min(
                Math.max(layout.getLineForOffset(offset), 0),
                layout.getLineCount() - 1);
    }
}
