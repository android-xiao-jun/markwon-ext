package io.noties.markwon;

import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;

import java.util.ArrayList;

/**
 * Mutable state of an <em>incremental</em> (streaming) markdown parsing session.
 * <p>
 * A regular {@link Markwon#toMarkdown(String)} call parses and renders the <em>whole</em> input
 * on each invocation, which makes it expensive for a stream of chunks (SSE, LLM tokens, etc):
 * a document of {@code N} characters delivered in {@code K} chunks costs {@code O(K * N)}.
 * <p>
 * {@link Markwon#appendMarkdown(Spanned, String)} keeps this state object (attached to the
 * returned {@link Spanned} via a dedicated marker span) and only re-parses/re-renders the
 * <em>unstable tail</em> of a document - everything that is already <em>settled</em> is rendered
 * exactly once and then re-used.
 *
 * <h3>What is settled and what is not</h3>
 * Markdown is not a line independent format: a chunk that arrives later can change the meaning
 * of previously received text (a list item can continue a list started earlier, a paragraph can
 * turn into a setext heading, an unterminated code fence swallows everything after it, etc).
 * So the source is split in two regions:
 * <ul>
 * <li>{@code [0, settledEnd)} - <em>settled</em>: already rendered, cached and never touched
 * again;</li>
 * <li>{@code [settledEnd, length)} - <em>unstable tail</em>: re-parsed and re-rendered on
 * every append.</li>
 * </ul>
 * {@code settledEnd} always points at a position at which a top level block starts and at which
 * no <em>container</em> (list, block quote, table, code fence, html block, link reference
 * definition) is open, which is what makes parsing of the tail in isolation give exactly the
 * same result as parsing the whole document.
 * <p>
 * The tail is deliberately small (normally - the last block of a document), but it can grow:
 * an unterminated fenced code block, a long list or a single huge paragraph are all kept in the
 * tail until they are finished. In such (deliberate) cases the algorithm degrades to
 * {@code O(N)} per append for that particular block, but never produces a wrong result.
 *
 * @see Markwon#appendMarkdown(Spanned, String)
 * @see Markwon#appendMarkdown(MarkwonAppendState, String)
 * @since 4.6.2
 */
public final class MarkwonAppendState {

    private final StringBuilder source = new StringBuilder();

    /**
     * Raw markdown of all link reference definitions that are already settled. They are
     * document-scoped, so they must be supplied to a tail that is parsed in isolation.
     */
    private final StringBuilder definitions = new StringBuilder();

    /**
     * Ascending list of source positions at which a new top level block <em>region</em> starts.
     * Positions that are less than {@link #settledEnd} are pruned (they can never be used again).
     */
    private final ArrayList<Integer> blockStarts = new ArrayList<>(8);

    // mutable: new settled content is appended in place (no defensive copy on each settle)
    private SpannableBuilder settled;

    private int settledEnd;

    // incremental line scanning state
    private int scannedTo;
    private int lineStart;

    // "swallows everything until it is closed" block state (code fence, $$ block, type-1 html)
    private boolean inFence;
    private char fenceChar;
    private int fenceLength;
    private String fenceTag;
    private int fenceOpenLineStart = -1;

    // "swallows everything until the next blank line" block state (type-6 / type-7 html
    // blocks: <tag>...</tag>, <tag/>), tracked alongside {@link #inFence}. A commonmark
    // html-block opener (<tag attr="..."/> or <tag> on its own line) cannot be split across
    // the settle/tail boundary — the parser would receive a half-finished tag in one
    // fragment and the rest in another, producing a different AST than the full parse
    // (most visibly: a mid-tag `<img` becomes plain paragraph text instead of a self-closing
    // inline image, and the next line is then re-parsed as a fresh heading / paragraph).
    private boolean inHtmlBlock;
    private int htmlBlockOpenLineStart = -1;

    /**
     * Set when a settled region contains a reference-like token (`[label]`) that was not resolved
     * into a link: a link reference definition that comes later in a document can still resolve
     * it, so all cached rendering must be discarded in such a case.
     */
    private boolean hasUnresolvedReferences;
    private boolean rebuildRequired;

