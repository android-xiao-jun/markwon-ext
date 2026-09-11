package io.noties.markwon.core.scroll;

import android.text.Layout;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.R;
import io.noties.markwon.core.CodeBlockCopyTheme;
import io.noties.markwon.core.spans.CodeBlockLineSpan;
import io.noties.markwon.core.spans.CodeBlockSpan;

/**
 * Wires the three things a scrollable code block cannot do on its own:
 *
 * <ol>
 *     <li><b>viewport injection</b> — a span has no access to the {@code Layout} while it is
 *     being measured, so the available width is pushed into every
 *     {@link CodeBlockLineSpan} before {@code setText} (and again whenever the TextView
 *     is re-laid out, because at first {@code setText} the view is usually still 0px wide);</li>
 *     <li><b>touch handling</b> — horizontal drags inside a block are translated into
 *     {@link CodeBlockScrollState#scrollBy(float)};</li>
 *     <li><b>the copy button</b> — a tap on it (see
 *     {@link CodeBlockSpan#hitCopy(float, float)}) hands the block's original source to the
 *     host through {@link CodeBlockCopyListener}, without the gesture ever turning into a
 *     scroll. What "copy" means — the clipboard write, the feedback, the tracking — is the
 *     host's business, not the library's.</li>
 * </ol>
 *
 * <p>One instance is attached per TextView (stored in the view tag) and reused for
 * every subsequent {@code setText}.
 *
 * @since 4.6.3
 */
final class CodeBlockScrollHelper implements View.OnTouchListener, View.OnLayoutChangeListener {

    /**
     * How long the copy button keeps showing its confirmation label after a tap (see
     * {@code CodeBlockCopyTheme.Builder#successText(String)}). Purely cosmetic, so a constant
     * is enough — no need to grow a theme knob for it.
     */
    private static final long COPY_FEEDBACK_DURATION_MS = 1200L;

    /**
     * "The offset could not be resolved" marker for {@link #applyViewport}; a real offset is
     * never negative.
     */
    private static final float UNSET_OFFSET = -1F;

    /**
     * Injects the width of the text area into every code block span of {@code markdown}.
     */
    static void injectViewport(@NonNull TextView textView, @NonNull Spanned markdown) {
        final float width = textAreaWidth(textView);
        if (width <= 0F) {
            return;
        }
        applyViewport(textView, markdown, width);
    }

    /**
     * Hands every block its room: the width of the text area, plus the position the block
     * actually starts at.
     *
     * <p>The offset is not a detail. A block nested in a list item is shifted right by that
     * item's leading margin as well as by its own, and only the {@code Layout} knows by how
     * much. Handing out the text area without it reserves more room than the block has: its
     * lines are painted past the right edge of the text area (clipped away by the view), while
     * the inflated viewport keeps {@code canScroll()} at {@code false} — so the cut-off part
     * cannot be scrolled into view either. Resolved here, where both the {@code Spanned} and
     * the {@code Layout} are at hand.
     */
    private static boolean applyViewport(
            @NonNull TextView textView,
            @NonNull Spanned markdown,
            float textAreaWidth) {

        final CodeBlockLineSpan[] spans =
                markdown.getSpans(0, markdown.length(), CodeBlockLineSpan.class);
        if (spans == null) {
            return false;
        }

        final Layout layout = textView.getLayout();
        boolean changed = false;
        for (CodeBlockLineSpan span : spans) {
            // NB: `|=` and not `||` — every span must be handed the new viewport, not just
            // the first one that happens to change.
            changed |= span.setViewport(textAreaWidth, leftOffset(markdown, layout, span));
        }
        return changed;
    }

    /**
     * Offset of the block relative to the left edge of the text area: the sum of the leading
     * margins of the line it starts on — exactly the shift the framework applies before handing
     * the line to be drawn. Negative when it cannot be resolved (no layout yet, or one that
     * still describes the previous text), which leaves the span to fall back to its own margin.
     */
    private static float leftOffset(
            @NonNull Spanned spanned,
            @Nullable Layout layout,
            @NonNull Object span) {

        if (layout == null || layout.getLineCount() <= 0) {
            return UNSET_OFFSET;
        }

        final int start = spanned.getSpanStart(span);
        if (start < 0 || start >= spanned.length()) {
            return UNSET_OFFSET;
        }

        final int line = Math.min(
                Math.max(layout.getLineForOffset(start), 0),
                layout.getLineCount() - 1);

        float offset = 0F;
        final LeadingMarginSpan[] margins = spanned.getSpans(
                layout.getLineStart(line),
                layout.getLineEnd(line),
                LeadingMarginSpan.class);
        if (margins != null) {
            for (LeadingMarginSpan margin : margins) {
                offset += margin.getLeadingMargin(true);
            }
        }

        return offset > 0F ? offset : UNSET_OFFSET;
    }

