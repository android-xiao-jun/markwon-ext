package io.noties.markwon.core;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.annotation.Size;

import java.util.Arrays;
import java.util.Locale;

import io.noties.markwon.MarkwonPlugin;
import io.noties.markwon.utils.ColorUtils;
import io.noties.markwon.utils.Dip;

/**
 * Class to hold <i>theming</i> information for rending of markdown.
 * <p>
 * Since version 3.0.0 this class should be considered as <em>CoreTheme</em> as its
 * information holds data for core features only. But based on this other components can still use it
 * to display markdown consistently.
 * <p>
 * Since version 3.0.0 this class should not be instantiated manually. Instead a {@link MarkwonPlugin}
 * should be used: {@link MarkwonPlugin#configureTheme(Builder)}
 * <p>
 * Since version 3.0.0 properties related to <em>strike-through</em>, <em>tables</em> and <em>HTML</em>
 * are moved to specific plugins in independent artifacts
 *
 * @see CorePlugin
 * @see MarkwonPlugin#configureTheme(Builder)
 */
@SuppressWarnings("WeakerAccess")
public class MarkwonTheme {

    /**
     * Factory method to obtain an instance of {@link MarkwonTheme} with all values as defaults
     *
     * @param context Context in order to resolve defaults
     * @return {@link MarkwonTheme} instance
     * @see #builderWithDefaults(Context)
     * @since 1.0.0
     */
    @NonNull
    public static MarkwonTheme create(@NonNull Context context) {
        return builderWithDefaults(context).build();
    }

    /**
     * Create an <strong>empty</strong> instance of {@link Builder} with no default values applied
     * <p>
     * Since version 3.0.0 manual construction of {@link MarkwonTheme} is not required, instead a
     * {@link MarkwonPlugin#configureTheme(Builder)} should be used in order
     * to change certain theme properties
     *
     * @since 3.0.0
     */
    @SuppressWarnings("unused")
    @NonNull
    public static Builder emptyBuilder() {
        return new Builder();
    }

    /**
     * Factory method to create a {@link Builder} instance and initialize it with values
     * from supplied {@link MarkwonTheme}
     *
     * @param copyFrom {@link MarkwonTheme} to copy values from
     * @return {@link Builder} instance
     * @see #builderWithDefaults(Context)
     * @since 1.0.0
     */
    @NonNull
    public static Builder builder(@NonNull MarkwonTheme copyFrom) {
        return new Builder(copyFrom);
    }

    /**
     * Factory method to obtain a {@link Builder} instance initialized with default values taken
     * from current application theme.
     *
     * @param context Context to obtain default styling values (colors, etc)
     * @return {@link Builder} instance
     * @since 1.0.0
     */
    @NonNull
    public static Builder builderWithDefaults(@NonNull Context context) {

        final Dip dip = Dip.create(context);
        return new Builder()
                .codeBlockMargin(dip.toPx(8))
                .blockMargin(dip.toPx(24))
                .blockQuoteWidth(dip.toPx(4))
                .bulletListItemStrokeWidth(dip.toPx(1))
                .headingBreakHeight(dip.toPx(1))
                .thematicBreakHeight(dip.toPx(4));
    }

    protected static final int BLOCK_QUOTE_DEF_COLOR_ALPHA = 25;

    protected static final int CODE_DEF_BACKGROUND_COLOR_ALPHA = 25;
    protected static final float CODE_DEF_TEXT_SIZE_RATIO = .87F;

    protected static final int HEADING_DEF_BREAK_COLOR_ALPHA = 75;

    // taken from html spec (most browsers render headings like that)
    // is not exposed via protected modifier in order to disallow modification
    private static final float[] HEADING_SIZES = {
            2.F, 1.5F, 1.17F, 1.F, .83F, .67F,
    };

    protected static final int THEMATIC_BREAK_DEF_ALPHA = 25;

    protected final int linkColor;

    // specifies whether we underline links, by default is true
    // @since 4.5.0
    protected final boolean isLinkedUnderlined;

    // used in quote, lists
    protected final int blockMargin;

    // by default it's 1/4th of `blockMargin`
    protected final int blockQuoteWidth;

    // by default it's text color with `BLOCK_QUOTE_DEF_COLOR_ALPHA` applied alpha
    protected final int blockQuoteColor;

    // by default uses text color (applied for un-ordered lists & ordered (bullets & numbers)
    protected final int listItemColor;

    // by default the stroke color of a paint object
    protected final int bulletListItemStrokeWidth;

    // width of bullet, by default min(blockMargin, height) / 2
    protected final int bulletWidth;

    // by default - main text color
    protected final int codeTextColor;

    // by default - codeTextColor
    protected final int codeBlockTextColor;

