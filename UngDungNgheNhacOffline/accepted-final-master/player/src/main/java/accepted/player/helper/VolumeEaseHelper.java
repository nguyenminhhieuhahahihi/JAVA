package accepted.player.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import accepted.player.audio.MusicPlayer;

public class VolumeEaseHelper {
    private final MusicPlayer mMusicPlayer;
    private final Callback mCallback;

    private ObjectAnimator mStartVolumeAnimator;
    private ObjectAnimator mPauseVolumeAnimator;
    private ObjectAnimator mDismissQuietVolumeAnimator;

    private boolean mQuiet;

    public VolumeEaseHelper(@NonNull MusicPlayer musicPlayer, @NonNull Callback callback) {
        Preconditions.checkNotNull(musicPlayer);
        Preconditions.checkNotNull(callback);

        mMusicPlayer = musicPlayer;
        mCallback = callback;

        initVolumeAnimator();
    }

    public void start() {
        cancel();
        setVolume(0.0F);
        mCallback.start();
        mStartVolumeAnimator.start();
    }

    public void pause() {
        cancel();

        if (mQuiet) {
            mCallback.pause();
            return;
        }

        mPauseVolumeAnimator.start();
    }

    public void quiet() {
        mQuiet = true;
        mMusicPlayer.setVolume(0.2F, 0.2F);
    }

    public void dismissQuiet() {
        mQuiet = false;
        if (mPauseVolumeAnimator.isStarted()) {
            return;
        }

        cancel();
        mDismissQuietVolumeAnimator.start();
    }

    private void initVolumeAnimator() {
        long volumeAnimDuration = 400L;

        mStartVolumeAnimator = ObjectAnimator.ofFloat(this, "volume", 0.0F, 1.0F);
        mStartVolumeAnimator.setDuration(1000L);
        mStartVolumeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                setVolume(1.0F);
            }
        });

        mPauseVolumeAnimator = ObjectAnimator.ofFloat(this, "volume", 1.0F, 0.0F);
        mPauseVolumeAnimator.setDuration(volumeAnimDuration);
        mPauseVolumeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                mCallback.pause();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCallback.pause();
            }
        });

        mDismissQuietVolumeAnimator = ObjectAnimator.ofFloat(this, "volume", 0.2F, 1.0F);
        mDismissQuietVolumeAnimator.setDuration(volumeAnimDuration);
        mDismissQuietVolumeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                setVolume(1.0F);
            }
        });
    }

    public void setVolume(float volume) {
        mMusicPlayer.setVolume(volume, volume);
    }

    public void cancel() {
        if (mStartVolumeAnimator.isStarted()) {
            mStartVolumeAnimator.cancel();
        }

        if (mPauseVolumeAnimator.isStarted()) {
            mPauseVolumeAnimator.cancel();
        }

        if (mDismissQuietVolumeAnimator.isStarted()) {
            mDismissQuietVolumeAnimator.cancel();
        }
    }

    public interface Callback {
        void start();

        void pause();
    }
}
