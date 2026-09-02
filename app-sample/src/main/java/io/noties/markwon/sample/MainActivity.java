package io.noties.markwon.sample;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.Target;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.data.DataUriSchemeHandler;
import io.noties.markwon.image.file.FileSchemeHandler;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.image.network.NetworkSchemeHandler;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.simple.ext.SimpleExtPlugin;
import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;

/**
 * 案例页面：从 {@code res/raw} 读取 Markdown 文本，交给 Markwon 渲染到 TextView。
 *
 * <p>渲染链路：
 * <ol>
 *     <li>{@link #readRawText(int)} 读取 raw 资源中的 Markdown 字符串；</li>
 *     <li>{@link #createMarkwon(RequestManager)} 构建 Markwon 实例并注册插件；</li>
 *     <li>{@link Markwon#setMarkdown(TextView, String)} 解析 + 渲染 + 调度异步图片 / 公式。</li>
 * </ol>
 *
 * <p><strong>关于图片方案</strong>：{@link GlideImagesPlugin} 与 {@link ImagesPlugin} 都会向
 * {@code MarkwonConfiguration} 注册 {@code asyncDrawableLoader}，同时注册后一个覆盖另一个，
 * 所以二者只能二选一，由 {@link #IMAGE_MODE} 常量切换。
 */
public class MainActivity extends AppCompatActivity {

    /**
     * 图片加载方案。改成 {@link #IMAGE_MODE_IMAGES_PLUGIN} 即可切到 markwon-image 自带的加载器
     * （走 HttpURLConnection，支持 data-uri / file / SVG / GIF，不依赖 Glide）。
     */
    private static final int IMAGE_MODE_GLIDE = 0;
    private static final int IMAGE_MODE_IMAGES_PLUGIN = 1;
    private static final int IMAGE_MODE = IMAGE_MODE_GLIDE;

    private static final String TAG = "MainActivity";

    private Markwon markwon;
    private TextView textView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text_view);

        // 使用 applicationContext，避免 Activity 销毁后 Glide 仍在请求导致异常
        markwon = createMarkwon(Glide.with(getApplicationContext()));

        findViewById(R.id.button_image_case).setOnClickListener(v -> show(R.raw.case_image));
        findViewById(R.id.button_all_plugins).setOnClickListener(v -> show(R.raw.case_all_plugins));

        show(R.raw.case_image);
    }

    private void show(@RawRes int resId) {
        markwon.setMarkdown(textView, readRawText(resId));
    }

    /**
     * 构建 Markwon 实例。{@link Markwon#builder} 默认已注册 {@code CorePlugin}，这里叠加其余插件。
     */
    @NonNull
    private Markwon createMarkwon(@NonNull final RequestManager requestManager) {
        final Markwon.Builder builder = Markwon.builder(this);

        if (IMAGE_MODE == IMAGE_MODE_GLIDE) {
            builder.usePlugin(createGlideImagesPlugin(requestManager));
        } else {
            builder.usePlugin(createImagesPlugin());
        }

        builder
                // 内联解析器增强（反斜杠转义、更完整的内联规则）
                .usePlugin(MarkwonInlineParserPlugin.create())
                // HTML 标签，默认支持 u / b / i / sub / sup / strike / blockquote / h1-h6 / img / a 等
                .usePlugin(HtmlPlugin.create())
                // 纯文本 URL / 邮箱 / 手机号 自动变成可点击链接
                .usePlugin(LinkifyPlugin.create())
                // ~~删除线~~
                .usePlugin(StrikethroughPlugin.create())
                // - [ ] / - [x] 任务列表
                .usePlugin(TaskListPlugin.create(this))
                // GFM 表格
                .usePlugin(TablePlugin.create(this))
                // 自定义分隔符：==高亮== 与 ++下划线++
                .usePlugin(SimpleExtPlugin.create(plugin -> {
                    plugin.addExtension(2, '=', (configuration, props) ->
                            new BackgroundColorSpan(0xFFFFF176));
                    plugin.addExtension(2, '+', (configuration, props) ->
                            new UnderlineSpan());
                }))
                // LaTeX 公式：$$块级$$ 与 $行内$（行内解析默认关闭，需显式打开）
                .usePlugin(JLatexMathPlugin.create(sp2px(16), config ->
                        config
                                .inlinesEnabled(true)
                                .blocksEnabled(true)))
                // 代码块高亮，语法定义见 SampleGrammarLocator，未命中的语言回退到 java
                .usePlugin(SyntaxHighlightPlugin.create(
                        new Prism4j(new SampleGrammarLocator()),
                        Prism4jThemeDarkula.create(0xFF2B2B2B),
                        "java"));

        return builder.build();
    }

    /**
     * 图片方案 A：Glide。Markwon 先插入占位 {@link AsyncDrawable}，Glide 回调写入真实 Drawable，
     * 再由 {@code AsyncDrawableScheduler} 触发 TextView 重绘。
     */
    @NonNull
    private GlideImagesPlugin createGlideImagesPlugin(@NonNull final RequestManager requestManager) {
        return GlideImagesPlugin.create(new GlideImagesPlugin.GlideStore() {
            @NonNull
            @Override
            public RequestBuilder<Drawable> load(@NonNull AsyncDrawable drawable) {
                return requestManager
                        .load(drawable.getDestination())
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error);
            }

            @Override
            public void cancel(@NonNull Target<?> target) {
                requestManager.clear(target);
            }
        });
    }

    /**
     * 图片方案 B：markwon-image 自带的异步加载器，不依赖任何图片框架。
     * classpath 中存在 androidsvg / android-gif-drawable 时会自动启用 SVG、GIF 解码。
     */
    @NonNull
    private ImagesPlugin createImagesPlugin() {
        return ImagesPlugin.create(plugin -> {
            // data:image/png;base64,... 内联图片
            plugin.addSchemeHandler(DataUriSchemeHandler.create());
            // file:// 本地文件（读取外部存储需要 READ_EXTERNAL_STORAGE 权限）
            plugin.addSchemeHandler(FileSchemeHandler.create());
            // http / https，可换成 OkHttpNetworkSchemeHandler.create(okHttpClient)
            plugin.addSchemeHandler(NetworkSchemeHandler.create());

            plugin.placeholderProvider(drawable ->
                    ContextCompat.getDrawable(this, R.drawable.ic_image_placeholder));

            plugin.errorHandler((url, throwable) -> {
                Log.w(TAG, "图片加载失败: " + url, throwable);
                return ContextCompat.getDrawable(this, R.drawable.ic_image_error);
            });
        });
    }

    private float sp2px(float sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    /**
     * 读取 {@code res/raw} 下的文本内容（UTF-8）。
     */
    @NonNull
    private String readRawText(@RawRes int resId) {
        InputStream inputStream = null;
        try {
            inputStream = getResources().openRawResource(resId);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), Charset.forName("UTF-8"));
        } catch (IOException e) {
            Log.e(TAG, "Exception reading raw markdown resource", e);
            return getString(R.string.markdown_load_failed);
        } finally {
            closeQuietly(inputStream);
        }
    }

    private static void closeQuietly(@Nullable InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // no-op
            }
        }
    }
}
