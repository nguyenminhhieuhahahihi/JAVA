package accepted.player.util;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ProgressClock {
    private static final String TAG = "ProgressClock";

    private final boolean mCountDown;
    private boolean mEnabled;
    private final Callback mCallback;

    private float mProgressSec;
    private int mDurationSec;

    private Disposable mDisposable;

    private float mSpeed = 1.0F;

    public ProgressClock(@NonNull Callback callback) {
        this(false, callback);
    }

    public ProgressClock(boolean countDown, @NonNull Callback callback) {
        Preconditions.checkNotNull(callback);
        mEnabled = true;
        mCountDown = countDown;
        mCallback = callback;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public boolean isCountDown() {
        return mCountDown;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!mEnabled) {
            cancel();
        }
    }

    public void setSpeed(float speed) {
        mSpeed = speed;

        if (speed <= 0) {
            cancelTimer();
        }
    }

    public void start(int progress, long updateTime, int duration) {
        start(progress, updateTime, duration, 1.0F);
    }

    public void start(int progress, long updateTime, int duration, float speed) throws IllegalArgumentException {
        cancelTimer();

        mSpeed = speed;

        if (duration < 1) {
            mCallback.onUpdateProgress(0, 0);
            return;
        }

        if (mSpeed <= 0) {
            return;
        }

        long currentTime = SystemClock.elapsedRealtime();

        if (updateTime > currentTime) {
            updateTime = currentTime;
            throw new IllegalArgumentException("updateTime > currentTime. " +
                    "updateTime=" + updateTime + ", " +
                    "currentTime=" + currentTime);
        }

        long realProgress = (long) (progress + (currentTime - updateTime));

        if (mCountDown) {
            mProgressSec = (int) Math.ceil((duration - realProgress) / 1000.0);
        } else {
            mProgressSec = (int) (realProgress / 1000);
        }
        mDurationSec = duration / 1000;

        if (!mEnabled) {
            mCallback.onUpdateProgress(Math.round(mProgressSec), mDurationSec);
            return;
        }

        if (isTimeout()) {
            notifyTimeout();
            return;
        }

        updateProgress(mProgressSec);

        long delay = 1000 - (realProgress % 1000);
        mDisposable = Observable.interval(delay, 1000, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) {
                        if (mCountDown) {
                            decrease();
                        } else {
                            increase();
                        }
                    }
                });
    }

    private boolean isTimeout() {
        if (mCountDown) {
            return mProgressSec <= 0;
        }

        return mProgressSec >= mDurationSec;
    }

    private void notifyTimeout() {
        if (mCountDown) {
            mCallback.onUpdateProgress(0, mDurationSec);
            return;
        }

        mCallback.onUpdateProgress(mDurationSec, mDurationSec);
    }

    public void cancel() {
        cancelTimer();
    }

    private void cancelTimer() {
        if (mDisposable != null && !mDisposable.isDisposed()) {
            mDisposable.dispose();
            mDisposable = null;
        }
    }

    private void increase() {
        float newProgress = mProgressSec + (1 * mSpeed);

        if (newProgress >= mDurationSec) {
            cancel();
        }

        updateProgress(newProgress);
    }

    private void decrease() {
        float newProgress = mProgressSec - (1 * mSpeed);

        if (newProgress <= 0) {
            cancel();
        }

        updateProgress(newProgress);
    }

    private void updateProgress(float progressSec) {
        mProgressSec = progressSec;
        mCallback.onUpdateProgress(Math.min(Math.round(mProgressSec), mDurationSec), mDurationSec);
    }

    public static String asText(int seconds) {
        if (seconds <= 0) {
            return "00:00";
        }

        int maxSeconds = (99 * 60 * 60) + (59 * 60) + 59;

        if (seconds >= maxSeconds) {
            return "99:59:59";
        }

        int second = seconds % 60;
        int minute = (seconds / 60) % 60;
        int hour = (seconds / 3600);

        if (hour <= 0) {
            return String.format(Locale.ENGLISH, "%02d:%02d", minute, second);
        }

        return String.format(Locale.ENGLISH, "%02d:%02d:%02d", hour, minute, second);
    }

    public interface Callback {
        void onUpdateProgress(int progressSec, int durationSec);
    }
}
