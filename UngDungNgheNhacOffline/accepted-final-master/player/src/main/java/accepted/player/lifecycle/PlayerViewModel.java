package accepted.player.lifecycle;

import android.content.Context;
import android.os.SystemClock;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.google.common.base.Preconditions;

import accepted.player.PlayMode;
import accepted.player.PlaybackState;
import accepted.player.Player;
import accepted.player.PlayerClient;
import accepted.player.R;
import accepted.player.SleepTimer;
import accepted.player.audio.MusicItem;
import accepted.player.playlist.Playlist;
import accepted.player.playlist.PlaylistManager;
import accepted.player.util.ProgressClock;
import accepted.player.util.MusicItemUtil;

public class PlayerViewModel extends ViewModel {
    private PlayerClient mPlayerClient;

    private MutableLiveData<String> mTitle;
    private MutableLiveData<String> mArtist;
    private MutableLiveData<String> mAlbum;
    private MutableLiveData<String> mIconUri;
    private MutableLiveData<Integer> mDuration;
    private MutableLiveData<Integer> mPlayProgress;
    private MutableLiveData<Integer> mBufferedProgress;
    private MutableLiveData<Boolean> mSleepTimerStarted;
    private MutableLiveData<Integer> mSleepTimerTime;
    private MutableLiveData<Integer> mSleepTimerProgress;
    private MutableLiveData<Integer> mPlayPosition;
    private MutableLiveData<PlayMode> mPlayMode;
    private MutableLiveData<Float> mSpeed;
    private MutableLiveData<PlaybackState> mPlaybackState;
    private MutableLiveData<Boolean> mStalled;
    private MutableLiveData<Boolean> mConnected;
    private MutableLiveData<Boolean> mPreparing;
    private MutableLiveData<String> mErrorMessage;
    private MutableLiveData<MusicItem> mPlayingMusicItem;
    private MutableLiveData<Boolean> mPlayingNoStalled;

    private Player.OnPlayingMusicItemChangeListener mPlayingMusicItemChangeListener;
    private Player.OnPlaylistChangeListener mPlaylistChangeListener;
    private Player.OnPlayModeChangeListener mPlayModeChangeListener;
    private Player.OnSpeedChangeListener mSpeedChangeListener;
    private PlayerClient.OnPlaybackStateChangeListener mClientPlaybackStateChangeListener;
    private Player.OnBufferedProgressChangeListener mBufferedProgressChangeListener;
    private SleepTimer.OnStateChangeListener mSleepTimerStateChangeListener;
    private Player.OnSeekCompleteListener mSeekCompleteListener;
    private Player.OnStalledChangeListener mStalledChangeListener;
    private Player.OnPrepareListener mPrepareListener;
    private PlayerClient.OnConnectStateChangeListener mConnectStateChangeListener;
    private Player.OnRepeatListener mRepeatListener;

    private String mDefaultTitle;
    private String mDefaultArtist;
    private String mDefaultAlbum;

    private ProgressClock mProgressClock;
    private ProgressClock mSleepTimerProgressClock;

    private boolean mInitialized;
    private boolean mCleared;
    private boolean mAutoDisconnect;

