package accepted.music.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import accepted.music.R;
import accepted.music.util.DialogUtil;

public abstract class BottomDialog extends AppCompatDialogFragment {
    private static final String KEY_KEEP_ON_RESTARTED = "KEEP_ON_RESTARTED";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && !savedInstanceState.getBoolean(KEY_KEEP_ON_RESTARTED, true)) {
            dismiss();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AppCompatDialog dialog = new AppCompatDialog(getContext(), getTheme());

        DialogUtil.setWith(dialog, WindowManager.LayoutParams.MATCH_PARENT);
        DialogUtil.setGravity(dialog, Gravity.BOTTOM);
        DialogUtil.setBackgroundDrawableResource(dialog, R.drawable.bg_bottom_dialog);
        DialogUtil.setAnimations(dialog, R.style.BottomDialogTransition);
        dialog.setCanceledOnTouchOutside(true);

        onInitDialog(dialog);

        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_KEEP_ON_RESTARTED, keepOnRestarted());
    }

    protected boolean keepOnRestarted() {
        return true;
    }

    protected abstract void onInitDialog(AppCompatDialog dialog);
}
