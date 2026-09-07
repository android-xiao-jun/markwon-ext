package io.noties.markwon.html;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * @since 2.0.0
 */
public abstract class MarkwonHtmlParser {

    public interface FlushAction<T> {
        void apply(@NonNull List<T> tags);
    }

    public abstract <T extends Appendable & CharSequence> void processFragment(
            @NonNull T output,
            @NonNull String htmlFragment);

    /**
     * After this method exists a {@link MarkwonHtmlParser} will clear internal state for stored tags.
     * If you wish to process them further after this method exists create own copy of supplied
     * collection.
     *
     * @param documentLength known document length. This value is used to close all non-closed tags.
     *                       If you wish to keep them open (do not force close at the end of a
     *                       document pass here {@link HtmlTag#NO_END}. Later non-closed tags
     *                       can be detected by calling {@link HtmlTag#isClosed()}
     * @param action         {@link FlushAction} to be called with resulting tags ({@link HtmlTag.Inline})
     */
    public abstract void flushInlineTags(
            int documentLength,
            @NonNull FlushAction<HtmlTag.Inline> action);

    /**
     * After this method exists a {@link MarkwonHtmlParser} will clear internal state for stored tags.
     * If you wish to process them further after this method exists create own copy of supplied
     * collection.
     *
     * @param documentLength known document length. This value is used to close all non-closed tags.
     *                       If you wish to keep them open (do not force close at the end of a
     *                       document pass here {@link HtmlTag#NO_END}. Later non-closed tags
     *                       can be detected by calling {@link HtmlTag#isClosed()}
     * @param action         {@link FlushAction} to be called with resulting tags ({@link HtmlTag.Block})
     */
    public abstract void flushBlockTags(
            int documentLength,
            @NonNull FlushAction<HtmlTag.Block> action);

    public abstract void reset();

    /**
     * Reset context-only state that should not survive across {@code appendMarkdown} chunk
     * boundaries (specifically {@code previousIsBlock} and {@code isInsidePreTag}). The
     * per-render state (open-block stack, inline-tag list) is intentionally NOT touched here
     * — those are cleared by {@link #reset()} at the end of each {@code renderInto} call.
     *
     * <p>Streaming-mode {@code MarkwonImpl.appendMarkdown} invokes this method at the start
     * of each call, BEFORE settling the new chunk, so the settle pass starts with no stale
     * context flags from the previous chunk's tail. A no-op default implementation is
     * provided so legacy subclasses that do not know about this method continue to work
     * (their behavior matches the pre-streaming-correctness version).
     *
     * @since 4.6.3
     */
    public void resetForNextChunk() {
        // default no-op for back-compat with custom subclasses that pre-date streaming fixes
    }

    /**
     * Variant of {@link #resetForNextChunk()} that lets the caller pass the value to set for
     * the parser's {@code previousIsBlock} flag at the start of the next chunk. This is the
     * context-continuity bridge: the flag should reflect the state at the END of the SETTLED
     * content (recorded by the previous chunk's {@code afterSettle}), not the stale value
     * left by the previous chunk's tail renderInto.
     *
     * <p>Default implementation delegates to {@link #resetForNextChunk()} and is overridden
     * by subclasses that actually track {@code previousIsBlock}.
     *
     * @param previousIsBlock value the parser should set for its {@code previousIsBlock}
     *                         flag at the start of the new chunk
     * @since 4.6.3
     */
    public void resetForNextChunk(boolean previousIsBlock) {
        // default delegates to no-arg variant; subclasses that track previousIsBlock override
        resetForNextChunk();
    }

    /**
     * @return the parser's {@code previousIsBlock} flag at the moment of the call. Used by
     * {@code HtmlPlugin}'s {@code afterSettle} hook to snapshot the state at the end of the
     * settle pass so the next chunk's {@link #resetForNextChunk(boolean)} can restore it.
     * {@code false} by default.
     *
     * @since 4.6.3
     */
    public boolean previousIsBlock() {
        return false;
    }

}
