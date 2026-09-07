package io.noties.markwon.image;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class AsyncDrawableLoader {

    /**
     * @since 3.0.0
     */
    @NonNull
    public static AsyncDrawableLoader noOp() {
        return new AsyncDrawableLoaderNoOp();
    }

    /**
     * 渲染态开关：为 {@code true} 时，本次渲染过程中创建的 {@link AsyncDrawable} 只使用
     * {@link #placeholder(AsyncDrawable)}，不会调用 {@link #load(AsyncDrawable)}。
     *
     * <p>{@code appendMarkdown} 的<strong>尾部（tail）渲染</strong>会打开这个开关。tail 是
     * 每个 SSE chunk 都会整段丢弃重渲染的「未稳定区域」：在这里发起图片请求，轻则白费流量，
     * 重则每来一个 token 就重建一次 {@link AsyncDrawable} 并重新 attach/cancel —— 图片高度在
     * 「0（无结果）」与「真实高度（加载完）」之间反复跳变。
     *
     * <p>开关只在渲染线程同步读写（{@code renderInto} 内），不做跨线程保护。
     *
     * @since 4.6.3
     */
    private boolean deferLoading;

    /**
     * @see #deferLoading
     * @since 4.6.3
     */
    public boolean isDeferLoading() {
        return deferLoading;
    }

    /**
     * @see #deferLoading
     * @since 4.6.3
     */
    public void setDeferLoading(boolean deferLoading) {
        this.deferLoading = deferLoading;
    }

    /**
     * @since 4.0.0
     */
    public abstract void load(@NonNull AsyncDrawable drawable);

    /**
     * @since 4.0.0
     */
    public abstract void cancel(@NonNull AsyncDrawable drawable);

    @Nullable
    public abstract Drawable placeholder(@NonNull AsyncDrawable drawable);

}