    /**
     * Start of the earliest settled region that contains a reference that is not resolved into
     * a link. Everything starting with this position must stay in the unstable tail (a link
     * reference definition received later can still resolve it), {@code -1} if there is none.
     */
    private int firstUnresolvedRegion = -1;

    public MarkwonAppendState() {
        blockStarts.add(0);
    }

    /**
     * Discards all accumulated state (source, rendered content, block positions).
     */
    public void reset() {
        source.setLength(0);
        definitions.setLength(0);
        blockStarts.clear();
        blockStarts.add(0);
        settled = null;
        settledEnd = 0;
        scannedTo = 0;
        lineStart = 0;
        inFence = false;
        fenceChar = 0;
        fenceLength = 0;
        fenceTag = null;
        fenceOpenLineStart = -1;
        inHtmlBlock = false;
        htmlBlockOpenLineStart = -1;
        hasUnresolvedReferences = false;
        rebuildRequired = false;
        firstUnresolvedRegion = -1;
    }

    /**
     * @return {@code true} if a link reference definition that can resolve an already settled
     * (and thus already rendered) reference was received
     */
    boolean consumeRebuild() {
        if (rebuildRequired) {
            rebuildRequired = false;
            return true;
        }
        return false;
    }

    /**
     * Discards all cached rendering (but keeps accumulated source) and re-scans it from scratch.
     */
    void rebuild() {
        blockStarts.clear();
        blockStarts.add(0);
        settled = null;
        settledEnd = 0;
        definitions.setLength(0);
        scannedTo = 0;
        lineStart = 0;
        inFence = false;
        fenceChar = 0;
        fenceLength = 0;
        fenceTag = null;
        fenceOpenLineStart = -1;
        inHtmlBlock = false;
        htmlBlockOpenLineStart = -1;
        hasUnresolvedReferences = false;
        rebuildRequired = false;
        // NB, firstUnresolvedRegion is intentionally kept
        scan();
    }

    /**
     * @return full raw markdown that was passed to this state via
     * {@link Markwon#appendMarkdown(MarkwonAppendState, String)}
     */
    @NonNull
    public String source() {
        return source.toString();
    }

    /**
     * @return length of {@link #source()}
     */
    public int sourceLength() {
        return source.length();
    }

    /**
     * @return number of leading source characters that are rendered, cached and will never be
     * parsed again
     */
    public int settledLength() {
        return settledEnd;
    }

    // ---------------------------------------------------------------------------
    // package private API (used by MarkwonImpl)
    // ---------------------------------------------------------------------------

    @NonNull
    CharSequence sourceRange(int start, int end) {
        return source.subSequence(start, end);
    }

    /**
     * @return raw markdown of all settled link reference definitions (empty string if there
     * are none). Must be prepended to a tail before parsing it.
     */
    @NonNull
    String definitions() {
        return definitions.toString();
    }

    /**
     * Inspects a region that is about to become settled:
     * <ul>
     * <li>collects all link reference definitions (they are document-scoped, so a tail that is
     * parsed in isolation must still see them);</li>
     * <li>detects references that are not resolved into a link - a link reference definition
     * received later can still resolve them.</li>
     * </ul>
     *
     * @param node   parsed region
     * @param region raw markdown of a region
     */
    void inspectSettledRegion(@NonNull Node node, @NonNull CharSequence region, final int start) {
        collectDefinitions(node, definitions);
        if (!containsLink(node) && hasReferenceLikeToken(region, 0, region.length())) {
            if (firstUnresolvedRegion < 0 || start < firstUnresolvedRegion) {
                firstUnresolvedRegion = start;
            }
            hasUnresolvedReferences = true;
        }
    }

    private static boolean containsLink(@NonNull Node node) {
        if (node instanceof Link) {
            return true;
        }
        Node child = node.getFirstChild();
        while (child != null) {
            if (containsLink(child)) {
                return true;
            }
            child = child.getNext();
        }
        return false;
    }

