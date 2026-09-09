package io.noties.markwon.core.factory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.RenderProps;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.spans.CodeRoundedSpan;
import io.noties.markwon.core.spans.CodeSpan;

public class CodeSpanFactory implements SpanFactory {
    @Nullable
    @Override
    public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
        final MarkwonTheme theme = configuration.theme();
        // @since 4.6.3 — when a positive radius is requested, CodeRoundedSpan
        // takes over rendering (it draws a rounded background and the glyphs
        // itself). The plain CodeSpan keeps the original TextView-managed
        // background via TextPaint#bgColor.
        if (theme.getCodeBackgroundRadius() > 0) {
            return new CodeRoundedSpan(theme);
        }
        return new CodeSpan(theme);
    }
}
