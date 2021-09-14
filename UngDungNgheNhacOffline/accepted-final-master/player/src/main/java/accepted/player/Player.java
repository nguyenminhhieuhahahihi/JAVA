package accepted.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import channel.helper.Channel;
import channel.helper.UseOrdinal;
import accepted.player.audio.MusicItem;
import accepted.player.playlist.PlaylistManager;

@Channel
public interface Player {
    void play();

    void pause();

    void stop();

    void playPause();

    void seekTo(int progress);

    void fastForward();

    void rewind();

    void skipToNext();

    void skipToPrevious();

    void skipToPosition(int position);

    void playPause(int position);

    void setPlayMode(@UseOrdinal PlayMode playMode);

    void setSpeed(float speed);

    interface OnPlaybackStateChangeListener {
        void onPlay(boolean stalled, int playProgress, long playProgressUpdateTime);

        void onPause(int playProgress, long updateTime);

        void onStop();

        void onError(int errorCode, String errorMessage);
    }

    interface OnPrepareListener {
        void onPreparing();

        void onPrepared(int audioSessionId);
    }

    interface OnStalledChangeListener {
        void onStalledChanged(boolean stalled, int playProgress, long updateTime);
    }

    interface OnSeekCompleteListener {
        void onSeekComplete(int progress, long updateTime, boolean stalled);
    }

    interface OnBufferedProgressChangeListener {
        void onBufferedProgressChanged(int bufferedProgress);
    }

    interface OnPlayingMusicItemChangeListener {
        void onPlayingMusicItemChanged(@Nullable MusicItem musicItem, int position, int playProgress);
    }

    interface OnPlaylistChangeListener {
        void onPlaylistChanged(PlaylistManager playlistManager, int position);
    }

    interface OnPlayModeChangeListener {
        void onPlayModeChanged(@UseOrdinal PlayMode playMode);
    }

    interface OnSpeedChangeListener {
        void onSpeedChanged(float speed);
    }

    interface OnRepeatListener {
        @SuppressWarnings("NullableProblems")
        void onRepeat(@NonNull MusicItem musicItem, long repeatTime);
    }
}