    // by default 0.1 alpha of textColor/codeTextColor
    protected final int codeBackgroundColor;

    // by default codeBackgroundColor
    protected final int codeBlockBackgroundColor;

    // by default `width` of a space char... it's fun and games, but span doesn't have access to paint in `getLeadingMargin`
    // so, we need to set this value explicitly (think of an utility method, that takes TextView/TextPaint and measures space char)
    protected final int codeBlockMargin;

    // by default Typeface.MONOSPACE
    protected final Typeface codeTypeface;

    protected final Typeface codeBlockTypeface;

    // by default a bit (how much?!) smaller than normal text
    // applied ONLY if default typeface was used, otherwise, not applied
    protected final int codeTextSize;

    protected final int codeBlockTextSize;

    // @since 4.6.3 — corner radius (in pixels) of the inline-code background.
    // Non-positive value means no rounding (default, backward compatible).
    protected final int codeBackgroundRadius;

    // @since 4.6.3 — corner radius (in pixels) of the fenced-code-block background.
    // Non-positive value means no rounding (default, backward compatible).
    protected final int codeBlockBackgroundRadius;

    // @since 4.6.3 — when true code blocks are rendered as non-wrapping, horizontally
    // scrollable blocks with a header (language) and a scrollbar.
    protected final boolean codeBlockScrollable;

    // @since 4.6.3 — horizontal padding of a code block (also the left inset of the code
    // text and of the header label, and the bottom inset of the footer row).
    // `0` (default) = not configured = no inset at all.
    protected final int codeBlockPadding;

    // @since 4.6.3 — height of the header row (language label). `UNSET` (-1) and `0` both mean
    // "there is no header row": nothing is reserved and the language label is not painted at
    // all — the row is opt-in, exactly like the scrollbar. A positive value is used as is,
    // except that it is never allowed to be shorter than a line of text
    // (see #getCodeBlockHeaderHeight(Paint)).
    protected final int codeBlockHeaderHeight;

    // NB: there is deliberately no `codeBlockHeaderTextSize` / `codeBlockHeaderTextColor` /
    // `codeBlockHeaderBackgroundColor`. Those were duplicates of the older
    // `codeBlockTextSize` / `codeBlockTextColor` / `codeBlockBackgroundColor` above —
    // see #applyCodeBlockHeaderStyle(Paint), which now reuses those instead.

    // @since 4.6.3 — horizontal padding of inline code, in pixels. Default 4dp.
    // Kept as a theme value so the host app can match its design spec
    // (e.g. `padding: 2px 6px` around the inline-code background).
    protected final int codeHorizontalPadding;

    // @since 4.6.3 — height reserved for the horizontal scrollbar. `0` (default) =
    // not configured = no scrollbar at all (nothing is drawn, no room is reserved).
    protected final int codeBlockScrollbarHeight;

    // @since 4.6.3 — color of the scrollbar track. `0` (default) = not configured = the
    // track is not painted at all (no fallback color, no reserved footer row either).
    protected final int codeBlockScrollbarTrackColor;

    // @since 4.6.3 — color of the scrollbar thumb. `0` (default) = not configured = the
    // thumb is not painted. Configure it (and/or the track) to get a scrollbar.
    protected final int codeBlockScrollbarThumbColor;

    // by default paint.getStrokeWidth
    protected final int headingBreakHeight;

    // by default, text color with `HEADING_DEF_BREAK_COLOR_ALPHA` applied alpha
    protected final int headingBreakColor;

    // by default, whatever typeface is set on the TextView
    // @since 1.1.0
    protected final Typeface headingTypeface;

    // by default, we use standard multipliers from the HTML spec (see HEADING_SIZES for values).
    // this library supports 6 heading sizes, so make sure the array you pass here has 6 elements.
    // @since 1.1.0
    protected final float[] headingTextSizeMultipliers;

    // by default textColor with `THEMATIC_BREAK_DEF_ALPHA` applied alpha
    protected final int thematicBreakColor;

    // by default paint.strokeWidth
    protected final int thematicBreakHeight;

