package io.noties.markwon.ext.latex;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spanned;
import android.util.LruCache;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;

import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableLoader;
import io.noties.markwon.image.AsyncDrawableScheduler;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.image.DrawableUtils;
import io.noties.markwon.image.ImageSizeResolver;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import ru.noties.jlatexmath.JLatexMathDrawable;

/**
 * @since 3.0.0
 */
public class JLatexMathPlugin extends AbstractMarkwonPlugin {

    /**
     * @since 4.3.0
     */
    public interface ErrorHandler {

        /**
         * @param latex that caused the error
         * @param error occurred
         * @return (optional) error drawable that will be used instead (if drawable will have bounds
         * it will be used, if not intrinsic bounds will be set)
         */
        @Nullable
        Drawable handleError(@NonNull String latex, @NonNull Throwable error);
    }

    public interface BuilderConfigure {
        void configureBuilder(@NonNull Builder builder);
    }

    @NonNull
    public static JLatexMathPlugin create(float textSize) {
        return new JLatexMathPlugin(builder(textSize).build());
    }

    /**
     * @since 4.3.0
     */
    @NonNull
    public static JLatexMathPlugin create(@Px float inlineTextSize, @Px float blockTextSize) {
        return new JLatexMathPlugin(builder(inlineTextSize, blockTextSize).build());
    }

    @NonNull
    public static JLatexMathPlugin create(@NonNull Config config) {
        return new JLatexMathPlugin(config);
    }

    @NonNull
    public static JLatexMathPlugin create(@Px float textSize, @NonNull BuilderConfigure builderConfigure) {
        final Builder builder = builder(textSize);
        builderConfigure.configureBuilder(builder);
        return new JLatexMathPlugin(builder.build());
    }

    /**
     * @since 4.3.0
     */
    @NonNull
    public static JLatexMathPlugin create(
            @Px float inlineTextSize,
            @Px float blockTextSize,
            @NonNull BuilderConfigure builderConfigure) {
        final Builder builder = builder(inlineTextSize, blockTextSize);
        builderConfigure.configureBuilder(builder);
        return new JLatexMathPlugin(builder.build());
    }

    @NonNull
    public static JLatexMathPlugin.Builder builder(@Px float textSize) {
        return new Builder(JLatexMathTheme.builder(textSize));
    }

    /**
     * @since 4.3.0
     */
    @NonNull
    public static JLatexMathPlugin.Builder builder(@Px float inlineTextSize, @Px float blockTextSize) {
        return new Builder(JLatexMathTheme.builder(inlineTextSize, blockTextSize));
    }

    @VisibleForTesting
    static class Config {

        // @since 4.3.0
        final JLatexMathTheme theme;

        // @since 4.3.0
        final boolean blocksEnabled;
        final boolean blocksLegacy;
        final boolean inlinesEnabled;
        final boolean allowInlineSingle$;

        // @since 4.3.0
        final ErrorHandler errorHandler;

        final ExecutorService executorService;

