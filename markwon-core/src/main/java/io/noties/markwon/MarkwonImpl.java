package io.noties.markwon;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commonmark.node.Block;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.image.AsyncDrawableLoader;

/**
 * @since 3.0.0
 */
class MarkwonImpl extends Markwon {

    private final TextView.BufferType bufferType;
    private final Parser parser;
    private final MarkwonVisitorFactory visitorFactory; // @since 4.1.1
    private final MarkwonConfiguration configuration;
    private final List<MarkwonPlugin> plugins;

    // @since 4.1.0
    @Nullable
    private final TextSetter textSetter;

    // @since 4.4.0
    private final boolean fallbackToRawInputWhenEmpty;

    MarkwonImpl(
            @NonNull TextView.BufferType bufferType,
            @Nullable TextSetter textSetter,
            @NonNull Parser parser,
            @NonNull MarkwonVisitorFactory visitorFactory,
            @NonNull MarkwonConfiguration configuration,
            @NonNull List<MarkwonPlugin> plugins,
            boolean fallbackToRawInputWhenEmpty
    ) {
        this.bufferType = bufferType;
        this.textSetter = textSetter;
        this.parser = parser;
        this.visitorFactory = visitorFactory;
        this.configuration = configuration;
        this.plugins = plugins;
        this.fallbackToRawInputWhenEmpty = fallbackToRawInputWhenEmpty;
    }

    @NonNull
    @Override
    public Node parse(@NonNull String input) {

        // make sure that all plugins are called `processMarkdown` before parsing
        for (MarkwonPlugin plugin : plugins) {
            input = plugin.processMarkdown(input);
        }

        return parser.parse(input);
    }

    @NonNull
    @Override
    public Spanned render(@NonNull Node node) {

        for (MarkwonPlugin plugin : plugins) {
            plugin.beforeRender(node);
        }

        // @since 4.1.1 obtain visitor via factory
        final MarkwonVisitor visitor = visitorFactory.create();

        node.accept(visitor);

        for (MarkwonPlugin plugin : plugins) {
            plugin.afterRender(node, visitor);
        }

        //noinspection UnnecessaryLocalVariable
        final Spanned spanned = visitor.builder().spannableStringBuilder();

        // clear render props and builder after rendering
        // @since 4.1.1 as we no longer reuse visitor - there is no need to clean it
        //  we might still do it if we introduce a thread-local storage though
//        visitor.clear();

        return spanned;
    }

    @NonNull
    @Override
    public Spanned toMarkdown(@NonNull String input) {
        final Spanned spanned = render(parse(input));

        // @since 4.4.0
        // if spanned is empty, we are configured to use raw input and input is not empty
        if (TextUtils.isEmpty(spanned)
                && fallbackToRawInputWhenEmpty
                && !TextUtils.isEmpty(input)) {
            // let's use SpannableStringBuilder in order to keep backward-compatibility
            return new SpannableStringBuilder(input);
        }

        return spanned;
    }

    @NonNull
    @Override
    public Spanned appendMarkdown(@NonNull Spanned old, @NonNull String newString) {

        MarkwonAppendState state = AppendStateSpan.find(old);

        if (state == null) {
            // supplied Spanned was not created by `appendMarkdown` -> markdown source of it is
            // unknown, so we can only treat it as an already rendered document
            state = new MarkwonAppendState();
            state.preset(old);
        }

        return appendMarkdown(state, newString);
    }