    protected MarkwonTheme(@NonNull Builder builder) {
        this.linkColor = builder.linkColor;
        this.isLinkedUnderlined = builder.isLinkUnderlined;
        this.blockMargin = builder.blockMargin;
        this.blockQuoteWidth = builder.blockQuoteWidth;
        this.blockQuoteColor = builder.blockQuoteColor;
        this.listItemColor = builder.listItemColor;
        this.bulletListItemStrokeWidth = builder.bulletListItemStrokeWidth;
        this.bulletWidth = builder.bulletWidth;
        this.codeTextColor = builder.codeTextColor;
        this.codeBlockTextColor = builder.codeBlockTextColor;
        this.codeBackgroundColor = builder.codeBackgroundColor;
        this.codeBlockBackgroundColor = builder.codeBlockBackgroundColor;
        this.codeBlockMargin = builder.codeBlockMargin;
        this.codeTypeface = builder.codeTypeface;
        this.codeBlockTypeface = builder.codeBlockTypeface;
        this.codeTextSize = builder.codeTextSize;
        this.codeBlockTextSize = builder.codeBlockTextSize;
        this.codeBackgroundRadius = builder.codeBackgroundRadius;
        this.codeBlockBackgroundRadius = builder.codeBlockBackgroundRadius;
        this.codeBlockScrollable = builder.codeBlockScrollable;
        this.codeBlockPadding = resolvePx(builder.codeBlockPadding, 0F);
        // NB: a header height that was not configured is kept as `UNSET` (-1) on purpose —
        // there is no pixel default, the row falls back to the height of a line of text.
        this.codeBlockHeaderHeight = builder.codeBlockHeaderHeight;
        // NB: no `!= 0 ? … : <hardcoded color>` fallback for the two scrollbar colors —
        // a color that was not configured stays 0, which the consumers read as
        // "this property does not exist" and the scrollbar is not painted at all.
        this.codeHorizontalPadding = resolvePx(builder.codeHorizontalPadding, 0F);
        this.codeBlockScrollbarHeight = resolvePx(builder.codeBlockScrollbarHeight, 0F);
        this.codeBlockScrollbarTrackColor = builder.codeBlockScrollbarTrackColor;
        this.codeBlockScrollbarThumbColor = builder.codeBlockScrollbarThumbColor;
        this.headingBreakHeight = builder.headingBreakHeight;
        this.headingBreakColor = builder.headingBreakColor;
        this.headingTypeface = builder.headingTypeface;
        this.headingTextSizeMultipliers = builder.headingTextSizeMultipliers;
        this.thematicBreakColor = builder.thematicBreakColor;
        this.thematicBreakHeight = builder.thematicBreakHeight;
    }

    /**
     * Sentinel for "not explicitly configured". A {@code @Px} builder field is initialized to
     * this value instead of {@code 0}, so that <b>{@code 0} is a legal explicit value</b>
     * (a zero padding really is zero). Same convention as {@code headingBreakHeight}.
     *
     * @since 4.6.3
     */
    private static final int UNSET = -1;

    /**
     * Resolves a px value that was not explicitly configured to its dp default.
     * Anything {@code >= 0} is taken literally — including {@code 0}.
     *
     * @since 4.6.3
     */
    private static int resolvePx(int value, float dp) {
        return value >= 0
                ? value
                : Math.round(Resources.getSystem().getDisplayMetrics().density * dp);
    }

    /**
     * Same as {@link #resolvePx(int, float)} but for text sizes (scaledDensity).
     *
     * @since 4.6.3
     */
    private static int resolveSp(int value, float sp) {
        return value > 0
                ? value
                : Math.round(Resources.getSystem().getDisplayMetrics().scaledDensity * sp);
    }

    /**
     * @since 1.0.5
     */
    public void applyLinkStyle(@NonNull TextPaint paint) {
        paint.setUnderlineText(isLinkedUnderlined);
        if (linkColor != 0) {
            paint.setColor(linkColor);
        } else {
            // if linkColor is not specified during configuration -> use default one
            paint.setColor(paint.linkColor);
        }
    }

    public void applyLinkStyle(@NonNull Paint paint) {
        paint.setUnderlineText(isLinkedUnderlined);
        if (linkColor != 0) {
            // by default we will be using text color
            paint.setColor(linkColor);
        } else {
            // @since 1.0.5, if link color is specified during configuration, _try_ to use the
            // default one (if provided paint is an instance of TextPaint)
            if (paint instanceof TextPaint) {
                paint.setColor(((TextPaint) paint).linkColor);
            }
        }
    }

