package io.noties.markwon.core.spans;

import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Builds a {@link Path} that describes a rectangle with independently rounded
 * corners. Used as a {@code Canvas#clipPath} source on API &lt; 21 where
 * {@code Path#addRoundRect(float[], ...)} is not available. API 21+ has
 * hardware-accelerated path construction, but this implementation works on
 * all supported API levels.
 *
 * @since 4.6.3
 */
final class RoundedRectPath {

    private RoundedRectPath() {
    }

    static void build(
            @NonNull Path path,
            @NonNull RectF bounds,
            float radius,
            boolean topLeft,
            boolean topRight,
            boolean bottomRight,
            boolean bottomLeft) {

        final float r = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2F);

        path.reset();
        path.moveTo(bounds.left + (topLeft ? r : 0F), bounds.top);

        if (topRight) {
            path.lineTo(bounds.right - r, bounds.top);
            path.arcTo(bounds.right - r * 2, bounds.top, bounds.right, bounds.top + r * 2, -90F, 90F, false);
        } else {
            path.lineTo(bounds.right, bounds.top);
        }

        if (bottomRight) {
            path.lineTo(bounds.right, bounds.bottom - r);
            path.arcTo(bounds.right - r * 2, bounds.bottom - r * 2, bounds.right, bounds.bottom, 0F, 90F, false);
        } else {
            path.lineTo(bounds.right, bounds.bottom);
        }

        if (bottomLeft) {
            path.lineTo(bounds.left + r, bounds.bottom);
            path.arcTo(bounds.left, bounds.bottom - r * 2, bounds.left + r * 2, bounds.bottom, 90F, 90F, false);
        } else {
            path.lineTo(bounds.left, bounds.bottom);
        }

        if (topLeft) {
            path.lineTo(bounds.left, bounds.top + r);
            path.arcTo(bounds.left, bounds.top, bounds.left + r * 2, bounds.top + r * 2, 180F, 90F, false);
        } else {
            path.lineTo(bounds.left, bounds.top);
        }

        path.close();
    }
}
