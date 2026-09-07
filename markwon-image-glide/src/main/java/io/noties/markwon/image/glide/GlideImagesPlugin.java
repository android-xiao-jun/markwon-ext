package io.noties.markwon.image.glide;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.util.LruCache;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import org.commonmark.node.Image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableLoader;
import io.noties.markwon.image.AsyncDrawableScheduler;
import io.noties.markwon.image.DrawableUtils;
import io.noties.markwon.image.ImageSpanFactory;

/**
 * @since 4.0.0
 */
public class GlideImagesPlugin extends AbstractMarkwonPlugin {

    public interface GlideStore {

        @NonNull
        RequestBuilder<Drawable> load(@NonNull AsyncDrawable drawable);

        void cancel(@NonNull Target<?> target);
    }

    /**
     * 占位图提供器。加载中与加载失败时 {@link AsyncDrawableLoader#placeholder(AsyncDrawable)}
     * / 加载失败回退都会用它创建占位 drawable。
     *
     * <p><strong>实现要求</strong>：返回的 drawable 必须提供非空 intrinsic 尺寸
     * （{@link Drawable#getIntrinsicWidth()} / {@link Drawable#getIntrinsicHeight()}），
     * 否则 {@code AsyncDrawable#setPlaceholderResult} 会退化成 {@code setBounds(0,0,1,1)}，
     * 行高在 0 与图片真实高度之间反复跳变。
     *
     * @see GlideImagesPlugin#placeholderProvider(PlaceholderProvider)
     * @since 4.6.3
     */
    public interface PlaceholderProvider {

        /**
         * @param drawable 占位所属的 drawable，可用于读取 destination / imageSize；
         *                 加载失败且所有等待者已被 detach 清空时为 {@code null}，
         *                 实现方需能处理
         * @return 自定义占位 drawable；返回 {@code null} 时回落到内置灰色占位
         */
        @Nullable
        Drawable newPlaceholder(@Nullable AsyncDrawable drawable);
    }

    @NonNull
    public static GlideImagesPlugin create(@NonNull final Context context) {
        // @since 4.5.0 cache RequestManager
        //  sometimes `cancel` would be called after activity is destroyed,
        //  so `Glide.with(context)` will throw an exception
        return create(Glide.with(context));
    }

    @NonNull
    public static GlideImagesPlugin create(@NonNull final RequestManager requestManager) {
        return create(new GlideStore() {
            @NonNull
            @Override
            public RequestBuilder<Drawable> load(@NonNull AsyncDrawable drawable) {
                return requestManager.load(drawable.getDestination());
            }

            @Override
            public void cancel(@NonNull Target<?> target) {
                requestManager.clear(target);
            }
        });
    }

    @NonNull
    public static GlideImagesPlugin create(@NonNull GlideStore glideStore) {
        return new GlideImagesPlugin(glideStore);
    }

    private final GlideAsyncDrawableLoader glideAsyncDrawableLoader;

    @SuppressWarnings("WeakerAccess")
    GlideImagesPlugin(@NonNull GlideStore glideStore) {
        this.glideAsyncDrawableLoader = new GlideAsyncDrawableLoader(glideStore);
    }

    /**
     * 配置自定义占位图。默认（不调用此方法）使用内置的 24dp 灰色矩形占位。
     *
     * <p>可链式调用：
     * <pre>{@code
     * GlideImagesPlugin.create(requestManager)
     *     .placeholderProvider(drawable -> MyApp.ui.placeholder())
     * }</pre>
     *
     * @param provider 占位图提供器；传 {@code null} 恢复为内置默认占位
     * @return this（链式）
     * @see PlaceholderProvider
     * @since 4.6.3
     */
    @NonNull
    public GlideImagesPlugin placeholderProvider(@Nullable PlaceholderProvider provider) {
        this.glideAsyncDrawableLoader.setPlaceholderProvider(provider);
        return this;
    }

