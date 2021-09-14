package accepted.music.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;


public class EditTextEx extends AppCompatEditText {
    private OnBackPressListener mOnBackPressListener;

    public EditTextEx(@NonNull Context context) {
        super(context);
    }

    public EditTextEx(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextEx(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mOnBackPressListener != null) {
            mOnBackPressListener.onBackPressed();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    public void setOnBackPressListener(@Nullable OnBackPressListener listener) {
        mOnBackPressListener = listener;
    }

    public interface OnBackPressListener {
        void onBackPressed();
    }
}