        Config(@NonNull Builder builder) {
            this.theme = builder.theme.build();
            this.blocksEnabled = builder.blocksEnabled;
            this.blocksLegacy = builder.blocksLegacy;
            this.inlinesEnabled = builder.inlinesEnabled;
            this.allowInlineSingle$ = builder.allowInlineSingle$;
            this.errorHandler = builder.errorHandler;
            // @since 4.0.0
            ExecutorService executorService = builder.executorService;
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
            }
            this.executorService = executorService;
        }
    }

    @VisibleForTesting
    final Config config;

    private final JLatextAsyncDrawableLoader jLatextAsyncDrawableLoader;
    private final JLatexBlockImageSizeResolver jLatexBlockImageSizeResolver;
    private final ImageSizeResolver inlineImageSizeResolver;

    @SuppressWarnings("WeakerAccess")
    JLatexMathPlugin(@NonNull Config config) {
        this.config = config;
        this.jLatextAsyncDrawableLoader = new JLatextAsyncDrawableLoader(config);
        this.jLatexBlockImageSizeResolver = new JLatexBlockImageSizeResolver(config.theme.blockFitCanvas());
        this.inlineImageSizeResolver = new InlineImageSizeResolver();
    }

    @Override
    public void configure(@NonNull Registry registry) {
        if (config.inlinesEnabled) {
            registry.require(MarkwonInlineParserPlugin.class)
                    .factoryBuilder()
                    .addInlineProcessor(new JLatexMathInlineProcessor(config.allowInlineSingle$));
        }
    }

    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        // @since 4.3.0
        if (config.blocksEnabled) {
            if (config.blocksLegacy) {
                builder.customBlockParserFactory(new JLatexMathBlockParserLegacy.Factory());
            } else {
                builder.customBlockParserFactory(new JLatexMathBlockParser.Factory());
            }
        }
    }

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        addBlockVisitor(builder);
        addInlineVisitor(builder);
    }

    private void addBlockVisitor(@NonNull MarkwonVisitor.Builder builder) {
        if (!config.blocksEnabled) {
            return;
        }

        builder.on(JLatexMathBlock.class, new MarkwonVisitor.NodeVisitor<JLatexMathBlock>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull JLatexMathBlock jLatexMathBlock) {

                visitor.blockStart(jLatexMathBlock);

                final String latex = jLatexMathBlock.latex();

                final int length = visitor.length();

                // @since 4.0.2 we cannot append _raw_ latex as a placeholder-text,
                // because Android will draw formula for each line of text, thus
                // leading to formula duplicated (drawn on each line of text)
                visitor.builder().append(prepareLatexTextPlaceholder(latex));

                final MarkwonConfiguration configuration = visitor.configuration();

                final AsyncDrawableSpan span = new JLatexAsyncDrawableSpan(
                        configuration.theme(),
                        new JLatextAsyncDrawable(
                                latex,
                                jLatextAsyncDrawableLoader,
                                jLatexBlockImageSizeResolver,
                                null,
                                true),
                        config.theme.blockTextColor()
                );

                visitor.setSpans(length, span);

                visitor.blockEnd(jLatexMathBlock);
            }
        });
    }

    private void addInlineVisitor(@NonNull MarkwonVisitor.Builder builder) {

        if (!config.inlinesEnabled) {
            return;
        }

        builder.on(JLatexMathNode.class, new MarkwonVisitor.NodeVisitor<JLatexMathNode>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull JLatexMathNode jLatexMathNode) {
                final String latex = jLatexMathNode.latex();

                final int length = visitor.length();

                // @since 4.0.2 we cannot append _raw_ latex as a placeholder-text,
                // because Android will draw formula for each line of text, thus
                // leading to formula duplicated (drawn on each line of text)
                visitor.builder().append(prepareLatexTextPlaceholder(latex));

                final MarkwonConfiguration configuration = visitor.configuration();

                final AsyncDrawableSpan span = new JLatexInlineAsyncDrawableSpan(
                        configuration.theme(),
                        new JLatextAsyncDrawable(
                                latex,
                                jLatextAsyncDrawableLoader,
                                inlineImageSizeResolver,
                                null,
                                false),
                        config.theme.inlineTextColor()
                );

                visitor.setSpans(length, span);
            }
        });
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
        AsyncDrawableScheduler.unschedule(textView);
    }

    @Override
    public void afterSetText(@NonNull TextView textView) {
        AsyncDrawableScheduler.schedule(textView);
    }

    // @since 4.0.2
    @VisibleForTesting
    @NonNull
    static String prepareLatexTextPlaceholder(@NonNull String latex) {
        return latex.replace('\n', ' ').trim();
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static class Builder {

        // @since 4.3.0
        private final JLatexMathTheme.Builder theme;

        // @since 4.3.0
        private boolean blocksEnabled = true;
        private boolean blocksLegacy;
        private boolean inlinesEnabled;
        private boolean allowInlineSingle$;

        // @since 4.3.0
        private ErrorHandler errorHandler;

        // @since 4.0.0
        private ExecutorService executorService;

        Builder(@NonNull JLatexMathTheme.Builder builder) {
            this.theme = builder;
        }

        @NonNull
        public JLatexMathTheme.Builder theme() {
            return theme;
        }

        /**
         * @since 4.3.0
         */
        @NonNull
        public Builder blocksEnabled(boolean blocksEnabled) {
            this.blocksEnabled = blocksEnabled;
            return this;
        }

        /**
         * @param blocksLegacy indicates if blocks should be handled in legacy mode ({@code pre 4.3.0})
         * @since 4.3.0
         */
        @NonNull
        public Builder blocksLegacy(boolean blocksLegacy) {
            this.blocksLegacy = blocksLegacy;
            return this;
        }

        /**
         * @param inlinesEnabled indicates if inline parsing should be enabled.
         *                       NB, this requires `MarkwonInlineParserPlugin` to be used when creating `MarkwonInstance`
         * @since 4.3.0
         */
        @NonNull
        public Builder inlinesEnabled(boolean inlinesEnabled) {
            this.inlinesEnabled = inlinesEnabled;
            return this;
        }

        /**
         * @param inlineSingleDollar indicates if $xxx$ is valid.
         * @since 4.7.0
         */
        @NonNull
        public Builder allowInlinesSingleDollar(boolean inlineSingleDollar){
            this.allowInlineSingle$ = inlineSingleDollar;
            return this;
        }

        @NonNull
        public Builder errorHandler(@Nullable ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * @since 4.0.0
         */
        @SuppressWarnings("WeakerAccess")
        @NonNull
        public Builder executorService(@NonNull ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        @NonNull
        public Config build() {
            return new Config(this);
        }
    }

    // @since 4.0.0
    static class JLatextAsyncDrawableLoader extends AsyncDrawableLoader {

        /**
         * 已渲染结果缓存（key = block标记 + latex 源文）。SSE / 增量渲染时每个 chunk
         * 都会为未稳定区域重建 {@link JLatextAsyncDrawable}，若没有这层缓存，同一公式
         * 会反复提交后台解析（jlatexmath 解析开销大）并从空白重新显示，造成闪烁。
         * 命中缓存时在主线程同步应用结果。
         */
        private static final int RESULT_CACHE_SIZE = 3 * 8;

        private final Config config;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final LruCache<String, JLatexMathDrawable> resultCache = new LruCache<>(RESULT_CACHE_SIZE);
        // 进行中的渲染任务：同一公式的其他 drawable（流式重渲染产生）登记为等待者，渲染完成后一起应用
        private final Map<String, List<AsyncDrawable>> pending = new HashMap<>(2);
        private final Map<AsyncDrawable, String> pendingKeys = new HashMap<>(2);

        JLatextAsyncDrawableLoader(@NonNull Config config) {
            this.config = config;
        }

        @Override
        public void load(@NonNull final AsyncDrawable drawable) {

            // this method must be called from main-thread only (thus synchronization can be skipped)

            final String latex = drawable.getDestination();
            final String key = (drawable instanceof JLatextAsyncDrawable
                    && ((JLatextAsyncDrawable) drawable).isBlock()
                    ? "b|"
                    : "i|") + latex;

            // 1. 已渲染过 -> 同步应用，立即可见，不再提交后台解析
            final JLatexMathDrawable cached = resultCache.get(key);
            if (cached != null) {
                drawable.setResult(cached);
                return;
            }

            // 2. 同一公式正在渲染 -> 登记为等待者，渲染完成后一起应用（等待块一起渲染）
            final List<AsyncDrawable> waiters = pending.get(key);
            if (waiters != null) {
                waiters.add(drawable);
                pendingKeys.put(drawable, key);
                return;
            }

            // 3. 首次渲染，提交后台任务
            final List<AsyncDrawable> list = new ArrayList<>(1);
            list.add(drawable);
            pending.put(key, list);
            pendingKeys.put(drawable, key);

            // as asyncDrawable is immutable, it won't have destination changed (so there is no need
            // to cancel any started tasks)
            config.executorService.submit(new Runnable() {
                @Override
                public void run() {
                    // @since 4.0.1 wrap in try-catch block and add error logging
                    try {
                        execute();
                    } catch (Throwable t) {
                        // @since 4.3.0 add error handling
                        final ErrorHandler errorHandler = config.errorHandler;
                        if (errorHandler == null) {
                            // as before
                            Log.e(
                                    "JLatexMathPlugin",
                                    "Error displaying latex: `" + drawable.getDestination() + "`",
                                    t);
                        } else {
                            // just call `getDestination` without casts and checks
                            final Drawable errorDrawable = errorHandler.handleError(
                                    drawable.getDestination(),
                                    t
                            );
                            if (errorDrawable != null) {
                                DrawableUtils.applyIntrinsicBoundsIfEmpty(errorDrawable);
                                postResult(key, errorDrawable);
                            }
                        }
                    }
                }

                private void execute() {

                    final JLatexMathDrawable jLatexMathDrawable;

                    final JLatextAsyncDrawable jLatextAsyncDrawable = (JLatextAsyncDrawable) drawable;

                    if (jLatextAsyncDrawable.isBlock()) {
                        jLatexMathDrawable = createBlockDrawable(jLatextAsyncDrawable);
                    } else {
                        jLatexMathDrawable = createInlineDrawable(jLatextAsyncDrawable);
                    }

                    postResult(key, jLatexMathDrawable);
                }
            });
        }

        @Override
        public void cancel(@NonNull AsyncDrawable drawable) {

            // this method also must be called from main thread only

            final String key = pendingKeys.remove(drawable);
            if (key == null) {
                return;
            }
            final List<AsyncDrawable> waiters = pending.get(key);
            if (waiters != null) {
                waiters.remove(drawable);
                if (waiters.isEmpty()) {
                    // 等待者全部消失，后台任务继续跑完（结果仍写入缓存，之后可同步复用）
                    pending.remove(key);
                }
            }
        }

        @Nullable
        @Override
        public Drawable placeholder(@NonNull AsyncDrawable drawable) {
            return null;
        }

        /**
         * 将渲染结果写入缓存，并投递到主线程应用到所有等待者。
         * LruCache 线程安全，可从后台线程写入。
         */
        private void postResult(@NonNull final String key, @NonNull final Drawable result) {
            if (result instanceof JLatexMathDrawable) {
                resultCache.put(key, (JLatexMathDrawable) result);
            }

            handler.postAtTime(new Runnable() {
                @Override
                public void run() {
                    final List<AsyncDrawable> waiters = pending.remove(key);
                    if (waiters == null) {
                        return;
                    }
                    for (AsyncDrawable waiter : waiters) {
                        // 已被 cancel 的等待者（pendingKeys 中已移除）直接跳过
                        if (pendingKeys.remove(waiter) == null
                                || !waiter.isAttached()) {
                            continue;
                        }
                        waiter.setResult(result);
                    }
                }
            }, key, SystemClock.uptimeMillis());
        }

        // @since 4.3.0
        @NonNull
        private JLatexMathDrawable createBlockDrawable(@NonNull JLatextAsyncDrawable drawable) {

            final String latex = drawable.getDestination();

            final JLatexMathTheme theme = config.theme;

            final JLatexMathTheme.BackgroundProvider backgroundProvider = theme.blockBackgroundProvider();
            final JLatexMathTheme.Padding padding = theme.blockPadding();
            final int color = theme.blockTextColor();

            final JLatexMathDrawable.Builder builder = JLatexMathDrawable.builder(latex)
                    .textSize(theme.blockTextSize())
                    .align(theme.blockHorizontalAlignment());

            if (backgroundProvider != null) {
                builder.background(backgroundProvider.provide());
            }

            if (padding != null) {
                builder.padding(padding.left, padding.top, padding.right, padding.bottom);
            }

            if (color != 0) {
                builder.color(color);
            }

            return builder.build();
        }

        // @since 4.3.0
        @NonNull
        private JLatexMathDrawable createInlineDrawable(@NonNull JLatextAsyncDrawable drawable) {

            final String latex = drawable.getDestination();

            final JLatexMathTheme theme = config.theme;

            final JLatexMathTheme.BackgroundProvider backgroundProvider = theme.inlineBackgroundProvider();
            final JLatexMathTheme.Padding padding = theme.inlinePadding();
            final int color = theme.inlineTextColor();

            final JLatexMathDrawable.Builder builder = JLatexMathDrawable.builder(latex)
                    .textSize(theme.inlineTextSize());

            if (backgroundProvider != null) {
                builder.background(backgroundProvider.provide());
            }

            if (padding != null) {
                builder.padding(padding.left, padding.top, padding.right, padding.bottom);
            }

            if (color != 0) {
                builder.color(color);
            }

            return builder.build();
        }
    }

    private static class InlineImageSizeResolver extends ImageSizeResolver {

        @NonNull
        @Override
        public Rect resolveImageSize(@NonNull AsyncDrawable drawable) {

            // @since 4.4.0 resolve inline size (scale down if exceed available width)
            final Rect imageBounds = drawable.getResult().getBounds();
            final int canvasWidth = drawable.getLastKnownCanvasWidth();
            final int w = imageBounds.width();

            if (w > canvasWidth) {
                // here we must scale it down (keeping the ratio)
                final float ratio = (float) w / imageBounds.height();
                final int h = (int) (canvasWidth / ratio + .5F);
                return new Rect(0, 0, canvasWidth, h);
            }

            return imageBounds;
        }
    }
}