    private static void collectDefinitions(@NonNull Node node, @NonNull StringBuilder out) {
        if (node instanceof LinkReferenceDefinition) {
            final LinkReferenceDefinition definition = (LinkReferenceDefinition) node;
            final String label = definition.getLabel();
            final String destination = definition.getDestination();
            if (isUsableLabel(label) && destination != null && destination.length() > 0) {
                out.append('[').append(label).append("]: ");
                if (containsWhitespace(destination)) {
                    out.append('<').append(destination).append('>');
                } else {
                    out.append(destination);
                }
                final String title = definition.getTitle();
                if (title != null && title.length() > 0) {
                    out.append(" \"").append(title.replace("\"", "\\\"")).append('"');
                }
                out.append('\n');
            }
            return;
        }
        Node child = node.getFirstChild();
        while (child != null) {
            collectDefinitions(child, out);
            child = child.getNext();
        }
    }

    private static boolean isUsableLabel(@Nullable String label) {
        if (label == null || label.length() == 0) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            final char c = label.charAt(i);
            if (c == '[' || c == ']' || c == '\n') {
                return false;
            }
        }
        return true;
    }

    private static boolean containsWhitespace(@NonNull String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n') {
                return true;
            }
        }
        return false;
    }

    void appendSource(@NonNull String markdown) {
        source.append(markdown);
    }

    @Nullable
    SpannableBuilder settled() {
        return settled;
    }

    /**
     * @return a fresh {@link SpannableBuilder} pre-populated with the same content and spans
     * as {@link #settled()}, or an empty builder if there is no settled content. Used by
     * {@code MarkwonImpl} to render the unstable tail into a builder with the same context
     * a full-document render would have, so context-dependent plugins (notably
     * {@code HtmlPlugin}) keep emitting byte-identical output.
     */
    @NonNull
    SpannableBuilder settledSeed() {
        return settled != null
                ? new SpannableBuilder(settled)
                : new SpannableBuilder();
    }

    int settledEnd() {
        return settledEnd;
    }

    /**
     * @return source position of the trailing incomplete line (the line that the scanner is
     * still receiving). The region {@code [0, incompleteLineStart())} is fully formed and can
     * be settled; the rest stays in the unstable tail. Used by
     * {@code MarkwonImpl#appendMarkdown} to decide where a full re-render ends when a
     * link-reference rebuild happens.
     */
    int incompleteLineStart() {
        return lineStart < source.length() ? lineStart : source.length();
    }

    /**
     * Used when a {@link Spanned} without an attached state is supplied: its content is treated
     * as already rendered (settled), raw source of it is unknown, so incremental parsing starts
     * from this point on.
     */
    void preset(@Nullable Spanned alreadyRendered) {
        settled = alreadyRendered != null
                ? new SpannableBuilder(alreadyRendered)
                : null;
        settledEnd = 0;
    }

    /**
     * Scans all the source that was added since the previous call (line by line) and collects
     * block start positions.
     */
    void scan() {
        final int end = source.length();
        int i = scannedTo;
        while (i < end) {
            if (source.charAt(i) == '\n') {
                processLine(lineStart, i);
                lineStart = i + 1;
            }
            i++;
        }
        scannedTo = end;

        // the very last line is not complete yet, but a link reference definition can already
        // be recognized on it (we must not wait for a new line in order to stay correct)
        if (hasUnresolvedReferences && lineStart < end && isLinkDefinition(lineStart, end)) {
            rebuildRequired = true;
        }
    }

    /**
     * @return source position at which the unstable tail starts, always {@code >= settledEnd}
     */
    int resolveTailStart() {

        final int end = source.length();
        final int floor = firstUnresolvedRegion >= 0
                ? Math.max(settledEnd, firstUnresolvedRegion)
                : settledEnd;

        if (inFence) {
            // everything starting with the opening fence line must stay in the tail, a fence
            // cannot be a continuation of anything -> no need to look back
            if (fenceOpenLineStart >= settledEnd && fenceOpenLineStart < end) {
                return Math.min(fenceOpenLineStart, floor);
            }
            return floor;
        }

        if (inHtmlBlock) {
            // an unterminated html-block (type-2..7) must keep its opener line together with
            // everything received after it - a chunk boundary that lands inside such a block
            // would otherwise re-parse the trailing content as paragraph / heading text,
            // diverging from the full-document parse
            if (htmlBlockOpenLineStart >= settledEnd && htmlBlockOpenLineStart < end) {
                return Math.min(htmlBlockOpenLineStart, floor);
            }
            return floor;
        }

        if (blockStarts.isEmpty()) {
            return floor;
        }

        // blockStarts are sorted ascending and pruned, so index 0 is >= settledEnd
        int index = blockStarts.size() - 1;

        while (index > 0) {

            final int start = blockStarts.get(index);

            // the very last region is still being received: it is empty right now, so we cannot
            // tell if it will continue a previous container (and content that will be added to it
            // can still change its meaning)
            if (start >= end) {
                index -= 1;
                continue;
            }

            // a block that follows a blank line can still _continue_ a container that was opened
            // by a previous block (list item, block quote, table row, indented code, etc).
            // In such a case both regions must be parsed together (otherwise parsing of the tail
            // in isolation gives a different result, for example a wrong ordered list number)
            if (isPotentialContinuation(source, start)
                    && isPotentialContinuation(source, lastContentLineStart(start))) {
                index -= 1;
                continue;
            }

            break;
        }

        final int tailStart = blockStarts.get(index);

        // a region that holds an unresolved reference must never be settled
        // (we cannot roll back further than `floor`); otherwise settle at the safe boundary
        return tailStart < floor
                ? floor
                : tailStart;
    }

    /**
     * @return position at which the last non-blank line before {@code end} (exclusive) starts
     */
    private int lastContentLineStart(final int end) {
        int i = end;
        while (i > 0) {
            final char c = source.charAt(i - 1);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i -= 1;
            } else {
                break;
            }
        }
        while (i > 0 && source.charAt(i - 1) != '\n') {
            i -= 1;
        }
        return i;
    }

    /**
     * Marks {@code [oldSettledEnd, newSettledEnd)} as final and replaces the cached settled
     * content with {@code newSettled}. The caller is responsible for rendering the delta into the
     * supplied builder (seeded from the old settled content) so that context-dependent plugins
     * such as {@code HtmlPlugin} see the same builder state a full-document render would have.
     *
     * @param tailStart   new {@link #settledEnd} value (must be {@code >= oldSettledEnd})
     * @param newSettled fully rendered content covering {@code [0, newSettledEnd)} of the
     *                    accumulated source. Any trailing-newline simulation that a standalone
     *                    delta parse would otherwise miss (because the delta's last block has no
     *                    next sibling) must already be appended to this builder by the caller.
     */
    void settle(int tailStart, @NonNull SpannableBuilder newSettled) {

        if (newSettled != null && newSettled.length() > 0) {
            this.settled = newSettled;
        }
        this.settledEnd = tailStart;

        // positions before settledEnd can never be used again
        for (int i = blockStarts.size() - 1; i >= 0; i--) {
            if (blockStarts.get(i) < settledEnd) {
                blockStarts.remove(i);
            }
        }
        if (blockStarts.isEmpty()) {
            blockStarts.add(settledEnd);
        }
    }

    /**
     * Discards the cached settled content and replaces it with a full re-render of the entire
     * accumulated source up to {@code newSettledEnd}. Used when a previously settled reference
     * can be retroactively resolved by a definition that landed in the unstable tail — once
     * that happens every prior rendered segment is stale and must be regenerated. The state
     * remains usable for further {@link Markwon#appendMarkdown(MarkwonAppendState, String)}
     * calls, which will resume the incremental path.
     *
     * @param wholeParsed   the AST produced by a full parse of the accumulated source up to
     *                      {@code newSettledEnd}
     * @param wholeRendered fully rendered {@link SpannableBuilder} of the same source range
     * @param newSettledEnd size of the region that has been re-rendered and is now cached as
     *                      settled (the trailing incomplete line, if any, stays in the tail)
     */
    void forceSettleAll(
            @NonNull Node wholeParsed,
            @NonNull SpannableBuilder wholeRendered,
            final int newSettledEnd) {
        this.settled = wholeRendered.length() > 0 ? wholeRendered : null;
        this.settledEnd = newSettledEnd;
        // re-collect every known definition so any subsequent delta / tail parse can resolve
        // references against them
        this.definitions.setLength(0);
        collectDefinitions(wholeParsed, this.definitions);
        // the rebuild reason (previously unresolved ref now resolved) is satisfied
        this.hasUnresolvedReferences = false;
        this.rebuildRequired = false;
        this.firstUnresolvedRegion = -1;
    }

    /**
     * @return {@code true} if {@code source} has a non-blank line at or after {@code start}.
     * Used by the incremental rendering to detect whether the just settled block's
     * {@code blockEnd#forceNewLine} (suppressed in standalone parse) would have fired in
     * a full-document parse, so a matching {@code "\n"} can be appended to {@link #settled}.
     */
    static boolean hasFollowingBlock(@NonNull CharSequence source, final int start) {
        final int length = source.length();
        int i = start;
        while (i < length) {
            int lineEnd = i;
            while (lineEnd < length && source.charAt(lineEnd) != '\n') {
                lineEnd += 1;
            }
            if (lineEnd > i) {
                return true; // found a non-blank line
            }
            if (lineEnd >= length) {
                return false;
            }
            i = lineEnd + 1; // skip the "\n", continue scanning
        }
        return false;
    }

    // ---------------------------------------------------------------------------
    // line scanning
    // ---------------------------------------------------------------------------

    private void processLine(final int start, final int newLine) {

        int end = newLine;
        if (end > start && source.charAt(end - 1) == '\r') {
            end -= 1;
        }

        if (inFence) {
            if (isFenceClose(start, end)) {
                inFence = false;
                fenceOpenLineStart = -1;
                fenceTag = null;
            }
            return;
        }

        // NB, fenceMarker additionally initializes fenceLength & fenceTag
        final int marker = fenceMarker(start, end);
        if (marker != 0) {
            inFence = true;
            fenceChar = (char) marker;
            fenceOpenLineStart = start;
            return;
        }

        // type-6 / type-7 commonmark html-blocks are terminated by a blank line, so an opener
        // line that lands at a chunk boundary must stay together with everything after it
        // until that blank line arrives. Otherwise the next chunk would re-parse the tag
        // (or treat content inside as plain paragraph / heading)
        if (inHtmlBlock) {
            if (isBlank(start, end)) {
                inHtmlBlock = false;
                htmlBlockOpenLineStart = -1;
                blockStarts.add(newLine + 1);
            }
            return;
        }
        if (isHtmlBlockOpener(start, end)) {
            inHtmlBlock = true;
            htmlBlockOpenLineStart = start;
            return;
        }

        if (isBlank(start, end)) {
            blockStarts.add(newLine + 1);
            return;
        }

        // a link reference definition that is received _after_ a reference was already
        // rendered can change previously produced output
        if (hasUnresolvedReferences && isLinkDefinition(start, end)) {
            rebuildRequired = true;
        }
    }

    private boolean isLinkDefinition(final int start, final int end) {
        int i = start;
        while (i < end && (i - start) < 3 && source.charAt(i) == ' ') {
            i += 1;
        }
        if (i >= end || source.charAt(i) != '[') {
            return false;
        }
        return indexOf(source, i, end, "]:") > i;
    }

    /**
     * @return fence character (or {@code 0} if a line does not open a fence), initializes
     * {@link #fenceLength} and {@link #fenceTag}
     */
    private int fenceMarker(final int start, final int end) {

        int i = start;
        while (i < end && (i - start) < 3 && source.charAt(i) == ' ') {
            i += 1;
        }
        if (i >= end) {
            return 0;
        }

        final char c = source.charAt(i);

        if (c == '`' || c == '~') {
            final int from = i;
            while (i < end && source.charAt(i) == c) {
                i += 1;
            }
            final int length = i - from;
            if (length >= 3) {
                fenceLength = length;
                return c;
            }
            return 0;
        }

        // `$$` blocks (markwon-ext-latex): must start a line and must not be balanced
        if (c == '$'
                && (i + 1) < end
                && source.charAt(i + 1) == '$'
                && (count(source, start, end, "$$") & 1) == 1) {
            fenceLength = 2;
            return '$';
        }

        // type-1 html blocks (`<pre>`, `<script>`, `<style>`, `<textarea>`) are the only html
        // blocks that are _not_ interrupted by a blank line
        if (c == '<') {
            final String tag = htmlType1Tag(i, end);
            if (tag != null) {
                fenceLength = 0;
                fenceTag = tag;
                return 'h';
            }
        }

        return 0;
    }

    private boolean isFenceClose(final int start, final int end) {
        if (fenceChar == '$') {
            return indexOf(source, start, end, "$$") >= 0;
        }
        if (fenceChar == 'h') {
            return indexOf(source, start, end, "</" + fenceTag) >= 0;
        }

        int i = start;
        while (i < end && (i - start) < 3 && source.charAt(i) == ' ') {
            i += 1;
        }

        int length = 0;
        while (i < end && source.charAt(i) == fenceChar) {
            i += 1;
            length += 1;
        }
        if (length < fenceLength) {
            return false;
        }

        // nothing but whitespace is allowed after a closing fence
        while (i < end) {
            final char c = source.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
            i += 1;
        }

        return true;
    }

    @Nullable
    private String htmlType1Tag(final int start, final int end) {
        final int from = start + 1; // skip `<`
        int i = from;
        while (i < end) {
            final char c = source.charAt(i);
            if (c == '>' || c == ' ' || c == '\t' || c == '\r') {
                break;
            }
            i += 1;
        }
        final String tag = source.subSequence(from, i).toString().toLowerCase();
        if ("script".equals(tag) || "pre".equals(tag) || "style".equals(tag) || "textarea".equals(tag)) {
            return tag;
        }
        return null;
    }

    /**
     * @return {@code true} if {@code [start, end)} is a line that opens a blank-line-terminated
     * commonmark html-block (types 2..7). Type-1 ({@code <pre>}/{@code <script>}/{@code <style>}/
     * {@code <textarea>}) is handled by {@link #fenceMarker} instead and never reaches this
     * method.
     */
    private boolean isHtmlBlockOpener(final int start, final int end) {
        int i = start;
        while (i < end && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) {
            i += 1;
        }
        if (i >= end || source.charAt(i) != '<') {
            return false;
        }
        final int nameStart = i + 1;
        if (nameStart < end && source.charAt(nameStart) == '/') {
            i = nameStart + 1;
        } else {
            i = nameStart;
        }
        if (i >= end) {
            return false;
        }
        final char fc = source.charAt(i);
        // type-2 (`<!--`), type-3 (`<?`), type-4 (`<![A-Z]`), type-5 (`<![CDATA[`) all close
        // themselves on the same line, so anything starting with `<!` or `<?` qualifies
        if (fc == '!' || fc == '?') {
            return isHtmlBlockOpenerRest(start, i + 1, end);
        }
        // type-6 and type-7 require an HTML tag name to start with an ASCII letter
        if (!isAsciiLetter(fc)) {
            return false;
        }
        int nameEnd = i + 1;
        while (nameEnd < end) {
            final char c = source.charAt(nameEnd);
            if (c == ' ' || c == '\t' || c == '>' || c == '/' || c == '-' || c == '.') {
                break;
            }
            nameEnd += 1;
        }
        if (nameEnd == i + 1) {
            return false;
        }
        return isHtmlBlockOpenerRest(start, nameEnd, end);
    }

    /**
     * Scans {@code [from, end)} assuming the line already started with an opener. The remainder
     * must consist of zero or more attribute chunks (alphanumerics, whitespace, dashes, dots,
     * slashes, {@code =}, quoted strings) terminated by {@code >} or {@code />}, followed by
     * whitespace-only content to the end of the line.
     */
    private boolean isHtmlBlockOpenerRest(final int lineStart, final int from, final int end) {
        int j = from;
        boolean closed = false;
        while (j < end) {
            final char c = source.charAt(j);
            if (c == ' ' || c == '\t') {
                j += 1;
                continue;
            }
            if (c == '/' && (j + 1) < end && source.charAt(j + 1) == '>') {
                j += 2;
                closed = true;
                break;
            }
            if (c == '>') {
                j += 1;
                closed = true;
                break;
            }
            if (c == '"' || c == '\'') {
                final char quote = c;
                j += 1;
                while (j < end && source.charAt(j) != quote) {
                    j += 1;
                }
                if (j < end) {
                    j += 1;
                }
                continue;
            }
            j += 1;
        }
        if (!closed) {
            return false;
        }
        while (j < end) {
            final char c = source.charAt(j);
            if (c != ' ' && c != '\t') {
                return false;
            }
            j += 1;
        }
        return true;
    }

    private static boolean isAsciiLetter(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isBlank(final int start, final int end) {
        for (int i = start; i < end; i++) {
            final char c = source.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------
    // static helpers
    // ---------------------------------------------------------------------------

    /**
     * @return {@code true} if a block that starts at supplied position <em>can</em> be a
     * continuation of a container that was started earlier (thus it cannot be used as a
     * start of the unstable tail)
     */
    static boolean isPotentialContinuation(@NonNull CharSequence source, final int position) {

        final int length = source.length();

        int i = position;
        int indent = 0;
        while (i < length) {
            final char c = source.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            i += 1;
            indent += 1;
        }

        // indented content: can be an indented code block, but also a continuation of a list item
        if (indent > 0) {
            return true;
        }

        if (i >= length) {
            return false;
        }

        switch (source.charAt(i)) {
            case '-':
            case '+':
            case '*':   // bullet list, thematic break (`***`) or an emphasised paragraph
            case '>':   // block quote
            case '|':   // table row (markwon-ext-tables)
            case '[':   // link reference definition / footnote definition
            case ':':   // definition list
                return true;
            default:
                // ordered list (`1.`, `2)`)
                return source.charAt(i) >= '0' && source.charAt(i) <= '9';
        }
    }

    /**
     * @return {@code true} if a supplied region contains a {@code [label]} token that is not
     * an inline link and not a full reference (such a token can be resolved by a link reference
     * definition that is received later)
     */
    static boolean hasReferenceLikeToken(@NonNull CharSequence source, final int start, final int end) {
        int i = start;
        while (i < end) {
            if (source.charAt(i) != '[') {
                i += 1;
                continue;
            }
            final int close = indexOf(source, i + 1, end, "]");
            if (close < 0) {
                return false;
            }
            final char next = (close + 1) < end
                    ? source.charAt(close + 1)
                    : 0;
            if (next == '(' || next == '[') {
                // `[text](url)` and `[text][label]`
                i = close + 1;
                continue;
            }
            return true;
        }
        return false;
    }

    private static int count(
            @NonNull CharSequence source,
            final int start,
            final int end,
            @NonNull String what) {
        int count = 0;
        int i = start;
        while (i < end) {
            final int index = indexOf(source, i, end, what);
            if (index < 0) {
                break;
            }
            count += 1;
            i = index + what.length();
        }
        return count;
    }

    private static int indexOf(
            @NonNull CharSequence source,
            final int start,
            final int end,
            @NonNull String what) {
        final int max = end - what.length();
        for (int i = start; i <= max; i++) {
            boolean matches = true;
            for (int j = 0; j < what.length(); j++) {
                if (source.charAt(i + j) != what.charAt(j)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }
        return -1;
    }
}
