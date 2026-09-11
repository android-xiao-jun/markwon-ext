package io.noties.markwon.core.factory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.RenderProps;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.core.CodeBlockCopyTheme;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.core.scroll.CodeBlockScrollState;
import io.noties.markwon.core.spans.CodeBlockSpan;

public class CodeBlockSpanFactory implements SpanFactory {

    /**
     * Style of the header row's copy button, or {@code null} for "no button".
     *
     * <p>Supplied by {@code CodeBlockScrollPlugin} (which re-registers this factory for the two
     * code block nodes) — the button is that plugin's feature, so the plugin owns the style and
     * hands it over right here. It never travels through {@link MarkwonTheme}, which is what
     * keeps one plugin's concern out of the global theme.
     *
     * @since 4.6.3
     */
    @Nullable
    private final CodeBlockCopyTheme copyTheme;

    public CodeBlockSpanFactory() {
        this(null);
    }

    /**
     * @since 4.6.3
     */
    public CodeBlockSpanFactory(@Nullable CodeBlockCopyTheme copyTheme) {
        this.copyTheme = copyTheme;
    }

    @Nullable
    @Override
    public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
        final MarkwonTheme theme = configuration.theme();
        if (!theme.isCodeBlockScrollable()) {
            return new CodeBlockSpan(theme);
        }
        // @since 4.6.3 — the scroll state is created by CorePlugin#visitCodeBlock (it also
        // applies the per-line spans, so it has to own the state); the language label comes
        // from the info string of the fenced code block, and the original source (handed to
        // the host by the copy button) from the very same visitor pass
        final CodeBlockScrollState state = CoreProps.CODE_BLOCK_SCROLL_STATE.get(props);
        return new CodeBlockSpan(
                theme,
                CoreProps.CODE_BLOCK_INFO.get(props),
                CoreProps.CODE_BLOCK_CODE.get(props),
                copyTheme,
                state != null ? state : new CodeBlockScrollState());
    }
}
