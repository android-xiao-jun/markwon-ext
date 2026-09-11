package io.noties.markwon.core.scroll;

import android.text.Spanned;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.CodeBlockCopyTheme;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.factory.CodeBlockSpanFactory;

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
 *     <li>the leading sentinel line becomes the header row (language label, copy button), the
 *     trailing one the footer row (scrollbar + bottom padding);</li>
 *     <li>the background, the header and the scrollbar are painted by
 *     {@code CodeBlockSpan}, as it already owns the per-line background drawing;</li>
 *     <li>a tap on the <b>copy button</b> hands the block's original source to the host, which
 *     is the only party that decides what a copy means
 *     ({@link #onCodeBlockCopy(CodeBlockCopyListener)}).</li>
 * </ol>
 *
 * <h3>Everything the copy button needs lives here</h3>
 * The button is this plugin's feature and nothing else's, so both halves of it are configured
 * on the plugin:
 * <ul>
 *     <li><b>the style</b> — {@link #codeBlockCopyTheme(CodeBlockCopyTheme)}, handed by
 *     {@link #configureSpansFactory(MarkwonSpansFactory.Builder)} straight to the code block
 *     span factory (it never travels through {@link MarkwonTheme});</li>
 *     <li><b>the behaviour</b> — {@link #onCodeBlockCopy(CodeBlockCopyListener)}, the host's
 *     clipboard write.</li>
 * </ul>
 *
 * <pre>
 * Markwon.builder(context)
 *         .usePlugin(CodeBlockScrollPlugin.create()
 *                 .codeBlockCopyTheme(CodeBlockCopyTheme.builder()
 *                         .enabled(true)
 *                         .text("复制")
 *                         .build())
 *                 .onCodeBlockCopy((textView, code) -&gt; copyToClipboard(textView, code)))
 *         .build();
 * </pre>
 *
 * <p>The code text itself stays in the {@code Spanned}: selection, copy and the
 * byte-identical guarantee of {@code Markwon#appendMarkdown} are unaffected.
 *
 * @see MarkwonTheme.Builder#codeBlockScrollable(boolean)
 * @see CodeBlockCopyTheme
 * @since 4.6.3
 */
public class CodeBlockScrollPlugin extends AbstractMarkwonPlugin {

    @NonNull
    public static CodeBlockScrollPlugin create() {
        return new CodeBlockScrollPlugin();
    }

    /**
     * Style of the header row's copy button, or {@code null} (default) for "no button at all".
     *
     * @see #codeBlockCopyTheme(CodeBlockCopyTheme)
     */
    @Nullable
    private CodeBlockCopyTheme copyTheme;

    /**
     * Host implementation of the copy button — receives the source of the block that was
     * tapped and decides what to do with it (write the clipboard, show a {@code Toast}, track
     * an event…). Belongs to the plugin rather than to the {@code TextView}, so it can be
     * re-assigned between {@code setText} calls.
     */
    @Nullable
    private CodeBlockCopyListener copyListener;

    @SuppressWarnings("WeakerAccess")
    public CodeBlockScrollPlugin() {
    }

    /**
     * Sets the style of the copy button drawn on the right end of the header row.
     * {@code null} (default) = <b>no button</b> — the whole feature is opt-in.
     *
     * <p>The style is handed over directly: {@link #configureSpansFactory(MarkwonSpansFactory.Builder)}
     * re-registers the code block span factory with it, and
     * {@link #beforeSetText(TextView, Spanned)} pushes it into every header row. It is
     * deliberately <b>not</b> part of {@link MarkwonTheme} — the button is this plugin's
     * concern, and routing its style through the global theme would expose it from every
     * corner of the library.
     *
     * <p>An <b>unconfigured</b> header height is raised to one line of text while the button is
     * enabled, so the feature works without configuring the row by hand; an explicit {@code 0}
     * still means "no header row" and disables the button with it.
     *
     * @param copyTheme the style, or {@code null} to remove the button
     * @see CodeBlockCopyTheme
     * @since 4.6.3
     */
    @NonNull
    public CodeBlockScrollPlugin codeBlockCopyTheme(@Nullable CodeBlockCopyTheme copyTheme) {
        this.copyTheme = copyTheme;
        return this;
    }

    /**
     * Takes over the copy button: whenever it is tapped, the host is handed the original source
     * of that block. <b>The library does not write the clipboard</b> — that is the whole point
     * of this callback, see {@link CodeBlockCopyListener}.
     *
     * <p>Without a listener attached the button is a no-op (and does not flash its confirmation
     * either), so the two go together: {@link #codeBlockCopyTheme(CodeBlockCopyTheme)} decides
     * what the button looks like, this decides what it does.
     *
     * @param copyListener the host implementation, or {@code null} (default) to detach it
     * @see CodeBlockCopyListener
     * @since 4.6.3
     */
    @NonNull
    public CodeBlockScrollPlugin onCodeBlockCopy(@Nullable CodeBlockCopyListener copyListener) {
        this.copyListener = copyListener;
        return this;
    }

    @Override
    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
        builder.codeBlockScrollable(true);
    }

    /**
     * Re-registers the code block span factory with this plugin's copy-button style.
     *
     * <p>Running after {@code CorePlugin} (which is always registered first, by
     * {@code Markwon.builder}), this replaces the factory it installed for both code block
     * nodes — the only way to get the style down to {@code CodeBlockSpan} without putting it
     * on the shared theme.
     */
    @Override
    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
        final CodeBlockSpanFactory factory = new CodeBlockSpanFactory(copyTheme);
        builder.setFactory(FencedCodeBlock.class, factory)
                .setFactory(IndentedCodeBlock.class, factory);
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
        // the rows are created by CorePlugin, which has no idea this plugin exists — so the
        // style has to be pushed into them before they are measured
        CodeBlockScrollHelper.injectCopy(markdown, copyTheme);
        CodeBlockScrollHelper.injectViewport(textView, markdown);
    }

    @Override
    public void afterSetText(@NonNull TextView textView) {
        CodeBlockScrollHelper.attach(textView, copyListener);
    }
}
