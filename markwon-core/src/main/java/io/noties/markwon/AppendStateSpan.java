package io.noties.markwon;

import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Marker span that carries {@link MarkwonAppendState} with a rendered {@link Spanned}.
 * <p>
 * It is attached to every {@link Spanned} returned by
 * {@link Markwon#appendMarkdown(Spanned, String)} (and
 * {@link Markwon#appendMarkdown(MarkwonAppendState, String)}), which allows to continue an
 * incremental session without keeping any additional references:
 * <pre>
 *     Spanned spanned = markwon.appendMarkdown(new SpannableStringBuilder(), "# Heading");
 *     spanned = markwon.appendMarkdown(spanned, "\n\nmore **text**");
 * </pre>
 * or, when a TextView is used (it receives this span as well):
 * <pre>
 *     markwon.appendMarkdown(textView, chunk);
 * </pre>
 * <p>
 * NB, this span is an implementation detail: it must not be removed if incremental parsing is
 * intended, and a Spanned that carries it should not be persisted (it is not parcelable).
 *
 * @since 4.6.2
 */
final class AppendStateSpan {

    @NonNull
    private final MarkwonAppendState state;

    AppendStateSpan(@NonNull MarkwonAppendState state) {
        this.state = state;
    }

    @NonNull
    MarkwonAppendState state() {
        return state;
    }

    @Nullable
    static MarkwonAppendState find(@NonNull Spanned spanned) {
        final AppendStateSpan[] spans = spanned.getSpans(0, spanned.length(), AppendStateSpan.class);
        if (spans != null && spans.length > 0) {
            return spans[0].state();
        }
        return null;
    }

    static void removeAll(@NonNull Spanned spanned) {
        if (spanned instanceof android.text.Spannable) {
            final android.text.Spannable spannable = (android.text.Spannable) spanned;
            final AppendStateSpan[] spans = spannable.getSpans(0, spannable.length(), AppendStateSpan.class);
            if (spans != null) {
                for (AppendStateSpan span : spans) {
                    spannable.removeSpan(span);
                }
            }
        }
    }
}
