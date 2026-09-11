package io.noties.markwon.core.scroll;

import android.text.Spanned;
import android.widget.TextView;

import androidx.annotation.NonNull;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;

/**
 * Turns fenced/indented code blocks into horizontally scrollable, non-wrapping blocks with
 * a header showing the language and a scrollbar (the style of
 * {@code app-sample/io.noties.markwon.sample.CodeBlockSpan}, but rendered inside the
 * markdown {@code Spanned} instead of replacing it).
 *
 * <p>How it works:
 * <ol>
 *     <li>every code line gets a {@code CodeBlockLineSpan} — a {@code ReplacementSpan}, so
 *     the line is never wrapped — clipped to the viewport and translated by the shared
 *     {@link CodeBlockScrollState};</li>
 *     <li>the leading sentinel line becomes the header row (language label), the trailing
 *     one the footer row (scrollbar + bottom padding);</li>
 *     <li>the background, the header and the scrollbar are painted by
 *     {@code CodeBlockSpan}, as it already owns the per-line background drawing.</li>
 * </ol>
 *
 * <p>The code text itself stays in the {@code Spanned}: selection, copy and the
 * byte-identical guarantee of {@code Markwon#appendMarkdown} are unaffected.
 *
 * <pre>
 * Markwon.builder(context)
 *         .usePlugin(CodeBlockScrollPlugin.create())
 *         .build();
 * </pre>
 *
 * @see MarkwonTheme.Builder#codeBlockScrollable(boolean)
 * @since 4.6.3
 */
public class CodeBlockScrollPlugin extends AbstractMarkwonPlugin {

    @NonNull
    public static CodeBlockScrollPlugin create() {
        return new CodeBlockScrollPlugin();
    }

    @SuppressWarnings("WeakerAccess")
    public CodeBlockScrollPlugin() {
    }

    @Override
    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
        builder.codeBlockScrollable(true);
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
        CodeBlockScrollHelper.injectViewport(textView, markdown);
    }

    @Override
    public void afterSetText(@NonNull TextView textView) {
        CodeBlockScrollHelper.attach(textView);
    }
}
