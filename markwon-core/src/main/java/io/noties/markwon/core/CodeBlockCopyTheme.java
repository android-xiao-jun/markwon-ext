package io.noties.markwon.core;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

/**
 * Style of the <b>copy button</b> drawn on the right end of a scrollable code block's header
 * row.
 *
 * <p>It is a theme of its own — rather than a handful of {@code codeBlockCopy*} fields on
 * {@link MarkwonTheme} — for the same reason {@code TableTheme} is: the button is one feature
 * with several knobs, and passing around a single object keeps {@link MarkwonTheme.Builder}
 * from growing half a dozen setters that only make sense together.
 *
 * <p>It is also <b>not</b> configured through {@link MarkwonTheme}: the button belongs to
 * {@code CodeBlockScrollPlugin} and is set on it directly, so its style never travels through
 * the global configuration:
 *
 * <pre>
 * Markwon.builder(context)
 *         .usePlugin(CodeBlockScrollPlugin.create()
 *                 // style: what the button looks like
 *                 .codeBlockCopyTheme(CodeBlockCopyTheme.builder()
 *                         .enabled(true)
 *                         .text("复制")
 *                         .successText("已复制")
 *                         .build())
 *                 // behaviour: the host is the one that writes the clipboard
 *                 .onCodeBlockCopy((textView, code) -&gt; copyToClipboard(textView, code)))
 *         .build();
 * </pre>
 *
 * <h3>What the button does</h3>
 * Nothing but report: a tap hands the block's <b>original</b> source — the fenced/indented
 * literal as the author wrote it, not the highlighted {@code Spanned} — to the host through
 * {@code CodeBlockCopyListener}. <b>The library does not write the clipboard</b>, so attaching
 * a listener is what makes the button useful. The gesture itself is handled by
 * {@code CodeBlockScrollPlugin}; the drawing by {@code CodeBlockSpan}.
 *
 * <h3>Where it lives</h3>
 * The button is hosted by the <b>header row</b>, which only exists for a scrollable code block
 * (i.e. when {@code CodeBlockScrollPlugin} is used).
 * An <em>unconfigured</em> header height is raised to one line of text while the button is
 * enabled, so turning this on is enough to make it visible; an explicit {@code 0} still means
 * "no header row" and disables the button with it.
 *
 * @see io.noties.markwon.core.scroll.CodeBlockScrollPlugin#codeBlockCopyTheme(CodeBlockCopyTheme)
 * @since 4.6.3
 */
public class CodeBlockCopyTheme {

    private final boolean enabled;
    private final String text;
    private final Drawable icon;
    private final int textSize;
    private final int textColor;
    private final String successText;

    private CodeBlockCopyTheme(@NonNull Builder builder) {
        this.enabled = builder.enabled;
        this.text = builder.text;
        this.icon = builder.icon;
        this.textSize = builder.textSize;
        this.textColor = builder.textColor;
        this.successText = builder.successText;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether the button is drawn at all. {@code false} (default) = not configured = the
     * header row carries nothing but the language label.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Label of the button, or {@code null} when it was not configured — the button is then
     * drawn as an icon (see {@link #getIcon()}).
     */
    @Nullable
    public String getText() {
        return text;
    }

    /**
     * Icon of the button, or {@code null} when it was not configured — the button then falls
     * back to {@link #getText()}, and finally to the built-in vector drawn by
     * {@code CodeBlockSpan} (which needs no drawable resource and therefore no
     * {@code appcompat} runtime dependency).
     *
     * <p>The instance is shared by every code block of the document and its bounds are
     * re-applied right before each draw, so one instance is enough for the whole
     * {@code TextView}.
     */
    @Nullable
    public Drawable getIcon() {
        return icon;
    }

    /**
     * Text size of the button label, or a non-positive value when it was not configured — the
     * label then follows the header text style ({@code codeBlockTextSize} → {@code codeTextSize}).
     */
    @Px
    public int getTextSize() {
        return textSize;
    }

    /**
     * Color of the button label, or {@code 0} when it was not configured — the label then
     * follows the header text color ({@code codeBlockTextColor} → {@code codeTextColor}).
     */
    @ColorInt
    public int getTextColor() {
        return textColor;
    }

    /**
     * Label the button switches to for a short while right after a tap, or {@code null} when
     * it was not configured — the button then stays as it is.
     *
     * <p>It is a <b>confirmation of the tap</b>, not of the copy: the library hands the source
     * over and never learns what the host did with it, so it is shown whenever a listener is
     * attached (a button nobody listens to stays silent rather than claiming a copy). Hosts
     * that need to report the actual outcome — or nothing at all — simply leave this
     * unconfigured and speak for themselves in {@code CodeBlockCopyListener}.
     */
    @Nullable
    public String getSuccessText() {
        return successText;
    }

    /**
     * Applies {@link #getTextSize()} / {@link #getTextColor()} on top of the <b>header</b>
     * style the caller has already seeded the paint with. Both knobs are optional and
     * independent: an unconfigured one leaves the paint alone, so the button reads as part of
     * the header by default and only diverges when explicitly asked to.
     */
    public void applyTextStyle(@NonNull Paint paint) {
        if (textSize > 0) {
            paint.setTextSize(textSize);
        }
        if (textColor != 0) {
            paint.setColor(textColor);
        }
    }

    @SuppressWarnings("unused")
    public static class Builder {

        private boolean enabled;
        private String text;
        private Drawable icon;
        private int textSize;
        private int textColor;
        private String successText;

        Builder() {
        }

        /**
         * Shows the button. {@code false} by default — the whole feature is opt-in, like the
         * scrollbar and the header row.
         *
         * @see CodeBlockCopyTheme
         */
        @NonNull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Draws the button as a <b>text</b> label (e.g. {@code "复制"}). {@code null}
         * (default) = not configured = the button is drawn as an icon instead.
         *
         * @see #icon(Drawable)
         */
        @NonNull
        public Builder text(@Nullable String text) {
            this.text = text;
            return this;
        }

        /**
         * Draws the button as an <b>icon</b>. Takes precedence over {@link #text(String)}.
         * {@code null}, or a drawable without an intrinsic size, falls back to the label and
         * finally to the built-in vector.
         */
        @NonNull
        public Builder icon(@Nullable Drawable icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Text size of the label. <b>Not configured by default</b> — the label then follows
         * the header text style.
         */
        @NonNull
        public Builder textSize(@Px int textSize) {
            this.textSize = textSize;
            return this;
        }

        /**
         * Color of the label. {@code 0} (default) = <b>not configured</b> — the label then
         * follows the header text color.
         */
        @NonNull
        public Builder textColor(@ColorInt int textColor) {
            this.textColor = textColor;
            return this;
        }

        /**
         * Label the button switches to for a short while right after a tap (e.g.
         * {@code "已复制"}). {@code null} (default) = not configured = the button does not
         * change at all.
         *
         * <p>Confirmed is the <b>tap</b>, not the copy — the library never learns what the host
         * did with the source, see {@link #getSuccessText()}.
         */
        @NonNull
        public Builder successText(@Nullable String successText) {
            this.successText = successText;
            return this;
        }

        @NonNull
        public CodeBlockCopyTheme build() {
            return new CodeBlockCopyTheme(this);
        }
    }
}
