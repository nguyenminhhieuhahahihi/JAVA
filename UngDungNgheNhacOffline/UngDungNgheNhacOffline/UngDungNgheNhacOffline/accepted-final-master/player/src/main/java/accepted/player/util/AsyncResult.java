package accepted.player.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class AsyncResult<T> {
    @Nullable
    private OnCancelListener mOnCancelListener;

    public abstract void onSuccess(@NonNull T t);

    public abstract void onError(@NonNull Throwable throwable);

    public abstract boolean isCancelled();

    public synchronized void setOnCancelListener(@Nullable OnCancelListener listener) {
        mOnCancelListener = listener;
    }

    protected synchronized void notifyCancelled() {
        if (mOnCancelListener != null) {
            mOnCancelListener.onCancelled();
        }
    }

    public interface OnCancelListener {
        void onCancelled();
    }
}
