package io.noties.markwon.image;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.R;

public abstract class AsyncDrawableScheduler {

    public static void schedule(@NonNull final TextView textView) {

        // we need a simple check if current text has already scheduled drawables
        // we need this in order to allow multiple calls to schedule (different plugins
        // might use AsyncDrawable), but we do not want to repeat the task
        //
        // hm... we need the same thing for unschedule then... we can check if last hash is !null,
        // if it's not -> unschedule, else ignore

        // @since 4.0.0
        final Integer lastTextHashCode =
                (Integer) textView.getTag(R.id.markwon_drawables_scheduler_last_text_hashcode);
        final int textHashCode = textView.getText().hashCode();
        if (lastTextHashCode != null
                && lastTextHashCode == textHashCode) {
            return;
        }
        textView.setTag(R.id.markwon_drawables_scheduler_last_text_hashcode, textHashCode);


        final AsyncDrawableSpan[] spans = extractSpans(textView);
        if (spans != null
                && spans.length > 0) {

            if (textView.getTag(R.id.markwon_drawables_scheduler) == null) {
                final View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {

                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        unschedule(textView);
                        v.removeOnAttachStateChangeListener(this);
                        v.setTag(R.id.markwon_drawables_scheduler, null);
                    }
                };
                textView.addOnAttachStateChangeListener(listener);
                textView.setTag(R.id.markwon_drawables_scheduler, listener);
            }

            // @since 4.1.0
            final DrawableCallbackImpl.Invalidator invalidator = new TextViewInvalidator(textView);

            AsyncDrawable drawable;

            for (AsyncDrawableSpan span : spans) {
                drawable = span.getDrawable();
                drawable.setCallback2(new DrawableCallbackImpl(textView, invalidator, drawable.getBounds()));
            }
        }
    }

    // must be called when text manually changed in TextView
    public static void unschedule(@NonNull TextView view) {

        // @since 4.0.0
        if (view.getTag(R.id.markwon_drawables_scheduler_last_text_hashcode) == null) {
            return;
        }
        view.setTag(R.id.markwon_drawables_scheduler_last_text_hashcode, null);


        final AsyncDrawableSpan[] spans = extractSpans(view);
        if (spans != null
                && spans.length > 0) {
            for (AsyncDrawableSpan span : spans) {
                span.getDrawable().setCallback2(null);
            }
        }
    }

    @Nullable
    private static AsyncDrawableSpan[] extractSpans(@NonNull TextView textView) {

        final CharSequence cs = textView.getText();
        final int length = cs != null
                ? cs.length()
                : 0;

        if (length == 0
                || !(cs instanceof Spanned)) {
            return null;
        }

        // we also could've tried the `nextSpanTransition`, but strangely it leads to worse performance
        // than direct getSpans

        return ((Spanned) cs).getSpans(0, length, AsyncDrawableSpan.class);
    }

    private AsyncDrawableScheduler() {
    }

    private static class DrawableCallbackImpl implements Drawable.Callback {

        // @since 4.1.0
        // interface to be used when bounds change and view must be invalidated
        interface Invalidator {
            void invalidate();
        }

        private final TextView view;
        private final Invalidator invalidator; // @since 4.1.0

        private Rect previousBounds;

        DrawableCallbackImpl(
                @NonNull TextView view,
                @NonNull Invalidator invalidator,
                Rect initialBounds) {
            this.view = view;
            this.invalidator = invalidator;
            this.previousBounds = new Rect(initialBounds);
        }

        @Override
        public void invalidateDrawable(@NonNull final Drawable who) {

            if (Looper.myLooper() != Looper.getMainLooper()) {
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        invalidateDrawable(who);
                    }
                });
                return;
            }

            final Rect rect = who.getBounds();

            // okay... the thing is IF we do not change bounds size, normal invalidate would do
            // but if the size has changed, then we need to update the whole layout...

            if (!previousBounds.equals(rect)) {
                // @since 4.1.0
                // invalidation moved to upper level (so invalidation can be deferred,
                // and multiple calls combined)
                invalidator.invalidate();
                // @since 4.6.3 就地更新而不是 new Rect(rect)：流式渲染每个 chunk 都会
                // 为 tail 区域的每张图重建 callback 并走这里，避免每次分配一个 Rect
                previousBounds.set(rect);
            } else {

                view.postInvalidate();
            }
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            final long delay = when - SystemClock.uptimeMillis();
            view.postDelayed(what, delay);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            view.removeCallbacks(what);
        }
    }

    private static class TextViewInvalidator implements DrawableCallbackImpl.Invalidator, Runnable {

        private final TextView textView;

        TextViewInvalidator(@NonNull TextView textView) {
            this.textView = textView;
        }

        @Override
        public void invalidate() {
            textView.removeCallbacks(this);
            textView.post(this);
        }

        @Override
        public void run() {
            // 图片加载完成后 AsyncDrawable 的 bounds 变了，而 ReplacementSpan 的高度是在
            // layout 阶段由 AsyncDrawableSpan#getSize 决定的 —— 必须让 TextView 重新走一次
            // 完整 setText 才会重新测量行高，重新绘制整段文本。
            textView.setText(textView.getText());
            // 兜底：部分 TextView 实现对「与当前 mText 相同 / 相等的 CharSequence」会跳过
            // 重新 layout，这里显式请求一次布局，避免图片被上一版行高裁切
            textView.requestLayout();
            textView.invalidate();
        }
    }
}
