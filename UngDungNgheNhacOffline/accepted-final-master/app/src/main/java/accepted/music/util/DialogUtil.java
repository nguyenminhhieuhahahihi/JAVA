package accepted.music.util;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.google.common.base.Preconditions;

public final class DialogUtil {
    private static final String TAG = "DialogUtil";

    private DialogUtil() {
        throw new AssertionError();
    }

    public static void setGravity(@NonNull Dialog dialog, int gravity) {
        Preconditions.checkNotNull(dialog);

        Window window = dialog.getWindow();
        if (window == null) {
            Log.e(TAG, "The Window of dialog is null.");
            return;
        }

        window.setGravity(gravity);
    }

    public static void setWith(@NonNull Dialog dialog, int width) {
        Preconditions.checkNotNull(dialog);

        Window window = dialog.getWindow();
        if (window == null) {
            Log.e(TAG, "The Window of dialog is null.");
            return;
        }

        View decorView = window.getDecorView();
        decorView.setPadding(0, decorView.getTop(), 0, decorView.getBottom());

        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = width;
        window.setAttributes(lp);
    }

    public static void setHeight(@NonNull Dialog dialog, int height) {
        Preconditions.checkNotNull(dialog);

        Window window = dialog.getWindow();
        if (window == null) {
            Log.e(TAG, "The Window of dialog is null.");
            return;
        }

        View decorView = window.getDecorView();
        decorView.setPadding(decorView.getLeft(), 0, decorView.getRight(), 0);

        WindowManager.LayoutParams lp = window.getAttributes();
        lp.height = height;
        window.setAttributes(lp);
    }

    public static void setAnimations(@NonNull Dialog dialog, @StyleRes int styleRes) {
        Preconditions.checkNotNull(dialog);

        Window window = dialog.getWindow();
        if (window == null) {
            Log.e(TAG, "The Window of dialog is null.");
            return;
        }

        window.setWindowAnimations(styleRes);
    }

    public static void setBackgroundDrawable(@NonNull Dialog dialog, @NonNull Drawable drawable) {
        Preconditions.checkNotNull(dialog);
        Preconditions.checkNotNull(drawable);

        Window window = dialog.getWindow();
        if (window == null) {
            Log.e(TAG, "The Window of dialog is null.");
            return;
        }

        window.setBackgroundDrawable(drawable);
    }

    public static void setBackgroundDrawableResource(@NonNull Dialog dialog, @DrawableRes int drawableId) {
        Preconditions.checkNotNull(dialog);

        Window window = dialog.getWindow();
        if (window == null) {
            Log.e(TAG, "The Window of dialog is null.");
            return;
        }

        window.setBackgroundDrawableResource(drawableId);
    }
}
