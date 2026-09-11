package io.noties.markwon.core.scroll;

import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import io.noties.markwon.R;

/**
 * Routes touch events to the several gesture handlers a single {@code TextView} may have.
 *
 * <p>A {@code TextView} has room for exactly <b>one</b>
 * {@link View.OnTouchListener} — installing a second one silently replaces the first, and the
 * feature behind it stops working without a single exception being thrown. Markdown rendering
 * has more than one such feature ({@code CodeBlockScrollPlugin} drags code blocks, the table
 * plugin drags tables), and both may well be registered on the same {@code TextView}. This
 * router is the single listener that is actually installed; each feature registers itself with
 * it under a key of its own.
 *
 * <h3>One gesture, one owner</h3>
 * Handlers are asked in registration order and the <b>first one to consume
 * {@code ACTION_DOWN} owns the whole gesture</b>: every following event goes to that handler
 * and to nobody else. Without that latch a handler that did not see the {@code DOWN} — and
 * therefore knows nothing about the gesture — would be fed orphan {@code MOVE} events and
 * could act on them.
 *
 * <p>Handlers that do not want the gesture return {@code false} at {@code ACTION_DOWN}; the
 * next one gets its chance, and if nobody consumes it, the {@code TextView}'s own touch
 * handling runs as usual (link taps, selection, a parent {@code ScrollView} scrolling).
 *
 * @since 4.6.3
 */
public final class GestureRouter implements View.OnTouchListener {

    /**
     * The router of {@code textView}, installing it if it is not there yet. Safe to call for
     * every {@code setText} — one router per {@code TextView}, reused for its whole life.
     */
    @NonNull
    public static GestureRouter attach(@NonNull TextView textView) {
        final Object tag = textView.getTag(R.id.markwon_gesture_router);
        if (tag instanceof GestureRouter) {
            return (GestureRouter) tag;
        }
        final GestureRouter router = new GestureRouter();
        textView.setTag(R.id.markwon_gesture_router, router);
        textView.setOnTouchListener(router);
        return router;
    }

    /**
     * Registration order = priority order. Re-registering an existing key replaces the
     * handler <em>without</em> moving it to the end, so a plugin that refreshes its listener
     * on every {@code setText} cannot shuffle the order behind the others' backs.
     */
    private final Map<Object, View.OnTouchListener> listeners = new LinkedHashMap<>();

    /**
     * Handler that consumed the {@code ACTION_DOWN} currently in flight — the only one that
     * may see the rest of the gesture.
     */
    @Nullable
    private View.OnTouchListener owner;

    private GestureRouter() {
    }

    /**
     * @param key      identity of the handler, so that re-registering replaces instead of
     *                 stacking up (a class token is the usual choice)
     * @param listener the handler
     */
    public void add(@NonNull Object key, @NonNull View.OnTouchListener listener) {
        listeners.put(key, listener);
    }

    public void remove(@NonNull Object key) {
        listeners.remove(key);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            // latch: whatever consumes the DOWN owns everything that follows
            owner = null;
            for (View.OnTouchListener listener : listeners.values()) {
                if (listener.onTouch(v, event)) {
                    owner = listener;
                    return true;
                }
            }
            return false;
        }

        final View.OnTouchListener owner = this.owner;
        if (owner == null) {
            // nobody claimed this gesture — an orphan MOVE must not reach anyone
            return false;
        }

        final boolean handled = owner.onTouch(v, event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            this.owner = null;
        }
        return handled;
    }
}