    @Override
    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
        builder.setFactory(Image.class, new ImageSpanFactory());
    }

    @Override
    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
        builder.asyncDrawableLoader(glideAsyncDrawableLoader);
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
        AsyncDrawableScheduler.unschedule(textView);
    }

    @Override
    public void afterSetText(@NonNull TextView textView) {
        AsyncDrawableScheduler.schedule(textView);
    }

    private static class GlideAsyncDrawableLoader extends AsyncDrawableLoader {

        /**
         * 已成功加载的结果缓存（key = destination）。SSE / 增量渲染时每个 chunk 都会为
         * 未稳定区域重建 {@link AsyncDrawable}，如果没有这层缓存，同一张图会反复发起
         * Glide 请求并从 placeholder 重新显示，造成闪烁。命中缓存时同步应用结果。
         */
        private static final int RESULT_CACHE_SIZE = 8 * 8;

        /**
         * 占位图边长（dp）。加载中与加载失败时使用。
         */
        private static final int PLACEHOLDER_DP = 24;

        /**
         * 占位图共享 Paint。
         *
         * <p>{@link PlaceholderDrawable} 是无状态的纯色矩形：构造后再不修改 Paint，
         * {@code Canvas#drawRect} 也不会改动它，因此可以在所有占位实例之间共享。
         *
         * <p>共享前每次 {@code newPlaceholder()} 都会 {@code new Paint()} —— Paint 构造走
         * native 分配（{@code nInit}），成本比普通 Java 对象高一个数量级。流式渲染每个
         * chunk 都要为 tail 区域的每张图重建占位（case_3 尾部 9 张图 × 767 chunk），
         * 累计上万次分配，是图片区比纯文本区明显更慢的成因之一。
         *
         * <p>仅在 {@link PlaceholderDrawable#draw(Canvas)} 中使用，而 draw 只发生在 UI 线程，
         * 共享 Paint 无线程安全问题。
         */
        private static final Paint PLACEHOLDER_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

        static {
            PLACEHOLDER_PAINT.setColor(0xFFDDDDDD);
            PLACEHOLDER_PAINT.setStyle(Paint.Style.FILL);
        }

        /**
         * 进行中的加载任务。同一 destination 的其他 drawable（流式重渲染产生）不再
         * 重复发起请求，而是登记为等待者，加载完成后一起应用结果（等待块一起渲染）。
         */
        private final GlideStore glideStore;
        private final LruCache<String, Drawable> resultCache = new LruCache<>(RESULT_CACHE_SIZE);
        private final Map<String, List<AsyncDrawable>> pending = new HashMap<>(2);
        private final Map<AsyncDrawable, String> pendingKeys = new HashMap<>(2);

        /**
         * 记录已经加载失败的 destination。流式渲染每个 chunk 都会为未稳定区域重建
         * {@link AsyncDrawable}，失败若不记录，同一个注定失败的 URL 会被反复请求：
         * case_3 里 16 个 shields.io 的 SVG 在 Glide 下必定失败（Glide 默认不解码 SVG），
         * 3408 字符 / 3 = 1136 个 chunk × 16 ≈ 两万次注定失败的请求 —— 这既把整段渲染
         * 拖到 200 秒，也让图片区域在「0 高度」和「有高度」之间反复跳变。
         */
        private final Set<String> failed = new HashSet<>(2);

        GlideAsyncDrawableLoader(@NonNull GlideStore glideStore) {
            this.glideStore = glideStore;
        }

        /**
         * 用户配置的占位图提供器；{@code null} 时使用内置灰色占位。
         *
         * @see GlideImagesPlugin#placeholderProvider(PlaceholderProvider)
         */
        @Nullable
        private PlaceholderProvider placeholderProvider;

        void setPlaceholderProvider(@Nullable PlaceholderProvider provider) {
            this.placeholderProvider = provider;
        }

        @Override
        public void load(@NonNull AsyncDrawable drawable) {

            final String destination = drawable.getDestination();

            // 0. 之前已经失败过 -> 直接上占位，不再重复发起注定失败的请求
            if (failed.contains(destination)) {
                final Drawable error = resultCache.get(destination);
                drawable.setResult(error != null ? error : newPlaceholder(drawable));
                return;
            }

            // 1. 已加载过 -> 同步应用，立即可见，不再走异步请求
            final Drawable cached = resultCache.get(destination);
            if (cached != null) {
                DrawableUtils.applyIntrinsicBoundsIfEmpty(cached);
                drawable.setResult(cached);
                return;
            }

            // 2. 同一 destination 正在加载 -> 登记为等待者，加载完成后一起应用
            final List<AsyncDrawable> waiters = pending.get(destination);
            if (waiters != null) {
                waiters.add(drawable);
                pendingKeys.put(drawable, destination);
                return;
            }

            // 3. 首次加载
            final List<AsyncDrawable> list = new ArrayList<>(1);
            list.add(drawable);
            pending.put(destination, list);
            pendingKeys.put(drawable, destination);

            glideStore.load(drawable)
                    .into(new AsyncDrawableTarget(destination));
        }

        @Override
        public void cancel(@NonNull AsyncDrawable drawable) {
            final String destination = pendingKeys.remove(drawable);
            if (destination == null) {
                return;
            }
            final List<AsyncDrawable> waiters = pending.get(destination);
            if (waiters == null) {
                return;
            }
            waiters.remove(drawable);

            // 关键：<strong>不要在等待者清空时取消 Glide 请求</strong>。
            //
            // 流式渲染每个 chunk 都会 beforeSetText(unschedule) → afterSetText(schedule)，
            // 也就是每个 chunk 都会把同一个 AsyncDrawable detach 再 attach 一次。如果这里
            // 取消请求：
            //   1. Glide 回调的是 onLoadCleared 而不是 onLoadFailed，`failed` 永远记不下来，
            //      于是下一个 chunk 又重新发起一次注定失败的请求 —— case_3 的 16 个 SVG
            //      就是这么把整段渲染拖到 200 秒的；
            //   2. 请求被取消后当前 drawable 也不再是等待者，图片永远停在占位图上。
            //
            // 所以请求一旦发出就让它跑到底（成功写 resultCache、失败写 failed + resultCache），
            // 之后重建的 drawable 在 load() 里同步命中，不需要再发请求。
            // pendingKey（waiters 列表）也必须保留：清空后仍要能被下一次 attach 重新登记。
        }

        @Nullable
        @Override
        public Drawable placeholder(@NonNull AsyncDrawable drawable) {
            // 同一 URL 在文档里出现多次（case_3 里很常见）：第二次出现在 tail 时它已经是
            // deferred drawable，不会再走 load()，如果这里只给灰色占位，已经渲染出来的图
            // 会在流式的最后一帧「变回」灰色方块再跳回图片。命中缓存就直接复用结果。
            final Drawable cached = resultCache.get(drawable.getDestination());
            return cached != null ? cached : newPlaceholder(drawable);
        }

        /**
         * 占位图。加载中与加载失败时都必须给 {@link AsyncDrawable} 一个「尺寸确定」的占位：
         * 否则 drawable 的 bounds 为空并退化成 0×0，TextView 行高会在 0 与图片真实高度
         * 之间反复跳变 —— 流式渲染每来一个 chunk 就重建 tail 区域的 drawable，抖动会被
         * 放大得非常明显。
         *
         * <p>占位刻意<strong>不推测图片真实高度</strong>：需求就是加载中/失败先不解析高度，
         * 保持一个稳定占位，等图片真正加载完成后再触发一次完整渲染。
         *
         * <p>优先使用 {@link GlideImagesPlugin#placeholderProvider(PlaceholderProvider)}
         * 配置的自定义占位（返回 {@code null} 则回落），否则使用内置 24dp 灰色矩形。
         *
         * @param drawable 占位所属的 drawable，实现方可用它读取 destination / imageSize；
         *                 加载失败且所有等待者已被 detach 清空时可能为 {@code null}
         */
        @NonNull
        private Drawable newPlaceholder(@Nullable AsyncDrawable drawable) {
            final PlaceholderProvider provider = placeholderProvider;
            if (provider != null) {
                final Drawable custom = provider.newPlaceholder(drawable);
                if (custom != null) {
                    return custom;
                }
            }
            final float density = Resources.getSystem().getDisplayMetrics().density;
            final int d = Math.max(1, (int) (PLACEHOLDER_DP * density + 0.5f));
            return new PlaceholderDrawable(d, d);
        }

        /**
         * 纯色占位矩形。必须提供非空 intrinsic 尺寸，否则
         * {@code AsyncDrawable#setPlaceholderResult} 会退化成 {@code setBounds(0,0,1,1)}。
         */
        private static final class PlaceholderDrawable extends Drawable {

            private final int width;
            private final int height;
            private final Paint paint;

            PlaceholderDrawable(int width, int height) {
                this.width = width;
                this.height = height;
                this.paint = PLACEHOLDER_PAINT;
            }

            @Override
            public int getIntrinsicWidth() {
                return width;
            }

            @Override
            public int getIntrinsicHeight() {
                return height;
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.drawRect(getBounds(), paint);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }

            @NonNull
            @Override
            public String toString() {
                return "PlaceholderDrawable{" + width + "x" + height + '}';
            }
        }

        private class AsyncDrawableTarget extends CustomTarget<Drawable> {

            private final String destination;

            AsyncDrawableTarget(@NonNull String destination) {
                this.destination = destination;
            }

            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {

                // 先落缓存再分发：请求可能是在「等待者已被 detach 清空」的状态下完成的
                // （流式渲染每个 chunk 都会 detach/attach），此时同样要把结果缓存下来，
                // 否则下一个 chunk 重建的 drawable 会重新发一次请求。
                DrawableUtils.applyIntrinsicBoundsIfEmpty(resource);
                resultCache.put(destination, resource);

                final List<AsyncDrawable> waiters = pending.remove(destination);
                if (waiters == null) {
                    return;
                }

                for (AsyncDrawable waiter : waiters) {
                    // 已被 cancel 的等待者（pendingKeys 中已移除）直接跳过
                    if (pendingKeys.remove(waiter) == null
                            || !waiter.isAttached()) {
                        continue;
                    }
                    waiter.setResult(resource);
                }
            }

            @Override
            public void onLoadStarted(@Nullable Drawable placeholder) {
                // 刻意不把 Glide 的 placeholder 写进 AsyncDrawable。
                //
                // AsyncDrawable 只在 `result == null || result == 构造期的 loader.placeholder()`
                // 时才调用 load()。一旦这里把 Glide 的占位图设为 result，后续 chunk 重新
                // attach 时就不再触发 load()，该 drawable 也不再是等待者 —— 图片会永远停在
                // 占位图上。统一使用 AsyncDrawableLoader#placeholder 提供的占位，
                // 保证「未加载完成」的状态可识别、可重试。
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                // 失败必须留下痕迹，且必须在分发之前落定（等待者可能已被 detach 清空）：
                //  1) failed —— 流式渲染每个 chunk 都会重建 AsyncDrawable，不记录的话
                //     一个注定失败的 URL 会被请求上千次（case_3 里 16 个 shields.io 的 SVG
                //     在 Glide 下必定失败，1136 个 chunk × 16 ≈ 两万次请求，直接把整段渲染
                //     拖到 200 秒，并让行高反复抖动）；
                //  2) resultCache —— 让「失败结果」也能像成功结果一样被同步复用，
                //     后续重建的 drawable 一 attach 就拿到确定尺寸，不再回到无结果的 0 高度。
                failed.add(destination);

                Drawable fallback = errorDrawable;
                if (fallback == null) {
                    // 上下文从等待者里取（可能都已被 detach 清空，但仍可作为
                    // PlaceholderProvider 的参数传给实现方）；没有等待者则传 null
                    final List<AsyncDrawable> ctx = pending.get(destination);
                    fallback = newPlaceholder(ctx != null && !ctx.isEmpty() ? ctx.get(0) : null);
                }
                DrawableUtils.applyIntrinsicBoundsIfEmpty(fallback);
                resultCache.put(destination, fallback);

                final List<AsyncDrawable> waiters = pending.remove(destination);
                if (waiters == null) {
                    return;
                }

                for (AsyncDrawable waiter : waiters) {
                    pendingKeys.remove(waiter);
                    if (waiter.isAttached()) {
                        waiter.setResult(fallback);
                    }
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                // 结果已写入 resultCache，无需清空；等待者消失时由 cancel 处理
            }
        }
    }
}