    @NonNull
    @Override
    public Spanned appendMarkdown(@NonNull MarkwonAppendState state, @NonNull String newString) {

        state.appendSource(newString);
        state.scan();

        // Tell any stateful plugins (notably HtmlPlugin) to restore context flags that should
        // survive across `renderInto` boundaries within a single `appendMarkdown` call, but
        // should NOT survive across `appendMarkdown` calls themselves. Each plugin snapshots
        // its parser state at the END of the SETTLED pass (see afterSettle below) and uses
        // it here to put the parser back into the corresponding state. Without this, the
        // second and later streaming iterations would carry over `previousIsBlock` /
        // `isInsidePreTag` from the previous tail's last-processed block, which breaks
        // byte-equality with a full-document render (e.g. `<p align="left">...</p>\n\n<p
        // align="center">` would pick up a phantom `\n` after the open tag in the second
        // `<p>` block's character pass). Per-render state (open block stack, inline-tag
        // list) is cleared inside `renderInto` and stays continuous across settle+tail
        // within this call.
        for (MarkwonPlugin plugin : plugins) {
            plugin.beforeAppendChunk(state);
        }

        // a link reference definition can be received _after_ a reference was already rendered — the
        // previously cached settled Spanned is now stale and must be discarded. The only way to
        // produce correct output in this case is to render the entire accumulated source from
        // scratch (link references are document-scoped so a later def retroactively affects
        // earlier refs). This happens at most once per stream session in practice.
        if (state.consumeRebuild()) {
            state.rebuild();
            // re-parse and re-render the entire accumulated source from scratch
            final int sourceLength = state.sourceLength();
            final String wholeSource = state.sourceRange(0, sourceLength).toString();
            final Node whole = parse(wholeSource);
            final SpannableBuilder wholeBuilder = new SpannableBuilder();
            renderInto(whole, wholeBuilder);
            // leave the trailing incomplete line in the tail — a future chunk can extend it
            final int incompleteStart = state.incompleteLineStart();
            // truncate `wholeBuilder` to `[0, incompleteStart)` and use it as the new settled
            final SpannableBuilder settledBuilder;
            if (incompleteStart > 0 && incompleteStart <= wholeBuilder.length()) {
                settledBuilder = new SpannableBuilder(wholeBuilder.subSequence(0, incompleteStart));
            } else {
                // either nothing has been received yet, or the whole source completed mid-line
                // (rare for SSE, normal for a final chunk)
                settledBuilder = new SpannableBuilder(wholeBuilder);
            }
            state.forceSettleAll(whole, settledBuilder, incompleteStart);
            // early return: output is settled + tail rendered into the same builder
            return buildOutput(state, settledBuilder);
        }

        final int tailStart = state.resolveTailStart();

        // everything before tailStart cannot change anymore -> render it once and cache.
        // We render the delta into a builder seeded with current settled content, so
        // context-dependent plugins (notably HtmlPlugin) keep emitting byte-identical output
        // to a full-document parse.
        if (tailStart > state.settledEnd()) {
            final String delta = state.sourceRange(state.settledEnd(), tailStart).toString();
            final String knownDefinitions = state.definitions();
            final String markdown = knownDefinitions.length() > 0
                    ? knownDefinitions + "\n\n" + delta
                    : delta;
            final Node node = parse(markdown);
            state.inspectSettledRegion(node, markdown, state.settledEnd());

            // Render the delta DIRECTLY into the seeded builder: the first block of the delta
            // calls blockStart → ensureNewLine, which must see the settled content to decide
            // whether a "\n" is needed. Rendering into an empty builder would swallow that
            // newline (ensureNewLine is a no-op on an empty builder) and glue the delta's
            // first block to the last settled character (e.g. `图片名称图片名称修改文字的
            // 显示重心` instead of `图片名称图片名称\n修改文字的显示重心` for case_3).
            final SpannableBuilder settledBuilder = state.settledSeed();
            final int seedLength = settledBuilder.length();
            renderInto(node, settledBuilder);
            // mirror `toMarkdown` fallback, but only for a single (still incomplete) block: an
            // empty result of a multi-block region is a valid one (link reference definitions,
            // html comments, trailing blank lines, etc)
            if (settledBuilder.length() == seedLength
                    && fallbackToRawInputWhenEmpty
                    && markdown.trim().length() > 0
                    && indexOfBlankLine(markdown) < 0) {
                settledBuilder.append(markdown);
            }
            // If the LAST top-level block of the parsed delta is one whose visitor calls
            // `blockEnd`, and the full document continues after tailStart with another block,
            // that block would have had a next sibling in a full parse and its blockEnd would
            // have emitted "\n\n" — a standalone parse suppresses it (getNext() == null on the
            // last block). Mirror the missing newlines on the seeded builder so cached output
            // stays byte-identical to a full parse.
            //
            // The block type MUST be checked: `HtmlBlock` is visited by HtmlPlugin without any
            // blockStart/blockEnd, so simulating a trailing newline for it emits "\n\n" that a
            // full render never produces. That was the on-device `identical=false` around the
            // first `<img>` block of case_3. See #emitsBlockEndOnLastBlock(Node).
            if (emitsBlockEndOnLastBlock(node)
                    && MarkwonAppendState.hasFollowingBlock(state.source(), tailStart)) {
                final int len = settledBuilder.length();
                if (len > 0 && settledBuilder.charAt(len - 1) != '\n') {
                    settledBuilder.append('\n');
                }
                settledBuilder.append('\n');
            }
            state.settle(tailStart, settledBuilder);

            // Tell stateful plugins that the SETTLED pass is finished so they can snapshot
            // any context that should survive across chunk boundaries (e.g. HtmlPlugin's
            // parser.previousIsBlock). The snapshot must be taken HERE — after settle, before
            // tail — so the captured value reflects the end of the SETTLED content, not the
            // end of the tail (which can contain arbitrary additional chars that would leak
            // into the next chunk's settle if used as the starting state).
            for (MarkwonPlugin plugin : plugins) {
                plugin.afterSettle(state);
            }
        }

        // the tail is re-parsed and re-rendered on each append (it is small in a general case).
        // Render DIRECTLY into a builder seeded with current settled content (as the comment
        // always promised): the tail's first block calls blockStart → ensureNewLine, which
        // must see the settled content to know whether a "\n" is needed. Rendering into an
        // empty builder would drop that newline (ensureNewLine is a no-op on an empty
        // builder) — the on-device `identical=false` for case_3's `### 修改文字的显示重心`
        // glued to the preceding `图片名称图片名称` (and `</summary>` glued to `惊不惊喜？`).
        final SpannableBuilder outBuilder = state.settledSeed();
        final Node tailNode = parseTail(state);
        if (tailNode != null) {
            renderTailInto(tailNode, outBuilder);
        }
        return buildOutput(state, outBuilder);
    }