    public void applyBlockQuoteStyle(@NonNull Paint paint) {
        final int color;
        if (blockQuoteColor == 0) {
            color = ColorUtils.applyAlpha(paint.getColor(), BLOCK_QUOTE_DEF_COLOR_ALPHA);
        } else {
            color = blockQuoteColor;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    public int getBlockMargin() {
        return blockMargin;
    }

    public int getBlockQuoteWidth() {
        final int out;
        if (blockQuoteWidth == 0) {
            out = (int) (blockMargin * .25F + .5F);
        } else {
            out = blockQuoteWidth;
        }
        return out;
    }

    public void applyListItemStyle(@NonNull Paint paint) {

        final int color;
        if (listItemColor != 0) {
            color = listItemColor;
        } else {
            color = paint.getColor();
        }
        paint.setColor(color);

        if (bulletListItemStrokeWidth != 0) {
            paint.setStrokeWidth(bulletListItemStrokeWidth);
        }
    }

    public int getBulletWidth(int height) {

        final int min = Math.min(blockMargin, height) / 2;

        final int width;
        if (bulletWidth == 0
                || bulletWidth > min) {
            width = min;
        } else {
            width = bulletWidth;
        }

        return width;
    }

    /**
     * @since 3.0.0
     */
    public void applyCodeTextStyle(@NonNull Paint paint) {

        if (codeTextColor != 0) {
            paint.setColor(codeTextColor);
        }

        if (codeTypeface != null) {

            paint.setTypeface(codeTypeface);

            if (codeTextSize > 0) {
                paint.setTextSize(codeTextSize);
            }

        } else {

            paint.setTypeface(Typeface.MONOSPACE);

            if (codeTextSize > 0) {
                paint.setTextSize(codeTextSize);
            } else {
                paint.setTextSize(paint.getTextSize() * CODE_DEF_TEXT_SIZE_RATIO);
            }
        }
    }

    /**
     * @since 3.0.0
     */
    public void applyCodeBlockTextStyle(@NonNull Paint paint) {

        // apply text color, first check for block specific value,
        // then check for code (inline), else do nothing (keep original color of text)
        final int textColor = codeBlockTextColor != 0
                ? codeBlockTextColor
                : codeTextColor;

        if (textColor != 0) {
            paint.setColor(textColor);
        }

        final Typeface typeface = codeBlockTypeface != null
                ? codeBlockTypeface
                : codeTypeface;

        if (typeface != null) {

            paint.setTypeface(typeface);

            // please note that we won't be calculating textSize
            // (like we do when no Typeface is provided), if it's some specific typeface
            // we would confuse users about textSize
            final int textSize = codeBlockTextSize > 0
                    ? codeBlockTextSize
                    : codeTextSize;

            if (textSize > 0) {
                paint.setTextSize(textSize);
            }
        } else {

            // by default use monospace
            paint.setTypeface(Typeface.MONOSPACE);

            final int textSize = codeBlockTextSize > 0
                    ? codeBlockTextSize
                    : codeTextSize;

            if (textSize > 0) {
                paint.setTextSize(textSize);
            } else {
                // calculate default value
                paint.setTextSize(paint.getTextSize() * CODE_DEF_TEXT_SIZE_RATIO);
            }
        }
    }


    public int getCodeBlockMargin() {
        return codeBlockMargin;
    }

    /**
     * @since 3.0.0
     */
    public int getCodeBackgroundColor(@NonNull Paint paint) {
        final int color;
        if (codeBackgroundColor != 0) {
            color = codeBackgroundColor;
        } else {
            color = ColorUtils.applyAlpha(paint.getColor(), CODE_DEF_BACKGROUND_COLOR_ALPHA);
        }
        return color;
    }

    /**
     * @since 3.0.0
     */
    public int getCodeBlockBackgroundColor(@NonNull Paint paint) {

        final int color = codeBlockBackgroundColor != 0
                ? codeBlockBackgroundColor
                : codeBackgroundColor;

        return color != 0
                ? color
                : ColorUtils.applyAlpha(paint.getColor(), CODE_DEF_BACKGROUND_COLOR_ALPHA);
    }

    /**
     * Returns the corner radius (in pixels) used for the inline-code background.
     * A non-positive value means no rounding.
     *
     * @since 4.6.3
     */
    @Px
    public int getCodeBackgroundRadius() {
        return codeBackgroundRadius;
    }

    /**
     * Returns the corner radius (in pixels) used for the fenced-code-block background.
     * A non-positive value means no rounding.
     *
     * @since 4.6.3
     */
    @Px
    public int getCodeBlockBackgroundRadius() {
        return codeBlockBackgroundRadius;
    }

    /**
     * Whether code blocks are rendered as non-wrapping, horizontally scrollable blocks.
     *
     * @see io.noties.markwon.core.scroll.CodeBlockScrollPlugin
     * @since 4.6.3
     */
    public boolean isCodeBlockScrollable() {
        return codeBlockScrollable;
    }

    /**
     * Horizontal padding of a code block, in pixels. {@code 0} (default) = not configured
     * = no inset: the code text starts right at the edge of the block background.
     *
     * @since 4.6.3
     */
    @Px
    public int getCodeBlockPadding() {
        return codeBlockPadding;
    }

    /**
     * Raw configured height of the header row (the one holding the language label), in pixels,
     * or a <b>negative</b> value ({@code UNSET}) when it is not configured — use
     * {@link #getCodeBlockHeaderHeight(Paint)} to obtain a usable height.
     *
     * @see #getCodeBlockHeaderHeight(Paint)
     * @since 4.6.3
     */
    @Px
    public int getCodeBlockHeaderHeight() {
        return codeBlockHeaderHeight;
    }

    /**
     * Height of the header row, resolved against {@code paint}:
     * <ul>
     *     <li>a height that was <b>not configured</b> ({@code UNSET}) as well as an explicit
     *     {@code 0} resolve to {@code 0} — there simply is <b>no header row</b>: no room is
     *     reserved and the language label is not painted (the row is opt-in, same rule as
     *     {@link #isCodeBlockScrollbarEnabled()});</li>
     *     <li>any positive height is honoured, but is <b>raised to the height of a line of
     *     text</b> ({@code descent - ascent} of the supplied paint) when it is too short for
     *     the label — a row that clips its own label is never useful.</li>
     * </ul>
     *
     * @since 4.6.3
     */
    @Px
    public int getCodeBlockHeaderHeight(@NonNull Paint paint) {
        if (codeBlockHeaderHeight <= 0) {
            return 0;
        }
        return Math.max(codeBlockHeaderHeight, Math.round(paint.descent() - paint.ascent()));
    }

    /**
     * Height reserved below the last code line for the horizontal scrollbar, in pixels.
     * {@code 0} (default) = not configured = no scrollbar at all.
     *
     * @see #isCodeBlockScrollbarEnabled()
     * @since 4.6.3
     */
    @Px
    public int getCodeBlockScrollbarHeight() {
        return codeBlockScrollbarHeight;
    }

    /**
     * Whether a horizontal scrollbar must be painted (and room for it reserved) at all.
     *
     * <p>The scrollbar is an <em>opt-in</em> decoration: it exists only when it was given a
     * height (default is <b>no</b> height) <b>and</b> at least one of its two colors. Without
     * it a scrollable code block simply ends after the last code line (+ the bottom padding).
     *
     * @since 4.6.3
     */
    public boolean isCodeBlockScrollbarEnabled() {
        return codeBlockScrollbarHeight > 0
                && (codeBlockScrollbarTrackColor != 0 || codeBlockScrollbarThumbColor != 0);
    }

    /**
     * Color of the scrollbar track, or {@code 0} when it is not configured — in that case
     * the track is not painted (see {@link #isCodeBlockScrollbarEnabled()}).
     *
     * @since 4.6.3
     */
    @ColorInt
    public int getCodeBlockScrollbarTrackColor() {
        return codeBlockScrollbarTrackColor;
    }

    /**
     * Color of the scrollbar thumb, or {@code 0} when it is not configured — in that case
     * the thumb is not painted (see {@link #isCodeBlockScrollbarEnabled()}).
     *
     * @since 4.6.3
     */
    @ColorInt
    public int getCodeBlockScrollbarThumbColor() {
        return codeBlockScrollbarThumbColor;
    }

    /**
     * Horizontal padding of inline code, in pixels.
     *
     * @since 4.6.3
     */
    @Px
    public int getCodeHorizontalPadding() {
        return codeHorizontalPadding;
    }

    /**
     * Applies the styling of the language label drawn in the header row.
     *
     * <p>The label reuses the <b>code block text style</b> instead of carrying a size and a
     * color of its own: it follows {@code codeBlockTextSize} → {@code codeTextSize} and
     * {@code codeBlockTextColor} → {@code codeTextColor}, i.e. exactly the fallback chain of
     * {@link #applyCodeBlockTextStyle(Paint)}. A caller that seeds the paint first (as
     * {@code CodeBlockSpan#drawHeader} does) therefore gets the code block text color and
     * size for free, and there is no separate {@code codeBlockHeaderText*} to keep in sync.
     *
     * @since 4.6.3
     */
    public void applyCodeBlockHeaderStyle(@NonNull Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setFakeBoldText(false);
        paint.setUnderlineText(false);
        paint.setTypeface(Typeface.DEFAULT);

        // NB: only touch the text size when one was actually configured — setting 0 would
        // make the label invisible, so "not configured" means "keep what the paint has".
        final int textSize = codeBlockTextSize > 0
                ? codeBlockTextSize
                : codeTextSize;
        if (textSize > 0) {
            paint.setTextSize(textSize);
        }

        final int textColor = codeBlockTextColor != 0
                ? codeBlockTextColor
                : codeTextColor;
        if (textColor != 0) {
            paint.setColor(textColor);
        }
    }

    public void applyHeadingTextStyle(@NonNull Paint paint, @IntRange(from = 1, to = 6) int level) {
        if (headingTypeface == null) {
            paint.setFakeBoldText(true);
        } else {
            paint.setTypeface(headingTypeface);
        }
        final float[] textSizes = headingTextSizeMultipliers != null
                ? headingTextSizeMultipliers
                : HEADING_SIZES;

        if (textSizes != null && textSizes.length >= level) {
            paint.setTextSize(paint.getTextSize() * textSizes[level - 1]);
        } else {
            throw new IllegalStateException(String.format(
                    Locale.US,
                    "Supplied heading level: %d is invalid, where configured heading sizes are: `%s`",
                    level, Arrays.toString(textSizes)));
        }
    }

    public void applyHeadingBreakStyle(@NonNull Paint paint) {
        final int color;
        if (headingBreakColor != 0) {
            color = headingBreakColor;
        } else {
            color = ColorUtils.applyAlpha(paint.getColor(), HEADING_DEF_BREAK_COLOR_ALPHA);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        if (headingBreakHeight >= 0) {
            //noinspection SuspiciousNameCombination
            paint.setStrokeWidth(headingBreakHeight);
        }
    }

    public void applyThematicBreakStyle(@NonNull Paint paint) {
        final int color;
        if (thematicBreakColor != 0) {
            color = thematicBreakColor;
        } else {
            color = ColorUtils.applyAlpha(paint.getColor(), THEMATIC_BREAK_DEF_ALPHA);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        if (thematicBreakHeight >= 0) {
            //noinspection SuspiciousNameCombination
            paint.setStrokeWidth(thematicBreakHeight);
        }
    }

    @SuppressWarnings("unused")
    public static class Builder {

        private int linkColor;
        private boolean isLinkUnderlined = true; // @since 4.5.0
        private int blockMargin;
        private int blockQuoteWidth;
        private int blockQuoteColor;
        private int listItemColor;
        private int bulletListItemStrokeWidth;
        private int bulletWidth;
        private int codeTextColor;
        private int codeBlockTextColor; // @since 1.0.5
        private int codeBackgroundColor;
        private int codeBlockBackgroundColor; // @since 1.0.5
        private int codeBlockMargin;
        private Typeface codeTypeface;
        private Typeface codeBlockTypeface; // @since 3.0.0
        private int codeTextSize;
        private int codeBlockTextSize; // @since 3.0.0
        private int codeBackgroundRadius; // @since 4.6.3
        private int codeBlockBackgroundRadius; // @since 4.6.3
        private boolean codeBlockScrollable; // @since 4.6.3
        private int codeBlockPadding = UNSET; // @since 4.6.3
        private int codeBlockHeaderHeight = UNSET; // @since 4.6.3
        // NB: no `codeBlockHeaderTextSize` / `codeBlockHeaderTextColor` /
        // `codeBlockHeaderBackgroundColor` — duplicates of `codeBlockTextSize` /
        // `codeBlockTextColor` / `codeBlockBackgroundColor`, see applyCodeBlockHeaderStyle.
        private int codeHorizontalPadding = UNSET; // @since 4.6.3
        private int codeBlockScrollbarHeight = UNSET; // @since 4.6.3
        private int codeBlockScrollbarTrackColor; // @since 4.6.3
        private int codeBlockScrollbarThumbColor; // @since 4.6.3
        private int headingBreakHeight = -1;
        private int headingBreakColor;
        private Typeface headingTypeface;
        private float[] headingTextSizeMultipliers;
        private int thematicBreakColor;
        private int thematicBreakHeight = -1;

        Builder() {
        }

        Builder(@NonNull MarkwonTheme theme) {
            this.linkColor = theme.linkColor;
            this.isLinkUnderlined = theme.isLinkedUnderlined;
            this.blockMargin = theme.blockMargin;
            this.blockQuoteWidth = theme.blockQuoteWidth;
            this.blockQuoteColor = theme.blockQuoteColor;
            this.listItemColor = theme.listItemColor;
            this.bulletListItemStrokeWidth = theme.bulletListItemStrokeWidth;
            this.bulletWidth = theme.bulletWidth;
            this.codeTextColor = theme.codeTextColor;
            this.codeBlockTextColor = theme.codeBlockTextColor;
            this.codeBackgroundColor = theme.codeBackgroundColor;
            this.codeBlockBackgroundColor = theme.codeBlockBackgroundColor;
            this.codeBlockMargin = theme.codeBlockMargin;
            this.codeTypeface = theme.codeTypeface;
            this.codeTextSize = theme.codeTextSize;
            this.codeBlockTypeface = theme.codeBlockTypeface;
            this.codeBlockTextSize = theme.codeBlockTextSize;
            this.codeBackgroundRadius = theme.codeBackgroundRadius;
            this.codeBlockBackgroundRadius = theme.codeBlockBackgroundRadius;
            this.codeBlockScrollable = theme.codeBlockScrollable;
            this.codeBlockPadding = theme.codeBlockPadding;
            this.codeBlockHeaderHeight = theme.codeBlockHeaderHeight;
            this.codeHorizontalPadding = theme.codeHorizontalPadding;
            this.codeBlockScrollbarHeight = theme.codeBlockScrollbarHeight;
            this.codeBlockScrollbarTrackColor = theme.codeBlockScrollbarTrackColor;
            this.codeBlockScrollbarThumbColor = theme.codeBlockScrollbarThumbColor;
            this.headingBreakHeight = theme.headingBreakHeight;
            this.headingBreakColor = theme.headingBreakColor;
            this.headingTypeface = theme.headingTypeface;
            this.headingTextSizeMultipliers = theme.headingTextSizeMultipliers;
            this.thematicBreakColor = theme.thematicBreakColor;
            this.thematicBreakHeight = theme.thematicBreakHeight;
        }

        @NonNull
        public Builder linkColor(@ColorInt int linkColor) {
            this.linkColor = linkColor;
            return this;
        }

        @NonNull
        public Builder isLinkUnderlined(boolean isLinkUnderlined) {
            this.isLinkUnderlined = isLinkUnderlined;
            return this;
        }

        @NonNull
        public Builder blockMargin(@Px int blockMargin) {
            this.blockMargin = blockMargin;
            return this;
        }

        @NonNull
        public Builder blockQuoteWidth(@Px int blockQuoteWidth) {
            this.blockQuoteWidth = blockQuoteWidth;
            return this;
        }

        @SuppressWarnings("SameParameterValue")
        @NonNull
        public Builder blockQuoteColor(@ColorInt int blockQuoteColor) {
            this.blockQuoteColor = blockQuoteColor;
            return this;
        }

        @NonNull
        public Builder listItemColor(@ColorInt int listItemColor) {
            this.listItemColor = listItemColor;
            return this;
        }

        @NonNull
        public Builder bulletListItemStrokeWidth(@Px int bulletListItemStrokeWidth) {
            this.bulletListItemStrokeWidth = bulletListItemStrokeWidth;
            return this;
        }

        @NonNull
        public Builder bulletWidth(@Px int bulletWidth) {
            this.bulletWidth = bulletWidth;
            return this;
        }

        @NonNull
        public Builder codeTextColor(@ColorInt int codeTextColor) {
            this.codeTextColor = codeTextColor;
            return this;
        }

        /**
         * @since 1.0.5
         */
        @NonNull
        public Builder codeBlockTextColor(@ColorInt int codeBlockTextColor) {
            this.codeBlockTextColor = codeBlockTextColor;
            return this;
        }

        @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
        @NonNull
        public Builder codeBackgroundColor(@ColorInt int codeBackgroundColor) {
            this.codeBackgroundColor = codeBackgroundColor;
            return this;
        }

        /**
         * @since 1.0.5
         */
        @NonNull
        public Builder codeBlockBackgroundColor(@ColorInt int codeBlockBackgroundColor) {
            this.codeBlockBackgroundColor = codeBlockBackgroundColor;
            return this;
        }

        @NonNull
        public Builder codeBlockMargin(@Px int codeBlockMargin) {
            this.codeBlockMargin = codeBlockMargin;
            return this;
        }

        @NonNull
        public Builder codeTypeface(@NonNull Typeface codeTypeface) {
            this.codeTypeface = codeTypeface;
            return this;
        }

        /**
         * @since 3.0.0
         */
        @NonNull
        public Builder codeBlockTypeface(@NonNull Typeface typeface) {
            this.codeBlockTypeface = typeface;
            return this;
        }

        @NonNull
        public Builder codeTextSize(@Px int codeTextSize) {
            this.codeTextSize = codeTextSize;
            return this;
        }

        /**
         * @since 3.0.0
         */
        @NonNull
        public Builder codeBlockTextSize(@Px int codeTextSize) {
            this.codeBlockTextSize = codeTextSize;
            return this;
        }

        /**
         * Sets the horizontal padding (in pixels) applied to inline code — the distance
         * between the text and the left/right edge of its background. Default 4dp.
         *
         * <p>Like {@link #codeBlockPadding(int)}, {@code 0} is a legal explicit value.
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder codeHorizontalPadding(@Px int codeHorizontalPadding) {
            this.codeHorizontalPadding = codeHorizontalPadding;
            return this;
        }

        /**
         * Sets the corner radius (in pixels) for the inline-code background.
         * Pass a non-positive value to draw square corners (default).
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBackgroundRadius(@Px int codeBackgroundRadius) {
            this.codeBackgroundRadius = codeBackgroundRadius;
            return this;
        }

        /**
         * Sets the corner radius (in pixels) for the fenced-code-block background.
         * Pass a non-positive value to draw square corners (default).
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockBackgroundRadius(@Px int codeBlockBackgroundRadius) {
            this.codeBlockBackgroundRadius = codeBlockBackgroundRadius;
            return this;
        }

        /**
         * Renders code blocks as non-wrapping, horizontally scrollable blocks with a header
         * (language label) and a scrollbar. Off by default.
         *
         * @see io.noties.markwon.core.scroll.CodeBlockScrollPlugin
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockScrollable(boolean codeBlockScrollable) {
            this.codeBlockScrollable = codeBlockScrollable;
            return this;
        }

        /**
         * Horizontal padding of a code block, in pixels. Default {@code 0} = <b>not
         * configured</b> = no inset: code text, header label and scrollbar all start/end
         * right at the edge of the block background.
         *
         * <p>NB: {@code 0} is the default but also a legal explicit value — the backing field
         * is initialized to {@link MarkwonTheme#UNSET} rather than {@code 0}, so only a
         * genuinely unset value falls back (to {@code 0}).
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockPadding(@Px int codeBlockPadding) {
            this.codeBlockPadding = codeBlockPadding;
            return this;
        }

        /**
         * Height of the header row, in pixels. <b>Not configured by default</b>, and a row that
         * was not configured is <b>not rendered at all</b> — no room is reserved, the language
         * label is not painted. Pass a positive value to get one.
         *
         * <p>A height that is too small for the label is raised to the height of a line of text
         * at draw time ({@link MarkwonTheme#getCodeBlockHeaderHeight(Paint)}), so the label can
         * never be clipped by its own row.
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockHeaderHeight(@Px int codeBlockHeaderHeight) {
            this.codeBlockHeaderHeight = codeBlockHeaderHeight;
            return this;
        }

        // NB: there is no codeBlockHeaderTextSize / codeBlockHeaderTextColor /
        // codeBlockHeaderBackgroundColor setter (@since 4.6.3): the language label is part of
        // the code block, so it reuses codeBlockTextSize / codeBlockTextColor /
        // codeBlockBackgroundColor — see MarkwonTheme#applyCodeBlockHeaderStyle(Paint).

        /**
         * Height reserved for the scrollbar, in pixels. Default {@code 0} = <b>not
         * configured</b> = no scrollbar (nothing is drawn and no room is reserved).
         *
         * <p>Only consumed when a scrollbar is actually enabled
         * ({@link MarkwonTheme#isCodeBlockScrollbarEnabled()}), which additionally needs at
         * least one of the two colors.
         *
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockScrollbarHeight(@Px int codeBlockScrollbarHeight) {
            this.codeBlockScrollbarHeight = codeBlockScrollbarHeight;
            return this;
        }

        /**
         * Color of the scrollbar track. Default {@code 0} = <b>not configured</b> = the track
         * is not painted. Configure it (and/or the thumb color) to opt into a scrollbar.
         *
         * @see MarkwonTheme#isCodeBlockScrollbarEnabled()
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockScrollbarTrackColor(@ColorInt int color) {
            this.codeBlockScrollbarTrackColor = color;
            return this;
        }

        /**
         * Color of the scrollbar thumb. Default {@code 0} = <b>not configured</b> = the thumb
         * is not painted. Configure it (and/or the track color) to opt into a scrollbar.
         *
         * @see MarkwonTheme#isCodeBlockScrollbarEnabled()
         * @since 4.6.3
         */
        @NonNull
        public Builder codeBlockScrollbarThumbColor(@ColorInt int color) {
            this.codeBlockScrollbarThumbColor = color;
            return this;
        }

        public Builder headingBreakHeight(@Px int headingBreakHeight) {
            this.headingBreakHeight = headingBreakHeight;
            return this;
        }

        @NonNull
        public Builder headingBreakColor(@ColorInt int headingBreakColor) {
            this.headingBreakColor = headingBreakColor;
            return this;
        }

        /**
         * @param headingTypeface Typeface to use for heading elements
         * @return self
         * @since 1.1.0
         */
        @NonNull
        public Builder headingTypeface(@NonNull Typeface headingTypeface) {
            this.headingTypeface = headingTypeface;
            return this;
        }

        /**
         * @param headingTextSizeMultipliers an array of multipliers values for heading elements.
         *                                   The base value for this multipliers is TextView\'s text size
         * @return self
         * @since 1.1.0
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Builder headingTextSizeMultipliers(@Size(6) @NonNull float[] headingTextSizeMultipliers) {
            this.headingTextSizeMultipliers = headingTextSizeMultipliers;
            return this;
        }

        @NonNull
        public Builder thematicBreakColor(@ColorInt int thematicBreakColor) {
            this.thematicBreakColor = thematicBreakColor;
            return this;
        }

        @NonNull
        public Builder thematicBreakHeight(@Px int thematicBreakHeight) {
            this.thematicBreakHeight = thematicBreakHeight;
            return this;
        }

        @NonNull
        public MarkwonTheme build() {
            return new MarkwonTheme(this);
        }
    }

}