    /**
     * Pushes the plugin's copy-button style into every header row of {@code markdown}.
     *
     * <p>The {@link CodeBlockLineSpan} rows are created deep inside {@code CorePlugin}, which
     * knows nothing about {@code CodeBlockScrollPlugin} — so the plugin hands its style over
     * here, before the text is laid out, because the header row height depends on it (an
     * enabled button conjures the row up even for a language-less block).
     *
     * <p>Unlike {@link #injectViewport(TextView, Spanned)} this does not care whether the view
     * has been measured yet: the style must be in place for the very first measure pass, and
     * the viewport can only be resolved after a layout.
     */
    static void injectCopy(@NonNull Spanned markdown, @Nullable CodeBlockCopyTheme copyTheme) {
        final CodeBlockLineSpan[] spans =
                markdown.getSpans(0, markdown.length(), CodeBlockLineSpan.class);
        if (spans == null) {
            return;
        }
        for (CodeBlockLineSpan span : spans) {
            span.setCopyTheme(copyTheme);
        }
    }

    /**
     * Attaches the touch/layout handling. Safe to call for every {@code setText} — the
     * listeners are installed only once per TextView while the (optional) copy callback is
     * refreshed every time, because it belongs to the plugin instance, not to the view.
     */
    static void attach(@NonNull TextView textView, @Nullable CodeBlockCopyListener copyListener) {
        // NB: deferred, deliberately. `setText` throws the current Layout away and builds the
        // new one later in the same frame, so at this very moment `getLayout()` is either null
        // or still describes the previous text — neither can say where a block starts. The next
        // message runs after that layout pass, which is when the offset becomes resolvable.
        textView.post(new ResolveViewport(textView));

        final Object tag = textView.getTag(R.id.markwon_code_block_scroll_helper);
        if (tag instanceof CodeBlockScrollHelper) {
            ((CodeBlockScrollHelper) tag).copyListener = copyListener;
            return;
        }
        final CodeBlockScrollHelper helper = new CodeBlockScrollHelper(textView, copyListener);
        textView.setTag(R.id.markwon_code_block_scroll_helper, helper);
        // NB: this replaces whatever OnTouchListener was installed before. Everything the
        // helper does not handle is forwarded (it returns false), so MovementMethod based
        // link handling and text selection keep working — except inside a block that can
        // actually be scrolled, or on a copy button, where the gesture is consumed.
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
    private CodeBlockCopyListener copyListener;

    @Nullable
    private CodeBlockScrollState target;

    /**
     * Block whose copy button received the {@code ACTION_DOWN}. While it is set the gesture
     * belongs to the button: it can neither scroll nor scroll the parent.
     */
    @Nullable
    private CodeBlockSpan copyTarget;

    private float lastX;
    private float lastY;
    private boolean dragging;

    private CodeBlockScrollHelper(
            @NonNull TextView textView,
            @Nullable CodeBlockCopyListener copyListener) {
        this.textView = textView;
        this.copyListener = copyListener;
        this.touchSlop = ViewConfiguration.get(textView.getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                // the button sits inside the block rectangle, so it must be tested first —
                // otherwise the tap would be read as the beginning of a scroll
                copyTarget = findCopyTarget(event);
                if (copyTarget != null) {
                    target = null;
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = false;
                    return true;
                }
                target = findState(event);
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                // consume right away, otherwise a plain TextView (not selectable, not
                // clickable) would not report any of the MOVE events to us
                return target != null && target.canScroll();

            case MotionEvent.ACTION_MOVE: {
                if (copyTarget != null) {
                    // sliding off the button cancels the copy, exactly like a regular button;
                    // the gesture is swallowed either way so it cannot start a scroll
                    if (!copyTarget.hitCopy(eventX(event), eventY(event))) {
                        copyTarget = null;
                    }
                    return true;
                }
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
                if (copyTarget != null) {
                    final CodeBlockSpan block = copyTarget;
                    final boolean released = event.getActionMasked() == MotionEvent.ACTION_UP;
                    copyTarget = null;
                    target = null;
                    dragging = false;
                    if (released) {
                        copy(block);
                    }
                    return true;
                }
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

        // NB: the Layout is the fresh one at this point — the view has just been laid out —
        // which is exactly why the offset can be resolved here with confidence.
        applyViewport(textView, (Spanned) text, width);

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

        final float y = eventY(event);

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

    /**
     * Looks for the copy button under the touch. Unlike {@link #findState(MotionEvent)} this
     * is <em>not</em> a rectangle test over the block: it asks each span for its button
     * ({@link CodeBlockSpan#hitCopy(float, float)}), which is only set while the button is
     * actually drawn — a disabled (or not yet painted) button never matches.
     */
    @Nullable
    private CodeBlockSpan findCopyTarget(@NonNull MotionEvent event) {
        final CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) {
            return null;
        }

        final Spanned spanned = (Spanned) text;
        final CodeBlockSpan[] blocks =
                spanned.getSpans(0, spanned.length(), CodeBlockSpan.class);
        if (blocks == null) {
            return null;
        }

        final float x = eventX(event);
        final float y = eventY(event);
        for (CodeBlockSpan block : blocks) {
            if (block.hitCopy(x, y)) {
                return block;
            }
        }

        return null;
    }

    /**
     * Hands {@link CodeBlockSpan#getCode()} — the original source of the block — to the host,
     * and shows the button's confirmation when one is configured.
     *
     * <p><b>The clipboard write happens outside</b> (see {@link CodeBlockCopyListener}): the
     * library only knows which block was tapped and what its source is, the host decides what
     * a copy means. With no listener attached there is nobody to hand the source to, so the tap
     * is a no-op — and the confirmation is not shown either, so the button never claims a copy
     * that did not happen.
     *
     * <p>The confirmation is time-based, so two repaints are needed: one now (to show it) and
     * one once it has expired (to take it away). A plain {@code invalidate()} does not
     * re-measure, which is exactly what makes this cheap.
     */
    private void copy(@NonNull CodeBlockSpan block) {
        final CodeBlockCopyListener listener = copyListener;
        if (listener == null) {
            return;
        }

        final String code = block.getCode();
        if (code == null || code.length() == 0) {
            return;
        }

        block.showCopyFeedback(COPY_FEEDBACK_DURATION_MS);
        textView.invalidate();
        textView.postInvalidateDelayed(COPY_FEEDBACK_DURATION_MS + 16L);

        listener.onCodeBlockCopy(textView, code);
    }

    /**
     * {@code MotionEvent} x translated into Layout coordinates ({@code 0} is the left edge of
     * the text area) — the space {@link CodeBlockSpan#hitCopy(float, float)} works in.
     */
    private float eventX(@NonNull MotionEvent event) {
        return event.getX() + textView.getScrollX() - textView.getCompoundPaddingLeft();
    }

    /**
     * {@code MotionEvent} y translated into Layout coordinates; the same conversion
     * {@link #findState(MotionEvent)} has always used.
     */
    private float eventY(@NonNull MotionEvent event) {
        return event.getY() + textView.getScrollY() - textView.getCompoundPaddingTop();
    }

    private static int lineForOffset(@NonNull Layout layout, int offset) {
        return Math.min(
                Math.max(layout.getLineForOffset(offset), 0),
                layout.getLineCount() - 1);
    }

    /**
     * Resolves the viewport once, right after the view has laid out the text that was just set
     * — see {@link #attach} for why it cannot be done in place. Re-injects both the width and
     * the offset of every block, and asks for another layout pass <em>only</em> when the room
     * actually changed: the block constrains the width of its own lines, so a corrected
     * viewport has to reach the Layout to take effect, but an unchanged one must not cost a
     * layout pass on every {@code setText}.
     */
    private static final class ResolveViewport implements Runnable {

        private final TextView textView;

        ResolveViewport(@NonNull TextView textView) {
            this.textView = textView;
        }

        @Override
        public void run() {
            final CharSequence text = textView.getText();
            if (!(text instanceof Spanned)) {
                return;
            }

            final float width = textAreaWidth(textView);
            if (width <= 0F) {
                return;
            }

            if (applyViewport(textView, (Spanned) text, width)) {
                textView.requestLayout();
            }
        }
    }
}