    /**
     * 同 {@link #renderInto(Node, SpannableBuilder)}，但打开
     * {@link AsyncDrawableLoader#setDeferLoading(boolean)}：tail 是未稳定区域，每个 chunk
     * 都会整段重渲染。这里的图片只放占位图，等它所在的 block 落进 settled 区域（下一次
     * {@code appendMarkdown} 的 settled pass，或流结束时的全量渲染）才真正发起加载，
     * 加载完成后 {@code AsyncDrawableScheduler} 会让 TextView 重新 setText 触发重绘与重新测量。
     *
     * @since 4.6.3
     */
    private void renderTailInto(@NonNull Node node, @NonNull SpannableBuilder builder) {
        final AsyncDrawableLoader loader = configuration.asyncDrawableLoader();
        loader.setDeferLoading(true);
        try {
            renderInto(node, builder);
        } finally {
            loader.setDeferLoading(false);
        }
    }

    /**
     * Renders {@code node} into {@code builder} using a visitor whose underlying
     * {@link SpannableBuilder} IS {@code builder}. Plugins that need to inspect the visitor's
     * builder (e.g. {@code HtmlPlugin}'s fragment processor, {@code AsyncDrawableLoader} image
     * spans) therefore see exactly the content the caller prepared — critical for
     * {@code appendMarkdown}'s context-continuity with the already settled prefix.
     */
    private void renderInto(@NonNull Node node, @NonNull SpannableBuilder builder) {
        final MarkwonVisitor visitor = visitorFactory.create(builder);
        for (MarkwonPlugin plugin : plugins) {
            plugin.beforeRender(node);
        }
        node.accept(visitor);
        for (MarkwonPlugin plugin : plugins) {
            plugin.afterRender(node, visitor);
        }
    }

    /**
     * @return parsed AST of the current tail region ({@code [settledEnd, sourceLength)} of
     * {@code state.source()}), with already known link reference definitions prepended so the
     * parser can resolve `[ref]` references whose def is still further up the source stream.
     * Returns {@code null} if the tail is empty.
     */
    @Nullable
    private Node parseTail(@NonNull MarkwonAppendState state) {
        final int start = state.settledEnd();
        final int end = state.sourceLength();
        if (end <= start) {
            return null;
        }
        final String region = state.sourceRange(start, end).toString();
        final String definitions = state.definitions();
        final String markdown = definitions.length() > 0
                ? definitions + "\n\n" + region
                : region;
        return parse(markdown);
    }

