package io.noties.markwon.core.factory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.RenderProps;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.scroll.CodeBlockScrollState;
import io.noties.markwon.core.spans.CodeBlockSpan;

public class CodeBlockSpanFactory implements SpanFactory {
    @Nullable
    @Override
    public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
        final MarkwonTheme theme = configuration.theme();
        if (!theme.isCodeBlockScrollable()) {
            return new CodeBlockSpan(theme);
        }
        // @since 4.6.3 — the scroll state is created by CorePlugin#visitCodeBlock (it also
        // applies the per-line spans, so it has to own the state); the language label comes
        // from the info string of the fenced code block
        final CodeBlockScrollState state = CoreProps.CODE_BLOCK_SCROLL_STATE.get(props);
        return new CodeBlockSpan(
                theme,
                CoreProps.CODE_BLOCK_INFO.get(props),
                state != null ? state : new CodeBlockScrollState());
    }
}
