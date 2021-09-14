package accepted.music.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import accepted.music.R;
import accepted.music.dialog.InputDialog;
import accepted.music.store.MusicStore;

public class InputValidator implements InputDialog.Validator {
    private final Context mContext;
    private String mInvalidateHint;

    public InputValidator(Context context) {
        mContext = context;
    }

    @Override
    public boolean isValid(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            mInvalidateHint = mContext.getString(R.string.hint_please_input_music_list_title);
            return false;
        }

        if (MusicStore.getInstance().isNameExists(input)) {
            mInvalidateHint = mContext.getString(R.string.hint_music_list_name_exists);
            return false;
        }

        return true;
    }

    @NonNull
    @Override
    public String getInvalidateHint() {
        return mInvalidateHint;
    }
}