    public void init(@NonNull Context context, @NonNull PlayerClient playerClient) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerClient);

        init(context, playerClient, true);
    }

    public void init(@NonNull Context context, @NonNull PlayerClient playerClient, boolean enableProgressClock) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerClient);

        init(playerClient,
                context.getString(R.string.accepted_music_item_unknown_title),
                context.getString(R.string.accepted_music_item_unknown_artist),
                context.getString(R.string.accepted_music_item_unknown_album),
                enableProgressClock);
    }

    public void init(@NonNull PlayerClient playerClient,
                     @NonNull String defaultTitle,
                     @NonNull String defaultArtist,
                     @NonNull String defaultAlbum) {
        Preconditions.checkNotNull(playerClient);
        Preconditions.checkNotNull(defaultTitle);
        Preconditions.checkNotNull(defaultArtist);
        Preconditions.checkNotNull(defaultAlbum);

        init(playerClient, defaultTitle, defaultArtist, defaultAlbum, true);
    }

    public void init(@NonNull PlayerClient playerClient,
                     @NonNull String defaultTitle,
                     @NonNull String defaultArtist,
                     @NonNull String defaultAlbum,
                     boolean enableProgressClock) {
        Preconditions.checkNotNull(playerClient);
        Preconditions.checkNotNull(defaultTitle);
        Preconditions.checkNotNull(defaultArtist);
        Preconditions.checkNotNull(defaultAlbum);

        if (mInitialized) {
            throw new IllegalArgumentException("PlayerViewModel is initialized, please do not repeat initialization.");
        }

        if (mCleared) {
            throw new IllegalStateException("PlayerViewModel is cleared.");
        }

        mPlayerClient = playerClient;
        mDefaultTitle = defaultTitle;
        mDefaultArtist = defaultArtist;
        mDefaultAlbum = defaultAlbum;

        initAllLiveData();
        initAllListener();
        initAllProgressClock(enableProgressClock);

        addAllListener();

        mInitialized = true;
        onInitialized();
    }

    protected void onInitialized() {
    }

    public void setAutoDisconnect(boolean autoDisconnect) {
        mAutoDisconnect = autoDisconnect;
    }

    private void initAllListener() {
        mPlayingMusicItemChangeListener = new Player.OnPlayingMusicItemChangeListener() {
            @Override
            public void onPlayingMusicItemChanged(@Nullable MusicItem musicItem, int position, int playProgress) {
                mProgressClock.cancel();
                mPlayPosition.setValue(position);
                mPlayingMusicItem.setValue(musicItem);

                if (musicItem == null) {
                    mTitle.setValue(mDefaultTitle);
                    mArtist.setValue(mDefaultArtist);
                    mAlbum.setValue(mDefaultAlbum);
                    mIconUri.setValue("");
                    mDuration.setValue(0);
                    mPlayProgress.setValue(0);
                    return;
                }

                mTitle.setValue(MusicItemUtil.getTitle(musicItem, mDefaultTitle));
                mArtist.setValue(MusicItemUtil.getArtist(musicItem, mDefaultArtist));
                mAlbum.setValue(MusicItemUtil.getAlbum(musicItem, mDefaultAlbum));

                mIconUri.setValue(musicItem.getIconUri());
                mDuration.setValue(getDurationSec());
                mPlayProgress.setValue(playProgress / 1000);
            }
        };

        mPlaylistChangeListener = new Player.OnPlaylistChangeListener() {
            @Override
            public void onPlaylistChanged(PlaylistManager playlistManager, int position) {
                mPlayPosition.setValue(position);
            }
        };

        mPlayModeChangeListener = new Player.OnPlayModeChangeListener() {
            @Override
            public void onPlayModeChanged(PlayMode playMode) {
                mPlayMode.setValue(playMode);
            }
        };

        mSpeedChangeListener = new Player.OnSpeedChangeListener() {
            @Override
            public void onSpeedChanged(float speed) {
                mSpeed.setValue(speed);
                mProgressClock.setSpeed(speed);
            }
        };

        mClientPlaybackStateChangeListener = new PlayerClient.OnPlaybackStateChangeListener() {
            @Override
            public void onPlaybackStateChanged(PlaybackState playbackState, boolean stalled) {
                if (playbackState == PlaybackState.ERROR) {
                    mErrorMessage.setValue(mPlayerClient.getErrorMessage());
                } else {
                    mErrorMessage.setValue("");
                }

                mPlaybackState.setValue(playbackState);
                mPlayingNoStalled.setValue(playbackState == PlaybackState.PLAYING && !stalled);

                switch (playbackState) {
                    case PLAYING:
                        if (stalled) {
                            return;
                        }
                        mProgressClock.start(mPlayerClient.getPlayProgress(),
                                mPlayerClient.getPlayProgressUpdateTime(),
                                mPlayerClient.getPlayingMusicItemDuration(),
                                mPlayerClient.getSpeed());
                        break;
                    case STOPPED:
                        mPreparing.setValue(false);
                        mPlayProgress.setValue(0);
                    case PAUSED:
                    case ERROR:
                        mPreparing.setValue(false);
                        mProgressClock.cancel();
                        break;
                }
            }
        };

        mBufferedProgressChangeListener = new Player.OnBufferedProgressChangeListener() {
            @Override
            public void onBufferedProgressChanged(int bufferedProgress) {
                mBufferedProgress.setValue(getBufferedProgressSec());
            }
        };

        mSleepTimerStateChangeListener = new SleepTimer.OnStateChangeListener() {
            @Override
            public void onTimerStart(long time, long startTime, SleepTimer.TimeoutAction action) {
                mSleepTimerStarted.setValue(true);
                mSleepTimerTime.setValue((int) (time / 1000));
                mSleepTimerProgressClock.start(0, startTime, (int) time);
            }

            @Override
            public void onTimerEnd() {
                mSleepTimerStarted.setValue(false);
                mSleepTimerProgressClock.cancel();
                mSleepTimerTime.setValue(0);
                mSleepTimerProgress.setValue(0);
            }
        };

        mSeekCompleteListener = new Player.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(int progress, long updateTime, boolean stalled) {
                mPlayProgress.setValue(progress / 1000);

                if (PlaybackState.PLAYING == mPlaybackState.getValue() && !stalled) {
                    mProgressClock.start(progress,
                            updateTime,
                            mPlayerClient.getPlayingMusicItemDuration(),
                            mPlayerClient.getSpeed());
                }
            }
        };

        mStalledChangeListener = new Player.OnStalledChangeListener() {
            @Override
            public void onStalledChanged(boolean stalled, int playProgress, long updateTime) {
                mStalled.setValue(stalled);
                mPlayingNoStalled.setValue(mPlayerClient.isPlaying() && !stalled);
                if (stalled) {
                    mProgressClock.cancel();
                    return;
                }

                if (mPlayerClient.isPlaying()) {
                    mProgressClock.start(playProgress,
                            updateTime,
                            mPlayerClient.getPlayingMusicItemDuration(),
                            mPlayerClient.getSpeed());
                }
            }
        };

        mPrepareListener = new Player.OnPrepareListener() {
            @Override
            public void onPreparing() {
                mPreparing.setValue(true);
            }

            @Override
            public void onPrepared(int audioSessionId) {
                mPreparing.setValue(false);
            }
        };

        mConnectStateChangeListener = new PlayerClient.OnConnectStateChangeListener() {
            @Override
            public void onConnectStateChanged(boolean connected) {
                mConnected.setValue(connected);

                if (isInitialized() && !connected) {
                    mProgressClock.cancel();
                    mSleepTimerProgressClock.cancel();
                }
            }
        };

        mRepeatListener = new Player.OnRepeatListener() {
            @Override
            public void onRepeat(@NonNull MusicItem musicItem, long repeatTime) {
                mProgressClock.start(0,
                        repeatTime,
                        musicItem.getDuration(),
                        mPlayerClient.getSpeed());
            }
        };
    }

    private void initAllProgressClock(boolean enable) {
        mProgressClock = new ProgressClock(new ProgressClock.Callback() {
            @Override
            public void onUpdateProgress(int progressSec, int durationSec) {
                mPlayProgress.setValue(progressSec);
            }
        });
        mProgressClock.setEnabled(enable);

        mSleepTimerProgressClock = new ProgressClock(true, new ProgressClock.Callback() {
            @Override
            public void onUpdateProgress(int progressSec, int durationSec) {
                mSleepTimerProgress.setValue(progressSec);
            }
        });
    }

    private void addAllListener() {
        mPlayerClient.addOnPlayingMusicItemChangeListener(mPlayingMusicItemChangeListener);
        mPlayerClient.addOnPlaylistChangeListener(mPlaylistChangeListener);
        mPlayerClient.addOnPlayModeChangeListener(mPlayModeChangeListener);
        mPlayerClient.addOnSpeedChangeListener(mSpeedChangeListener);
        mPlayerClient.addOnPlaybackStateChangeListener(mClientPlaybackStateChangeListener);
        mPlayerClient.addOnBufferedProgressChangeListener(mBufferedProgressChangeListener);
        mPlayerClient.addOnSleepTimerStateChangeListener(mSleepTimerStateChangeListener);
        mPlayerClient.addOnSeekCompleteListener(mSeekCompleteListener);
        mPlayerClient.addOnStalledChangeListener(mStalledChangeListener);
        mPlayerClient.addOnPrepareListener(mPrepareListener);
        mPlayerClient.addOnConnectStateChangeListener(mConnectStateChangeListener);
        mPlayerClient.addOnRepeatListener(mRepeatListener);
    }

    private void removeAllListener() {
        mPlayerClient.removeOnPlayingMusicItemChangeListener(mPlayingMusicItemChangeListener);
        mPlayerClient.removeOnPlaylistChangeListener(mPlaylistChangeListener);
        mPlayerClient.removeOnPlayModeChangeListener(mPlayModeChangeListener);
        mPlayerClient.removeOnSpeedChangeListener(mSpeedChangeListener);
        mPlayerClient.removeOnPlaybackStateChangeListener(mClientPlaybackStateChangeListener);
        mPlayerClient.removeOnBufferedProgressChangeListener(mBufferedProgressChangeListener);
        mPlayerClient.removeOnSleepTimerStateChangeListener(mSleepTimerStateChangeListener);
        mPlayerClient.removeOnSeekCompleteListener(mSeekCompleteListener);
        mPlayerClient.removeOnStalledChangeListener(mStalledChangeListener);
        mPlayerClient.removeOnPrepareListener(mPrepareListener);
        mPlayerClient.removeOnConnectStateChangeListener(mConnectStateChangeListener);
        mPlayerClient.removeOnRepeatListener(mRepeatListener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        mCleared = true;

        if (!mInitialized) {
            return;
        }

        mProgressClock.cancel();
        mSleepTimerProgressClock.cancel();
        removeAllListener();

        if (mAutoDisconnect) {
            mPlayerClient.disconnect();
        }

        mPlayerClient = null;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    @NonNull
    public PlayerClient getPlayerClient() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPlayerClient;
    }

    @NonNull
    public LiveData<String> getTitle() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mTitle;
    }

    @NonNull
    public LiveData<String> getArtist() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mArtist;
    }

    @NonNull
    public LiveData<String> getAlbum() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mAlbum;
    }

    @NonNull
    public LiveData<String> getIconUri() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mIconUri;
    }

    @NonNull
    public LiveData<Integer> getDuration() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mDuration;
    }

    @NonNull
    public MutableLiveData<Integer> getPlayProgress() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPlayProgress;
    }

    @NonNull
    public LiveData<Integer> getBufferedProgress() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mBufferedProgress;
    }

    @NonNull
    public LiveData<Integer> getPlayPosition() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPlayPosition;
    }

    @NonNull
    public LiveData<PlayMode> getPlayMode() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPlayMode;
    }

    public LiveData<Float> getSpeed() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mSpeed;
    }

    @NonNull
    public LiveData<PlaybackState> getPlaybackState() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPlaybackState;
    }

    @NonNull
    public LiveData<Boolean> getStalled() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mStalled;
    }

    public LiveData<Boolean> getConnected() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mConnected;
    }

    public LiveData<Boolean> getPreparing() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPreparing;
    }

    @NonNull
    public LiveData<Boolean> isError() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return Transformations.map(mPlaybackState, new Function<PlaybackState, Boolean>() {
            @Override
            public Boolean apply(PlaybackState input) {
                return input == PlaybackState.ERROR;
            }
        });
    }

    @NonNull
    public LiveData<String> getErrorMessage() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mErrorMessage;
    }

    @NonNull
    public LiveData<MusicItem> getPlayingMusicItem() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mPlayingMusicItem;
    }

    @NonNull
    public LiveData<Boolean> getPlayingNoStalled() {
        return mPlayingNoStalled;
    }

    @NonNull
    public LiveData<String> getTextDuration() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return Transformations.map(mDuration, new Function<Integer, String>() {
            @Override
            public String apply(Integer input) {
                return ProgressClock.asText(input);
            }
        });
    }

    @NonNull
    public LiveData<String> getTextPlayProgress() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return Transformations.map(mPlayProgress, new Function<Integer, String>() {
            @Override
            public String apply(Integer input) {
                return ProgressClock.asText(input);
            }
        });
    }

    @NonNull
    public LiveData<Boolean> getSleepTimerStarted() {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mSleepTimerStarted;
    }

    @NonNull
    public LiveData<Integer> getSleepTimerTime() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mSleepTimerTime;
    }

    @NonNull
    public LiveData<String> getTextSleepTimerTime() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return Transformations.map(mSleepTimerTime, new Function<Integer, String>() {
            @Override
            public String apply(Integer input) {
                return ProgressClock.asText(input);
            }
        });
    }

    @NonNull
    public LiveData<Integer> getSleepTimerProgress() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return mSleepTimerProgress;
    }

    @NonNull
    public LiveData<String> getTextSleepTimerProgress() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("PlayerViewModel not initialized yet.");
        }

        return Transformations.map(mSleepTimerProgress, new Function<Integer, String>() {
            @Override
            public String apply(Integer input) {
                return ProgressClock.asText(input);
            }
        });
    }

    public void setPlaylist(Playlist playlist) {
        if (isInitialized()) {
            mPlayerClient.setPlaylist(playlist);
        }
    }

    public void setPlaylist(Playlist playlist, boolean play) {
        if (isInitialized()) {
            mPlayerClient.setPlaylist(playlist, play);
        }
    }

    public void setPlaylist(Playlist playlist, int position, boolean play) {
        if (isInitialized()) {
            mPlayerClient.setPlaylist(playlist, position, play);
        }
    }

    public void play() {
        if (isInitialized()) {
            mPlayerClient.play();
        }
    }

    public void pause() {
        if (isInitialized()) {
            mPlayerClient.pause();
        }
    }

    public void playPause() {
        if (isInitialized()) {
            mPlayerClient.playPause();
        }
    }

    public void playPause(int position) {
        if (isInitialized()) {
            mPlayerClient.playPause(position);
        }
    }

    public void stop() {
        if (isInitialized()) {
            mPlayerClient.stop();
        }
    }

    public void skipToPrevious() {
        if (isInitialized()) {
            mPlayerClient.skipToPrevious();
        }
    }

    public void skipToNext() {
        if (isInitialized()) {
            mPlayerClient.skipToNext();
        }
    }

    public void skipToPosition(int position) {
        if (isInitialized()) {
            mPlayerClient.skipToPosition(position);
        }
    }

    public void fastForward() {
        if (isInitialized()) {
            mPlayerClient.fastForward();
        }
    }

    public void rewind() {
        if (isInitialized()) {
            mPlayerClient.rewind();
        }
    }

    public void setNextPlay(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        if (isInitialized()) {
            mPlayerClient.setNextPlay(musicItem);
        }
    }

    public void setPlayMode(@NonNull PlayMode playMode) {
        Preconditions.checkNotNull(playMode);
        if (isInitialized()) {
            mPlayerClient.setPlayMode(playMode);
        }
    }

    public void setSpeed(float speed) {
        if (isInitialized()) {
            mPlayerClient.setSpeed(speed);
            mProgressClock.setSpeed(speed);
        }
    }

    public void seekTo(int progress) {
        if (isInitialized()) {
            mProgressClock.cancel();
            mPlayerClient.seekTo(progress);
        }
    }

    public void cancelProgressClock() {
        if (isInitialized()) {
            mProgressClock.cancel();
        }
    }

    public void startSleepTimer(long time) throws IllegalArgumentException {
        if (isInitialized()) {
            mPlayerClient.startSleepTimer(time);
        }
    }

    public void startSleepTimer(long time, SleepTimer.TimeoutAction action) throws IllegalArgumentException {
        if (isInitialized()) {
            mPlayerClient.startSleepTimer(time, action);
        }
    }

    public void cancelSleepTimer() {
        if (isInitialized()) {
            mPlayerClient.cancelSleepTimer();
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        cancelProgressClock();
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        if (!isInitialized()) {
            return;
        }

        if (mPlayerClient.isForbidSeek()) {
            restorePlayProgress();
            return;
        }

        seekTo(seekBar.getProgress() * 1000);
    }

    private void restorePlayProgress() {
        if (!mPlayerClient.isPlaying() || mPlayerClient.isStalled()) {
            mPlayProgress.setValue(mPlayerClient.getPlayProgress() / 1000);
            return;
        }

        mProgressClock.start(
                mPlayerClient.getPlayProgress(),
                mPlayerClient.getPlayProgressUpdateTime(),
                mPlayerClient.getPlayingMusicItemDuration(),
                mPlayerClient.getSpeed());
    }

    private void initAllLiveData() {
        mTitle = new MutableLiveData<>(mDefaultTitle);
        mArtist = new MutableLiveData<>(mDefaultArtist);
        mAlbum = new MutableLiveData<>(mDefaultAlbum);
        mIconUri = new MutableLiveData<>(getIconUri(mPlayerClient));
        mDuration = new MutableLiveData<>(getDurationSec());
        mPlayProgress = new MutableLiveData<>(getPlayProgressSec());
        mBufferedProgress = new MutableLiveData<>(getBufferedProgressSec());
        mSleepTimerStarted = new MutableLiveData<>(mPlayerClient.isSleepTimerStarted());
        mSleepTimerTime = new MutableLiveData<>((int) (mPlayerClient.getSleepTimerTime() / 1000));
        mSleepTimerProgress = new MutableLiveData<>((int) (mPlayerClient.getSleepTimerElapsedTime() / 1000));
        mPlayPosition = new MutableLiveData<>(mPlayerClient.getPlayPosition());
        mPlayMode = new MutableLiveData<>(mPlayerClient.getPlayMode());
        mSpeed = new MutableLiveData<>(mPlayerClient.getSpeed());
        mPlaybackState = new MutableLiveData<>(mPlayerClient.getPlaybackState());
        mStalled = new MutableLiveData<>(mPlayerClient.isStalled());
        mConnected = new MutableLiveData<>(mPlayerClient.isConnected());
        mPreparing = new MutableLiveData<>(mPlayerClient.isPreparing());
        mErrorMessage = new MutableLiveData<>(mPlayerClient.getErrorMessage());
        mPlayingMusicItem = new MutableLiveData<>(mPlayerClient.getPlayingMusicItem());
        mPlayingNoStalled = new MutableLiveData<>(mPlayerClient.isPlaying() && !mPlayerClient.isStalled());
    }

    private int getDurationSec() {
        return mPlayerClient.getPlayingMusicItemDuration() / 1000;
    }

    private int getPlayProgressSec() {
        if (mPlayerClient.isPlaying()) {
            long realProgress = mPlayerClient.getPlayProgress() + (SystemClock.elapsedRealtime() - mPlayerClient.getPlayProgressUpdateTime());
            return (int) (realProgress / 1000);
        }

        return mPlayerClient.getPlayProgress();
    }

    private int getBufferedProgressSec() {
        return mPlayerClient.getBufferedProgress() / 1000;
    }

    private String getIconUri(PlayerClient playerClient) {
        MusicItem musicItem = playerClient.getPlayingMusicItem();
        if (musicItem == null) {
            return "";
        }

        return musicItem.getIconUri();
    }
}