    /**
     * Finalizes the returned {@link Spanned}: removes any previous {@link AppendStateSpan} (in
     * case the seeded builder already carried one), applies the current state's marker span,
     * and converts to a {@link SpannableStringBuilder}.
     */
    @NonNull
    private Spanned buildOutput(@NonNull MarkwonAppendState state, @NonNull SpannableBuilder builder) {
        final SpannableStringBuilder out = builder.spannableStringBuilder();
        // a state of a previous session (if any) must not be carried over
        AppendStateSpan.removeAll(out);
        out.setSpan(
                new AppendStateSpan(state),
                0,
                out.length(),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return out;
    }

    /**
     * @return {@code true} if the LAST top-level block of a standalone-parsed delta is one that
     * Markwon's visitors wrap in {@code blockEnd}. Only for those blocks does a full-document
     * parse emit a trailing {@code "\n\n"} that a standalone parse suppresses, so only for those
     * may the trailing-newline simulation fire.
     *
     * <p>Implemented as a block-list (rather than an allow-list) so that block types contributed
     * by optional modules — {@code TableBlock} from {@code markwon-ext-tables}, custom blocks —
     * are covered automatically without {@code markwon-core} depending on them:
     *
     * <ul>
     *   <li>{@code HtmlBlock} — excluded. {@code HtmlPlugin}'s visitor calls only
     *       {@code visitHtml(literal)}; it is wrapped in neither blockStart nor blockEnd
     *       ({@code CorePlugin} only wraps BlockQuote, code blocks, ThematicBreak, Heading and
     *       non-tight-list Paragraph). Simulating a trailing newline here is exactly what
     *       produced the on-device {@code identical=false} around the first {@code <img>} block
     *       of case_3.</li>
     *   <li>{@code ListItem} — excluded. Its visitor ends with {@code ensureNewLine()} only,
     *       which contributes a single {@code \n}, never the forced second one.</li>
     *   <li>{@code Paragraph} inside a tight list — excluded, mirroring
     *       {@code CorePlugin#isInTightList}.</li>
     * </ul>
     *
     * @since 4.6.3
     */
    private static boolean emitsBlockEndOnLastBlock(@NonNull Node node) {
        final Node last = node.getLastChild();
        if (!(last instanceof Block)) {
            return false;
        }
        if (last instanceof HtmlBlock || last instanceof ListItem) {
            return false;
        }
        if (last instanceof Paragraph) {
            return !isInTightList((Paragraph) last);
        }
        return true;
    }

    /**
     * Mirror of {@code CorePlugin#isInTightList}: a {@link Paragraph} whose grandparent is a
     * tight {@link ListBlock} is rendered without blockStart/blockEnd.
     *
     * @since 4.6.3
     */
    private static boolean isInTightList(@NonNull Paragraph paragraph) {
        final Node parent = paragraph.getParent();
        if (parent != null) {
            final Node gramps = parent.getParent();
            if (gramps instanceof ListBlock) {
                return ((ListBlock) gramps).isTight();
            }
        }
        return false;
    }

    private static int indexOfBlankLine(@NonNull String markdown) {
        final int length = markdown.length();
        for (int i = 0; i < length; i++) {
            final char c = markdown.charAt(i);
            if (c != '\n') {
                continue;
            }
            int j = i + 1;
            while (j < length && (markdown.charAt(j) == ' ' || markdown.charAt(j) == '\t')) {
                j += 1;
            }
            if (j >= length || markdown.charAt(j) == '\n' || markdown.charAt(j) == '\r') {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void setMarkdown(@NonNull TextView textView, @NonNull String markdown) {
        setParsedMarkdown(textView, toMarkdown(markdown));
    }

    @Override
    public void setParsedMarkdown(@NonNull final TextView textView, @NonNull Spanned markdown) {

        for (MarkwonPlugin plugin : plugins) {
            plugin.beforeSetText(textView, markdown);
        }

        // @since 4.1.0
        if (textSetter != null) {
            textSetter.setText(textView, markdown, bufferType, new Runnable() {
                @Override
                public void run() {
                    // on-complete we just must call `afterSetText` on all plugins
                    for (MarkwonPlugin plugin : plugins) {
                        plugin.afterSetText(textView);
                    }
                }
            });
        } else {

            // if no text-setter is specified -> just a regular sync operation
            textView.setText(markdown, bufferType);

            for (MarkwonPlugin plugin : plugins) {
                plugin.afterSetText(textView);
            }
        }
    }

    @Override
    public boolean hasPlugin(@NonNull Class<? extends MarkwonPlugin> type) {
        return getPlugin(type) != null;
    }

    @Nullable
    @Override
    public <P extends MarkwonPlugin> P getPlugin(@NonNull Class<P> type) {
        MarkwonPlugin out = null;
        for (MarkwonPlugin plugin : plugins) {
            if (type.isAssignableFrom(plugin.getClass())) {
                out = plugin;
            }
        }
        //noinspection unchecked
        return (P) out;
    }

    @NonNull
    @Override
    public <P extends MarkwonPlugin> P requirePlugin(@NonNull Class<P> type) {
        final P plugin = getPlugin(type);
        if (plugin == null) {
            throw new IllegalStateException(String.format(Locale.US, "Requested plugin `%s` is not " +
                    "registered with this Markwon instance", type.getName()));
        }
        return plugin;
    }

    @NonNull
    @Override
    public List<? extends MarkwonPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    @NonNull
    @Override
    public MarkwonConfiguration configuration() {
        return configuration;
    }
}
