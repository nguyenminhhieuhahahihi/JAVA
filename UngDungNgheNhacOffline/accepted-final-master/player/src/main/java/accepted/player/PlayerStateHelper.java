package accepted.player;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import accepted.player.appwidget.AppWidgetPlayerState;
import accepted.player.audio.ErrorCode;
import accepted.player.audio.MusicItem;

class PlayerStateHelper {
    private final PlayerState mPlayerState;

    private final boolean mRunOnService;
    private final Context mContext;
    private final Class<? extends PlayerService> mPlayerService;

    public PlayerStateHelper(PlayerState playerState) {
        mPlayerState = playerState;

        mRunOnService = false;
        mContext = null;
        mPlayerService = null;
    }

    public PlayerStateHelper(PlayerState playerState,
                             Context context,
                             Class<? extends PlayerService> playerService) {
        mPlayerState = playerState;

        mRunOnService = true;
        mContext = context;
        mPlayerService = playerService;
    }

    void updatePlayProgress(int progress, long updateTime) {
        mPlayerState.setPlayProgress(progress);
        mPlayerState.setPlayProgressUpdateTime(updateTime);
    }

    private void updateAppWidgetPlayerState() {
        if (mContext == null || mPlayerService == null) {
            return;
        }

        AppWidgetPlayerState playerState = new AppWidgetPlayerState(
                mPlayerState.getPlaybackState(),
                mPlayerState.getMusicItem(),
                mPlayerState.getPlayMode(),
                mPlayerState.getSpeed(),
                mPlayerState.getPlayProgress(),
                mPlayerState.getPlayProgressUpdateTime(),
                mPlayerState.isPreparing(),
                mPlayerState.isPrepared(),
                mPlayerState.isStalled(),
                mPlayerState.getErrorMessage()
        );

        AppWidgetPlayerState.updatePlayerState(mContext, mPlayerService, playerState);
    }

    public void onPreparing() {
        mPlayerState.setPreparing(true);
        mPlayerState.setPrepared(false);

        if (mPlayerState.getPlaybackState() == PlaybackState.ERROR) {
            mPlayerState.setPlaybackState(PlaybackState.NONE);
            mPlayerState.setErrorCode(ErrorCode.NO_ERROR);
            mPlayerState.setErrorMessage("");
        }

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onPrepared(int audioSessionId) {
        mPlayerState.setPreparing(false);
        mPlayerState.setPrepared(true);
        mPlayerState.setAudioSessionId(audioSessionId);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void clearPrepareState() {
        mPlayerState.setPreparing(false);
        mPlayerState.setPrepared(false);
    }

    public void onPlay(boolean stalled, int progress, long updateTime) {
        mPlayerState.setStalled(stalled);
        mPlayerState.setPlaybackState(PlaybackState.PLAYING);
        updatePlayProgress(progress, updateTime);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onPaused(int playProgress, long updateTime) {
        mPlayerState.setPlaybackState(PlaybackState.PAUSED);
        mPlayerState.setPlayProgress(playProgress);
        mPlayerState.setPlayProgressUpdateTime(updateTime);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onStopped() {
        mPlayerState.setPlaybackState(PlaybackState.STOPPED);
        long updateTime = SystemClock.elapsedRealtime();
        updatePlayProgress(0, updateTime);
        clearPrepareState();

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onStalled(boolean stalled, int playProgress, long updateTime) {
        mPlayerState.setStalled(stalled);
        updatePlayProgress(playProgress, updateTime);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onRepeat(long repeatTime) {
        updatePlayProgress(0, repeatTime);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onError(int errorCode, String errorMessage) {
        mPlayerState.setPlaybackState(PlaybackState.ERROR);
        mPlayerState.setErrorCode(errorCode);
        mPlayerState.setErrorMessage(errorMessage);
        clearPrepareState();

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onBufferedChanged(int bufferedProgress) {
        mPlayerState.setBufferedProgress(bufferedProgress);
    }

    public void onPlayingMusicItemChanged(@Nullable MusicItem musicItem, int position, int playProgress) {
        mPlayerState.setMusicItem(musicItem);
        mPlayerState.setPlayPosition(position);
        mPlayerState.setBufferedProgress(0);

        long updateTime = SystemClock.elapsedRealtime();
        updatePlayProgress(playProgress, updateTime);

        if (mPlayerState.getPlaybackState() == PlaybackState.ERROR) {
            mPlayerState.setPlaybackState(PlaybackState.NONE);
            mPlayerState.setErrorCode(ErrorCode.NO_ERROR);
            mPlayerState.setErrorMessage("");
        }

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onSeekComplete(int playProgress, long updateTime, boolean stalled) {
        updatePlayProgress(playProgress, updateTime);
        mPlayerState.setStalled(stalled);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onPlaylistChanged(int position) {
        mPlayerState.setPlayPosition(position);
    }

    public void onPlayModeChanged(PlayMode playMode) {
        mPlayerState.setPlayMode(playMode);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onSpeedChanged(float speed) {
        mPlayerState.setSpeed(speed);

        if (mRunOnService) {
            updateAppWidgetPlayerState();
        }
    }

    public void onSleepTimerStart(long time, long startTime, SleepTimer.TimeoutAction action) {
        mPlayerState.setSleepTimerStarted(true);
        mPlayerState.setSleepTimerTime(time);
        mPlayerState.setSleepTimerStartTime(startTime);
        mPlayerState.setTimeoutAction(action);
    }

    public void onSleepTimerEnd() {
        mPlayerState.setSleepTimerStarted(false);
        mPlayerState.setSleepTimerTime(0);
        mPlayerState.setSleepTimerStartTime(0);
    }
}
