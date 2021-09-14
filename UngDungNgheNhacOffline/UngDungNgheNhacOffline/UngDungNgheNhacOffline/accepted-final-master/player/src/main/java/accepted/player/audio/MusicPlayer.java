package accepted.player.audio;

import androidx.annotation.Nullable;

public interface MusicPlayer {

    void prepare() throws Exception;

    void setLooping(boolean looping);

    boolean isLooping();

    boolean isStalled();

    boolean isPlaying();

    int getDuration();

    int getProgress();

    void start();

    void pause();

    void stop();

    void seekTo(int pos);

    void setVolume(float leftVolume, float rightVolume);

    void setSpeed(float speed);

    void quiet();

    void dismissQuiet();

    void release();

    boolean isInvalid();

    int getAudioSessionId();

    void setOnPreparedListener(@Nullable OnPreparedListener listener);

    void setOnCompletionListener(@Nullable OnCompletionListener listener);

    void setOnRepeatListener(@Nullable OnRepeatListener listener);

    void setOnSeekCompleteListener(@Nullable OnSeekCompleteListener listener);

    void setOnStalledListener(@Nullable OnStalledListener listener);

    void setOnBufferingUpdateListener(@Nullable OnBufferingUpdateListener listener);

    void setOnErrorListener(@Nullable OnErrorListener listener);

    interface OnPreparedListener {
        void onPrepared(MusicPlayer mp);
    }

    interface OnCompletionListener {
        void onCompletion(MusicPlayer mp);
    }

    interface OnRepeatListener {
        void onRepeat(MusicPlayer mp);
    }

    interface OnSeekCompleteListener {
        void onSeekComplete(MusicPlayer mp);
    }

    interface OnStalledListener {
        void onStalled(boolean stalled);
    }

    interface OnBufferingUpdateListener {
        void onBufferingUpdate(MusicPlayer mp, int buffered, boolean isPercent);
    }

    interface OnErrorListener {
        void onError(MusicPlayer mp, int errorCode);
    }
}
