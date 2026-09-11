package io.noties.markwon.core.scroll;

import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Handed the source of a code block when the <b>copy button</b> of its header row is tapped.
 *
 * <p><b>The library never touches the clipboard itself.</b> Writing a {@code ClipData} is the
 * host's call — it is the only one that knows what the payload should look like (a label, a
 * trimmed variant, a different flavour for a specific screen), whether a copy should be
 * recorded, and how the user should be told about it. This callback is the button's entire
 * body: with no listener attached the button does nothing at all.
 *
 * <pre>
 * Markwon.builder(context)
 *         .usePlugin(CodeBlockScrollPlugin.create()
 *                 .onCodeBlockCopy((textView, code) -&gt; {
 *                     final ClipboardManager manager =
 *                             (ClipboardManager) textView.getContext()
 *                                     .getSystemService(Context.CLIPBOARD_SERVICE);
 *                     if (manager != null) {
 *                         manager.setPrimaryClip(ClipData.newPlainText(null, code));
 *                     }
 *                 }))
 *         .build();
 * </pre>
 *
 * <p>Note that Android 13 (API 33) and above show a system notification on every clipboard
 * write, so a {@code Toast} there would be a duplicate — check
 * {@code Build.VERSION.SDK_INT} if the host supports both.
 *
 * @see CodeBlockScrollPlugin#onCodeBlockCopy(CodeBlockCopyListener)
 * @since 4.6.3
 */
public interface CodeBlockCopyListener {

    /**
     * @param textView the {@code TextView} the block belongs to
     * @param code     the original source of the block, exactly as the author wrote it
     */
    void onCodeBlockCopy(@NonNull TextView textView, @NonNull String code);
}
