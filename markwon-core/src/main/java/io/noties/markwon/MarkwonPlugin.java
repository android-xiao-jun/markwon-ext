package io.noties.markwon;

import android.text.Spanned;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.movement.MovementMethodPlugin;

/**
 * Class represents a plugin (extension) to Markwon to configure how parsing and rendering
 * of markdown is carried on.
 *
 * @see AbstractMarkwonPlugin
 * @see CorePlugin
 * @see MovementMethodPlugin
 * @since 3.0.0
 */
public interface MarkwonPlugin {

    /**
     * @see Registry#require(Class, Action)
     * @since 4.0.0
     */
    interface Action<P extends MarkwonPlugin> {
        void apply(@NonNull P p);
    }

    /**
     * @see #configure(Registry)
     * @since 4.0.0
     */
    interface Registry {

        @NonNull
        <P extends MarkwonPlugin> P require(@NonNull Class<P> plugin);

        <P extends MarkwonPlugin> void require(
                @NonNull Class<P> plugin,
                @NonNull Action<? super P> action);
    }

    /**
     * This method will be called before any other during {@link Markwon} instance construction.
     *
     * @since 4.0.0
     */
    void configure(@NonNull Registry registry);

    /**
     * Method to configure <code>org.commonmark.parser.Parser</code> (for example register custom
     * extension, etc).
     */
    void configureParser(@NonNull Parser.Builder builder);

    /**
     * Modify {@link MarkwonTheme} that is used for rendering of markdown.
     *
     * @see MarkwonTheme
     * @see MarkwonTheme.Builder
     */
    void configureTheme(@NonNull MarkwonTheme.Builder builder);

    /**
     * Configure {@link MarkwonConfiguration}
     *
     * @see MarkwonConfiguration
     * @see MarkwonConfiguration.Builder
     */
    void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder);

    /**
     * Configure {@link MarkwonVisitor} to accept new node types or override already registered nodes.
     *
     * @see MarkwonVisitor
     * @see MarkwonVisitor.Builder
     */
    void configureVisitor(@NonNull MarkwonVisitor.Builder builder);

    /**
     * Configure {@link MarkwonSpansFactory} to change what spans are used for certain node types.
     *
     * @see MarkwonSpansFactory
     * @see MarkwonSpansFactory.Builder
     */
    void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder);

    /**
     * Process input markdown and return new string to be used in parsing stage further.
     * Can be described as <code>pre-processing</code> of markdown String.
     *
     * @param markdown String to process
     * @return processed markdown String
     */
    @NonNull
    String processMarkdown(@NonNull String markdown);

    /**
     * This method will be called <strong>before</strong> rendering will occur thus making possible
     * to <code>post-process</code> parsed node (make changes for example).
     *
     * @param node root parsed org.commonmark.node.Node
     */
    void beforeRender(@NonNull Node node);

    /**
     * This method will be called <strong>after</strong> rendering (but before applying markdown to a
     * TextView, if such action will happen). It can be used to clean some
     * internal state, or trigger certain action. Please note that modifying <code>node</code> won\'t
     * have any effect as it has been already <i>visited</i> at this stage.
     *
     * @param node    root parsed org.commonmark.node.Node
     * @param visitor {@link MarkwonVisitor} instance used to render markdown
     */
    void afterRender(@NonNull Node node, @NonNull MarkwonVisitor visitor);

    /**
     * This method will be called <strong>before</strong> calling <code>TextView#setText</code>.
     * <p>
     * It can be useful to prepare a TextView for markdown. For example {@code ru.noties.markwon.image.ImagesPlugin}
     * uses this method to unregister previously registered {@link AsyncDrawableSpan}
     * (if there are such spans in this TextView at this point). Or {@link CorePlugin}
     * which measures ordered list numbers
     *
     * @param textView TextView to which <code>markdown</code> will be applied
     * @param markdown Parsed markdown
     */
    void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown);

    /**
     * This method will be called <strong>after</strong> markdown was applied.
     * <p>
     * It can be useful to trigger certain action on spans/textView. For example {@code ru.noties.markwon.image.ImagesPlugin}
     * uses this method to register {@link AsyncDrawableSpan} and start
     * asynchronously loading images.
     * <p>
     * Unlike {@link #beforeSetText(TextView, Spanned)} this method does not receive parsed markdown
     * as at this point spans must be queried by calling <code>TextView#getText#getSpans</code>.
     *
     * @param textView TextView to which markdown was applied
     */
    void afterSetText(@NonNull TextView textView);

    /**
     * Called at the start of every {@link Markwon#appendMarkdown(MarkwonAppendState, String)}
     * invocation, BEFORE the new chunk is settled. Plugins with stateful context that would
     * otherwise leak across chunk boundaries should use this hook to reset it.
     *
     * <p>Example: {@code HtmlPlugin} restores its parser's {@code previousIsBlock} and
     * {@code isInsidePreTag} flags to the values they had at the end of the SETTLED content
     * (recorded by the previous call's {@link #afterSettle}), not the stale values left by
     * the previous chunk's tail renderInto. Without this reset the new chunk's first block
     * can pick up a phantom {@code \n} from {@code ensureNewLineIfPreviousWasBlock} that a
     * full-document render would not produce.
     *
     * <p>Called even on the first call of a session (when {@code state.settledLength() == 0}),
     * in which case the plugin should clear all stateful context.
     *
     * @param state current streaming state (source/settledEnd/settled have all been updated
     *              with the new chunk's source and any prior rebuild/settle work)
     * @see Markwon#appendMarkdown(MarkwonAppendState, String)
     * @since 4.6.3
     */
    void beforeAppendChunk(@NonNull MarkwonAppendState state);

    /**
     * Called immediately after each settle pass within an {@code appendMarkdown} call,
     * BEFORE the tail is re-rendered. Plugins should snapshot any stateful context that
     * needs to survive across chunk boundaries — typically the same context that
     * {@link #beforeAppendChunk} restores.
     *
     * <p>Example: {@code HtmlPlugin} records its parser's {@code previousIsBlock} flag at
     * this point so the next chunk's {@link #beforeAppendChunk} can put the parser back
     * into the state corresponding to the end of the SETTLED content. Capturing AFTER the
     * settle (and BEFORE the tail) is what makes this work: the tail renderInto runs with
     * the same context-continuous parser, but its end state must NOT be used as the
     * starting state of the next chunk's settle.
     *
     * <p>No-op if no settle happened in this call (tail-only re-render).
     *
     * @param state current streaming state
     * @see Markwon#appendMarkdown(MarkwonAppendState, String)
     * @since 4.6.3
     */
    void afterSettle(@NonNull MarkwonAppendState state);
}
