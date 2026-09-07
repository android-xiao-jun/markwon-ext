package io.noties.markwon;

import androidx.annotation.NonNull;

/**
 * @since 4.1.1
 */
abstract class MarkwonVisitorFactory {

    @NonNull
    abstract MarkwonVisitor create();

    /**
     * @since 4.6.2 build a visitor with a pre-populated {@link SpannableBuilder}. Used by
     * {@code appendMarkdown} to render into a builder seeded with already settled content so
     * {@code HtmlPlugin}'s stateful fragment processing keeps emitting byte-identical output to
     * a full-document render.
     */
    @NonNull
    abstract MarkwonVisitor create(@NonNull SpannableBuilder builder);

    @NonNull
    static MarkwonVisitorFactory create(
            @NonNull final MarkwonVisitorImpl.Builder builder,
            @NonNull final MarkwonConfiguration configuration) {
        return new MarkwonVisitorFactory() {
            @NonNull
            @Override
            MarkwonVisitor create() {
                return builder.build(configuration, new RenderPropsImpl());
            }

            @NonNull
            @Override
            MarkwonVisitor create(@NonNull SpannableBuilder b) {
                return builder.build(configuration, new RenderPropsImpl(), b);
            }
        };
    }
}
