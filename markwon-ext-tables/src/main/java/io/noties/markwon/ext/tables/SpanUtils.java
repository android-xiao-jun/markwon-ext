package io.noties.markwon.ext.tables;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.Spanned;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.core.spans.TextLayoutSpan;
import io.noties.markwon.core.spans.TextViewSpan;

/**
 * @since 4.4.0
 */
public abstract class SpanUtils {

    public static int width(@Nullable Canvas canvas, @Nullable CharSequence cs) {
        // Layout
        // TextView
        // canvas

        if (cs instanceof Spanned) {
            final Spanned spanned = (Spanned) cs;

            // if we are displayed with layout information -> use it
            final Layout layout = TextLayoutSpan.layoutOf(spanned);
            if (layout != null && layout.getWidth() > 0) {
                return layout.getWidth();
            }

            // if we have TextView -> obtain width from it (exclude padding)
            final TextView textView = TextViewSpan.textViewOf(spanned);
            if (textView != null && textView.getWidth()>0) {
                return textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
            }
        }

        // else just use canvas width
        return canvas == null ? 1080 : canvas.getWidth();
    }
}
