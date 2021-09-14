package accepted.music.dialog;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;

import com.google.common.base.Preconditions;

import accepted.music.R;

public class InputDialog extends BottomDialog {
    private String mTitle;
    private String mText;
    private String mHint;
    private Validator mValidator;
    private OnInputConfirmListener mInputConfirmListener;

    @Override
    protected void onInitDialog(AppCompatDialog dialog) {
        dialog.setContentView(R.layout.dialog_input);

        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        EditText etInput = dialog.findViewById(R.id.etInput);
        Button btnNegative = dialog.findViewById(R.id.btnNegative);
        Button btnPositive = dialog.findViewById(R.id.btnPositive);

        assert tvDialogTitle != null;
        assert etInput != null;
        assert btnNegative != null;
        assert btnPositive != null;

        tvDialogTitle.setText(mTitle);

        if (mText.length() > 0) {
            etInput.setText(mText);
            etInput.setSelection(0, mText.length());
        }

        etInput.setHint(mHint);
        etInput.requestFocus();

        btnNegative.setOnClickListener(view -> dismiss());

        btnPositive.setOnClickListener(view -> {
            String input = etInput.getText().toString();
            if (mValidator.isValid(input)) {
                dismiss();
                mInputConfirmListener.onInputConfirmed(input);
                return;
            }

            Toast.makeText(getContext(), mValidator.getInvalidateHint(), Toast.LENGTH_SHORT).show();
        });

        showSoftInput(etInput);
    }

    @Override
    protected boolean keepOnRestarted() {
        return false;
    }

    private void showSoftInput(EditText editText) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        editText.postDelayed(() -> {
            editText.requestFocus();
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    public static class Builder {
        private final Context mContext;

        private String mTitle;
        private String mText;
        private String mHint;
        private Validator mValidator;
        private OnInputConfirmListener mInputConfirmListener;

        public Builder(Context context) {
            mContext = context;
            mTitle = context.getString(R.string.input_default_title);
            mHint = context.getString(R.string.input_default_hint);
            mText = "";
        }

        public Builder setTitle(@StringRes int resId) {
            mTitle = mContext.getString(resId);
            return this;
        }

        public Builder setTitle(@NonNull String title) {
            Preconditions.checkNotNull(title);

            mTitle = title;
            return this;
        }

        public Builder setText(@NonNull String text) {
            Preconditions.checkNotNull(text);

            mText = text;
            return this;
        }

        public Builder setHint(@StringRes int resId) {
            mHint = mContext.getString(resId);
            return this;
        }

        public Builder setHint(@NonNull String hint) {
            Preconditions.checkNotNull(hint);

            mHint = hint;
            return this;
        }


        public Builder setOnInputConfirmListener(@NonNull Validator validator, @NonNull OnInputConfirmListener listener) {
            Preconditions.checkNotNull(validator);
            Preconditions.checkNotNull(listener);

            mValidator = validator;
            mInputConfirmListener = listener;
            return this;
        }

        public InputDialog build() {
            InputDialog dialog = new InputDialog();

            dialog.mTitle = mTitle;
            dialog.mText = mText;
            dialog.mHint = mHint;
            dialog.mValidator = mValidator;
            dialog.mInputConfirmListener = mInputConfirmListener;

            return dialog;
        }
    }


    public interface Validator {

        boolean isValid(@Nullable String input);

        @NonNull
        String getInvalidateHint();
    }

    public interface OnInputConfirmListener {
        void onInputConfirmed(@Nullable String input);
    }
}
