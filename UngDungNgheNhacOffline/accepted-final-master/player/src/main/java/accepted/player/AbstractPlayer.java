package accepted.player;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import media.helper.AudioFocusHelper;
import media.helper.BecomeNoiseHelper;
import accepted.player.audio.MusicItem;
import accepted.player.audio.MusicPlayer;
import accepted.player.effect.AudioEffectManager;
import accepted.player.helper.PhoneCallStateHelper;
import accepted.player.playlist.Playlist;
import accepted.player.playlist.PlaylistEditor;
import accepted.player.playlist.PlaylistManager;
import accepted.player.audio.ErrorCode;
import accepted.player.helper.NetworkHelper;
import accepted.player.util.AsyncResult;

abstract class AbstractPlayer implements Player, PlaylistEditor {
    private static final String TAG = "AbstractPlayer";
    private static final int FORWARD_STEP = 15_000;

    private final Context mApplicationContext;
    private final PlayerConfig mPlayerConfig;
    private final PlayerState mPlayerState;
    private final PlayerStateHelper mPlayerStateHelper;
    @Nullable
    private PlayerStateListener mPlayerStateListener;

    private MusicPlayer.OnPreparedListener mPreparedListener;
    private MusicPlayer.OnCompletionListener mCompletionListener;
    private MusicPlayer.OnRepeatListener mRepeatListener;
    private MusicPlayer.OnSeekCompleteListener mSeekCompleteListener;
    private MusicPlayer.OnStalledListener mStalledListener;
    private MusicPlayer.OnBufferingUpdateListener mBufferingUpdateListener;
    private MusicPlayer.OnErrorListener mErrorListener;

    private AudioFocusHelper mAudioFocusHelper;
    private PhoneCallStateHelper mPhoneCallStateHelper;
    private BecomeNoiseHelper mBecomeNoiseHelper;
    private NetworkHelper mNetworkHelper;

    @Nullable
    private MusicPlayer mMusicPlayer;

    private boolean mLoadingPlaylist;

    private boolean mPlayOnPrepared;
    private boolean mPlayOnSeekComplete;
    private Runnable mPreparedAction;
    private Runnable mSeekCompleteAction;
    private Runnable mPlaylistLoadedAction;

    private final PlaylistManagerImp mPlaylistManager;
    private Playlist mPlaylist;

    private Random mRandom;
    private Disposable mRetrieveUriDisposable;

    private boolean mReleased;

    private Disposable mRecordProgressDisposable;
    private Disposable mCheckCachedDisposable;

    private MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mPlaybackStateBuilder;
    private PlaybackStateCompat.Builder mForbidSeekPlaybackStateBuilder;
    private MediaMetadataCompat.Builder mMediaMetadataBuilder;

    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    private boolean mConfirmNextPlay;
    private boolean mResumePlay;

    private final OnStateChangeListener mOnStateChangeListener;

    @Nullable
    private AudioEffectManager mAudioEffectManager;

    public AbstractPlayer(@NonNull Context context,
                          @NonNull PlayerConfig playerConfig,
                          @NonNull PlayerState playerState,
                          @NonNull PlaylistManagerImp playlistManager,
                          @NonNull Class<? extends PlayerService> playerService,
                          @NonNull OnStateChangeListener listener) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerConfig);
        Preconditions.checkNotNull(playerState);
        Preconditions.checkNotNull(playlistManager);
        Preconditions.checkNotNull(playerService);
        Preconditions.checkNotNull(listener);

        mApplicationContext = context.getApplicationContext();
        mPlayerConfig = playerConfig;
        mPlayerState = playerState;
        mPlayerStateHelper = new PlayerStateHelper(mPlayerState, mApplicationContext, playerService);
        mPlaylistManager = playlistManager;
        mOnStateChangeListener = listener;

        initAllListener();
        initAllHelper();
        initWakeLock();

        mNetworkHelper.subscribeNetworkState();
        reloadPlaylist();
    }

    void setAudioEffectManager(@Nullable AudioEffectManager audioEffectManager) {
        mAudioEffectManager = audioEffectManager;
    }

    protected abstract void isCached(@NonNull MusicItem musicItem, @NonNull SoundQuality soundQuality, @NonNull AsyncResult<Boolean> result);

    @NonNull
    protected abstract MusicPlayer onCreateMusicPlayer(@NonNull Context context, @NonNull MusicItem musicItem, @NonNull Uri uri);

    protected abstract void retrieveMusicItemUri(@NonNull MusicItem musicItem, @NonNull SoundQuality soundQuality, @NonNull AsyncResult<Uri> result) throws Exception;

    @Nullable
    protected abstract AudioManager.OnAudioFocusChangeListener onCreateAudioFocusChangeListener();

    public void release() {
        mReleased = true;
        disposeRetrieveUri();
        releaseMusicPlayer();
        releaseWakeLock();

        mAudioFocusHelper.abandonAudioFocus();
        mPhoneCallStateHelper.unregisterCallStateListener();
        mBecomeNoiseHelper.unregisterBecomeNoiseReceiver();
        mNetworkHelper.unsubscribeNetworkState();

        mAudioFocusHelper = null;
        mBecomeNoiseHelper = null;
        mNetworkHelper = null;

        mPreparedAction = null;
        mSeekCompleteAction = null;
        mPlaylistLoadedAction = null;
    }

    @Nullable
    protected final MusicItem getMusicItem() {
        return mPlayerState.getMusicItem();
    }

    private void prepareMusicPlayer(boolean playOnPrepared, @Nullable Runnable preparedAction) {
        releaseMusicPlayer();
        disposeRetrieveUri();

        MusicItem musicItem = mPlayerState.getMusicItem();
        if (musicItem == null) {
            return;
        }

        if (mPlayerConfig.isOnlyWifiNetwork() && !isWiFiNetwork()) {
            notifyError(ErrorCode.ONLY_WIFI_NETWORK, ErrorCode.getErrorMessage(mApplicationContext, ErrorCode.ONLY_WIFI_NETWORK));
            return;
        }

        mPlayOnPrepared = playOnPrepared;
        mRetrieveUriDisposable = getMusicItemUri(musicItem, mPlayerConfig.getSoundQuality())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(prepare(musicItem, preparedAction), notifyGetUrlFailed());
    }

    private void disposeRetrieveUri() {
        if (mRetrieveUriDisposable != null && !mRetrieveUriDisposable.isDisposed()) {
            mRetrieveUriDisposable.dispose();
        }
    }

    private Single<Uri> getMusicItemUri(@NonNull final MusicItem musicItem, @NonNull final SoundQuality soundQuality) {
        return Single.create(new SingleOnSubscribe<Uri>() {
            @Override
            public void subscribe(@NonNull final SingleEmitter<Uri> emitter) throws Exception {
                retrieveMusicItemUri(musicItem, soundQuality, new AsyncResult<Uri>() {
                    @Override
                    public void onSuccess(@NonNull Uri uri) {
                        emitter.onSuccess(uri);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        emitter.onError(throwable);
                    }

                    @Override
                    public boolean isCancelled() {
                        return emitter.isDisposed();
                    }

                    @Override
                    public synchronized void setOnCancelListener(@Nullable OnCancelListener listener) {
                        super.setOnCancelListener(listener);

                        emitter.setCancellable(new Cancellable() {
                            @Override
                            public void cancel() {
                                notifyCancelled();
                            }
                        });
                    }
                });
            }
        });
    }

    private Consumer<Uri> prepare(@NonNull final MusicItem musicItem, @Nullable final Runnable preparedAction) {
        return new Consumer<Uri>() {
            @Override
            public void accept(Uri uri) {
                mMusicPlayer = onCreateMusicPlayer(mApplicationContext, musicItem, uri);
                attachListeners(mMusicPlayer);

                mPreparedAction = preparedAction;
                notifyPreparing();

                try {
                    if (!mMusicPlayer.isInvalid()) {
                        mMusicPlayer.prepare();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    notifyError(ErrorCode.DATA_LOAD_FAILED, ErrorCode.getErrorMessage(mApplicationContext, ErrorCode.DATA_LOAD_FAILED));
                }
            }
        };
    }

    private Consumer<Throwable> notifyGetUrlFailed() {
        return new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                throwable.printStackTrace();
                notifyError(ErrorCode.GET_URL_FAILED, ErrorCode.getErrorMessage(mApplicationContext, ErrorCode.GET_URL_FAILED));
            }
        };
    }

    private boolean isWiFiNetwork() {
        return mNetworkHelper.isWifiNetwork();
    }

    private void initAllListener() {
        mPreparedListener = new MusicPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MusicPlayer mp) {
                if (mReleased) {
                    return;
                }

                mp.setLooping(isLooping());

                if (mPlayerConfig.isAudioEffectEnabled() && mAudioEffectManager != null) {
                    mAudioEffectManager.attachAudioEffect(mp.getAudioSessionId());
                }

                notifyPrepared(mp.getAudioSessionId());

                if (!mPlayerState.isForbidSeek() && mPlayerState.getPlayProgress() > 0) {
                    mPlayOnSeekComplete = mPlayOnPrepared;
                    mPlayOnPrepared = false;
                    seekTo(mPlayerState.getPlayProgress(), mPreparedAction);
                    mPreparedAction = null;
                    return;
                }

                if (mPlayOnPrepared) {
                    mPlayOnPrepared = false;
                    play();
                } else if (mPreparedAction == null) {
                    notifyPaused();
                }

                if (mPreparedAction != null) {
                    mPreparedAction.run();
                    mPreparedAction = null;
                }
            }
        };

        mCompletionListener = new MusicPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MusicPlayer mp) {
                if (mPlayerState.getPlayMode() == PlayMode.LOOP) {
                    return;
                }

                if (mPlayerState.getPlayMode() == PlayMode.SINGLE_ONCE) {
                    notifyPlayOnceComplete();
                    return;
                }

                skipToNext();
            }
        };

        mRepeatListener = new MusicPlayer.OnRepeatListener() {
            @Override
            public void onRepeat(MusicPlayer mp) {
                notifyRepeat(SystemClock.elapsedRealtime());
            }
        };

        mSeekCompleteListener = new MusicPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MusicPlayer mp) {
                if (mReleased) {
                    return;
                }

                notifySeekComplete(mp.getProgress(), SystemClock.elapsedRealtime(), mp.isStalled());

                if (mPlayOnSeekComplete) {
                    mPlayOnSeekComplete = false;
                    play();
                }

                if (mSeekCompleteAction != null) {
                    mSeekCompleteAction.run();
                    mSeekCompleteAction = null;
                }
            }
        };

        mStalledListener = new MusicPlayer.OnStalledListener() {
            @Override
            public void onStalled(boolean stalled) {
                notifyStalled(stalled);
            }
        };

        mBufferingUpdateListener = new MusicPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MusicPlayer mp, int buffered, boolean isPercent) {
                notifyBufferedChanged(buffered, isPercent);
            }
        };

        mErrorListener = new MusicPlayer.OnErrorListener() {
            @Override
            public void onError(MusicPlayer mp, int errorCode) {
                Log.e("MusicPlayer", "errorCode:" + errorCode);

                notifyError(errorCode, ErrorCode.getErrorMessage(mApplicationContext, errorCode));
            }
        };
    }

    private void initAllHelper() {
        initAudioFocusHelper();

        mPhoneCallStateHelper = new PhoneCallStateHelper(mApplicationContext, new PhoneCallStateHelper.OnStateChangeListener() {
            private boolean mResumePlay;

            @Override
            public void onIDLE() {
                if (mResumePlay) {
                    mResumePlay = false;
                    play();
                }
            }

            @Override
            public void onRinging() {
                if (mResumePlay) {
                    return;
                }

                mResumePlay = isPlayingState();
                pause();
            }

            @Override
            public void onOffHook() {
                if (mResumePlay) {
                    return;
                }

                mResumePlay = isPlayingState();
                pause();
            }
        });

        mBecomeNoiseHelper = new BecomeNoiseHelper(mApplicationContext, new BecomeNoiseHelper.OnBecomeNoiseListener() {
            @Override
            public void onBecomeNoise() {
                pause();
            }
        });

        mNetworkHelper = NetworkHelper.newInstance(mApplicationContext, new NetworkHelper.OnNetworkStateChangeListener() {
            @Override
            public void onNetworkStateChanged(boolean connected, boolean wifiNetwork) {
                if (!isPrepared() || !connected) {
                    return;
                }

                checkNetworkType(mPlayerConfig.isOnlyWifiNetwork(), wifiNetwork);
            }
        });
    }

    private void initAudioFocusHelper() {
        AudioManager.OnAudioFocusChangeListener listener = onCreateAudioFocusChangeListener();
        if (listener != null) {
            mAudioFocusHelper = new AudioFocusHelper(mApplicationContext, listener);
            return;
        }

        mAudioFocusHelper = new AudioFocusHelper(mApplicationContext, new AudioFocusHelper.OnAudioFocusChangeListener() {
            @Override
            public void onLoss() {
                mResumePlay = false;
                pause();
            }

            @Override
            public void onLossTransient() {
                boolean playing = isPlayingState();
                pause();
                mResumePlay = playing;
            }

            @Override
            public void onLossTransientCanDuck() {
                mResumePlay = isPlaying();
                if (isPlaying() && mResumePlay) {
                    assert mMusicPlayer != null;
                    mMusicPlayer.quiet();
                }
            }

            @Override
            public void onGain(boolean lossTransient, boolean lossTransientCanDuck) {
                if (!mResumePlay) {
                    return;
                }

                if (lossTransient) {
                    play();
                    return;
                }

                if (mMusicPlayer != null && lossTransientCanDuck && isPlaying()) {
                    mMusicPlayer.dismissQuiet();
                }
            }
        });
    }

    private void notifyPlayOnceComplete() {
        cancelRecordProgress();
        releaseWakeLock();

        int playProgress = mPlayerState.getPlayProgress();
        long updateTime = mPlayerState.getPlayProgressUpdateTime();

        if (isPrepared()) {
            assert mMusicPlayer != null;
            playProgress = mMusicPlayer.getProgress();
            updateTime = SystemClock.elapsedRealtime();

            releaseMusicPlayer();
        }

        mPlayerStateHelper.onPaused(playProgress, updateTime);
        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PAUSED));

        mPlayerState.setPlayProgress(0);
        mPlayerState.setPlayProgressUpdateTime(updateTime);

        mBecomeNoiseHelper.unregisterBecomeNoiseReceiver();

        mOnStateChangeListener.onPaused();

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPause(playProgress, updateTime);
        }
    }

    public final void setMediaSession(@NonNull MediaSessionCompat mediaSession) {
        Preconditions.checkNotNull(mediaSession);

        initMediaMetadataBuilder();
        initPlaybackStateBuilder();

        mMediaSession = mediaSession;

        if (getMusicItem() != null) {
            mPlayerState.setPlaybackState(PlaybackState.PAUSED);
            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PAUSED));
        } else {
            mPlayerState.setPlaybackState(PlaybackState.NONE);
            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_NONE));
        }

        mMediaSession.setMetadata(buildMediaMetadata());
    }

    private void initPlaybackStateBuilder() {
        mPlaybackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM |
                        PlaybackStateCompat.ACTION_SET_REPEAT_MODE |
                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE |
                        PlaybackStateCompat.ACTION_FAST_FORWARD |
                        PlaybackStateCompat.ACTION_REWIND |
                        PlaybackStateCompat.ACTION_SEEK_TO);

        mForbidSeekPlaybackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM |
                        PlaybackStateCompat.ACTION_SET_REPEAT_MODE |
                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE);
    }

    private void initMediaMetadataBuilder() {
        mMediaMetadataBuilder = new MediaMetadataCompat.Builder();
    }

    private void initWakeLock() {
        PowerManager pm = (PowerManager) mApplicationContext.getSystemService(Context.POWER_SERVICE);
        WifiManager wm = (WifiManager) mApplicationContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        String tag = "accepted.player:AbstractPlayer";

        if (pm != null) {
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
            mWakeLock.setReferenceCounted(false);
        }

        if (wm != null) {
            mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag);
            mWifiLock.setReferenceCounted(false);
        }
    }

    private void requireWakeLock() {
        if (wakeLockPermissionDenied()) {
            Log.w(TAG, "need permission: 'android.permission.WAKE_LOCK'");
            return;
        }

        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire(getMusicItemDuration() + 5_000);
        }

        if (mWifiLock != null && !mWifiLock.isHeld()) {
            mWifiLock.acquire();
        }
    }

    private boolean wakeLockPermissionDenied() {
        return PackageManager.PERMISSION_DENIED ==
                ContextCompat.checkSelfPermission(mApplicationContext, Manifest.permission.WAKE_LOCK);
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    private PlaybackStateCompat buildPlaybackState(int state) {
        if (mPlayerState.isForbidSeek()) {
            return mForbidSeekPlaybackStateBuilder.setState(state, mPlayerState.getPlayProgress(), 1.0F, mPlayerState.getPlayProgressUpdateTime())
                    .build();
        }

        return mPlaybackStateBuilder.setState(state, mPlayerState.getPlayProgress(), 1.0F, mPlayerState.getPlayProgressUpdateTime())
                .build();
    }

    private PlaybackStateCompat buildErrorState(String errorMessage) {
        if (mPlayerState.isForbidSeek()) {
            return mForbidSeekPlaybackStateBuilder.setState(PlaybackStateCompat.STATE_ERROR, mPlayerState.getPlayProgress(), 1.0F, mPlayerState.getPlayProgressUpdateTime())
                    .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, errorMessage)
                    .build();
        }

        return mPlaybackStateBuilder.setState(PlaybackStateCompat.STATE_ERROR, mPlayerState.getPlayProgress(), 1.0F, mPlayerState.getPlayProgressUpdateTime())
                .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, errorMessage)
                .build();
    }

    private MediaMetadataCompat buildMediaMetadata() {
        MusicItem musicItem = getMusicItem();

        if (musicItem != null) {
            return mMediaMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, musicItem.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, musicItem.getArtist())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, musicItem.getAlbum())
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, musicItem.getIconUri())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, musicItem.getDuration())
                    .build();
        }

        return new MediaMetadataCompat.Builder().build();
    }

    private void attachListeners(MusicPlayer musicPlayer) {
        musicPlayer.setOnPreparedListener(mPreparedListener);
        musicPlayer.setOnCompletionListener(mCompletionListener);
        musicPlayer.setOnRepeatListener(mRepeatListener);
        musicPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
        musicPlayer.setOnStalledListener(mStalledListener);
        musicPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
        musicPlayer.setOnErrorListener(mErrorListener);
    }

    private void releaseMusicPlayer() {
        cancelRecordProgress();
        if (mMusicPlayer != null) {
            mMusicPlayer.release();
            mMusicPlayer = null;
        }

        mPlayerStateHelper.clearPrepareState();
        mPlayOnPrepared = false;
        mPlayOnSeekComplete = false;

        mPreparedAction = null;
        mSeekCompleteAction = null;

        if (mPlayerState.isStalled()) {
            notifyStalled(false);
        }
    }

    public final boolean isPrepared() {
        return mMusicPlayer != null && mPlayerState.isPrepared();
    }

    public final boolean isPreparing() {
        return mPlayerState.isPreparing();
    }

    private boolean isPlaying() {
        if (isPrepared()) {
            assert mMusicPlayer != null;
            return mMusicPlayer.isPlaying();
        }

        return false;
    }

    private boolean isPlayingState() {
        return getPlaybackState() == PlaybackState.PLAYING;
    }

    @NonNull
    public final PlaybackState getPlaybackState() {
        return mPlayerState.getPlaybackState();
    }

    @NonNull
    public final PlayMode getPlayMode() {
        return mPlayerState.getPlayMode();
    }

    public final boolean isStalled() {
        return mPlayerState.isStalled();
    }

    public final int getAudioSessionId() {
        if (mMusicPlayer != null && isPrepared()) {
            return mMusicPlayer.getAudioSessionId();
        }

        return 0;
    }

    public final void setPlayerStateListener(@Nullable PlayerStateListener listener) {
        mPlayerStateListener = listener;
    }

    private void notifyPreparing() {
        requireWakeLock();
        mPlayerStateHelper.onPreparing();

        mOnStateChangeListener.onPreparing();

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPreparing();
        }
    }

    private void notifyPrepared(int audioSessionId) {
        mPlayerStateHelper.onPrepared(audioSessionId);

        mOnStateChangeListener.onPrepared(audioSessionId);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPrepared(audioSessionId);
        }
    }

    private void notifyPlaying(boolean stalled, int progress, long updateTime) {
        mPlayerStateHelper.onPlay(stalled, progress, updateTime);

        if (!stalled) {
            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PLAYING));
        }

        startRecordProgress();

        mBecomeNoiseHelper.registerBecomeNoiseReceiver();

        mOnStateChangeListener.onPlaying(progress, updateTime);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPlay(stalled, progress, updateTime);
        }
    }

    private void notifyPaused() {
        cancelRecordProgress();
        releaseWakeLock();

        int playProgress = mPlayerState.getPlayProgress();
        long updateTime = mPlayerState.getPlayProgressUpdateTime();

        if (isPrepared()) {
            assert mMusicPlayer != null;
            playProgress = mMusicPlayer.getProgress();
            updateTime = SystemClock.elapsedRealtime();
        }

        mPlayerStateHelper.onPaused(playProgress, updateTime);

        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PAUSED));

        mBecomeNoiseHelper.unregisterBecomeNoiseReceiver();

        mOnStateChangeListener.onPaused();

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPause(playProgress, updateTime);
        }
    }

    private void notifyStopped() {
        cancelRecordProgress();
        releaseWakeLock();

        mPlayerStateHelper.onStopped();
        mMediaSession.setActive(false);
        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_STOPPED));

        mAudioFocusHelper.abandonAudioFocus();
        mPhoneCallStateHelper.unregisterCallStateListener();
        mBecomeNoiseHelper.unregisterBecomeNoiseReceiver();

        mOnStateChangeListener.onStopped();

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onStop();
        }
    }

    private void notifyStalled(boolean stalled) {
        int playProgress = mPlayerState.getPlayProgress();
        long updateTime = mPlayerState.getPlayProgressUpdateTime();

        if (isPlaying()) {
            assert mMusicPlayer != null;
            playProgress = mMusicPlayer.getProgress();
            updateTime = SystemClock.elapsedRealtime();
        }

        mPlayerStateHelper.onStalled(stalled, playProgress, updateTime);
        updateMediaSessionPlaybackState(stalled);
        mOnStateChangeListener.onStalledChanged(stalled);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onStalledChanged(stalled, playProgress, updateTime);
        }
    }

    private void notifyRepeat(long repeatTime) {
        mPlayerStateHelper.onRepeat(repeatTime);
        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PLAYING));

        MusicItem musicItem = getMusicItem();
        if (mPlayerStateListener != null && musicItem != null) {
            mPlayerStateListener.onRepeat(musicItem, repeatTime);
        }
    }

    private void updateMediaSessionPlaybackState(boolean stalled) {
        if (stalled) {
            cancelRecordProgress();
            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_BUFFERING));
            return;
        }

        if (mPlayOnPrepared || mPlayOnSeekComplete) {
            return;
        }

        switch (getPlaybackState()) {
            case PLAYING:
                startRecordProgress();
                mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PLAYING));
                break;
            case PAUSED:
                mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PAUSED));
                break;
        }
    }

    private void notifyError(int errorCode, String errorMessage) {
        releaseMusicPlayer();
        releaseWakeLock();

        mPlayerStateHelper.onError(errorCode, errorMessage);
        mMediaSession.setPlaybackState(buildErrorState(errorMessage));

        mAudioFocusHelper.abandonAudioFocus();
        mPhoneCallStateHelper.unregisterCallStateListener();
        mBecomeNoiseHelper.unregisterBecomeNoiseReceiver();

        mOnStateChangeListener.onError(errorCode, errorMessage);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onError(errorCode, errorMessage);
        }
    }

    private int getMusicItemDuration() {
        MusicItem musicItem = getMusicItem();
        if (musicItem == null) {
            return 0;
        }

        return musicItem.getDuration();
    }

    private void notifyBufferedChanged(int buffered, boolean isPercent) {
        int bufferedProgress = buffered;

        if (isPercent) {
            bufferedProgress = (int) ((buffered / 100.0) * getMusicItemDuration());
        }

        mPlayerStateHelper.onBufferedChanged(bufferedProgress);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onBufferedProgressChanged(bufferedProgress);
        }
    }

    private void notifyPlayingMusicItemChanged(@Nullable MusicItem musicItem, int position, boolean play) {
        releaseMusicPlayer();

        mPlayerStateHelper.onPlayingMusicItemChanged(musicItem, position, 0);

        if (musicItem == null) {
            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_NONE));
        }
        mMediaSession.setMetadata(buildMediaMetadata());

        mOnStateChangeListener.onPlayingMusicItemChanged(musicItem);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPlayingMusicItemChanged(musicItem, position, mPlayerState.getPlayProgress());
        }

        notifyBufferedChanged(0, false);

        if (play) {
            play();
        }
    }

    private void notifySeekComplete(int playProgress, long updateTime, boolean stalled) {
        mPlayerStateHelper.onSeekComplete(playProgress, updateTime, stalled);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onSeekComplete(playProgress, updateTime, stalled);
        }

        if (stalled || mPlayOnSeekComplete) {
            return;
        }

        if (isPlaying()) {
            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_PLAYING));
        } else {
            notifyPaused();
        }
    }

    @Override
    public void play() {
        if (getMusicItem() == null || isPlaying()) {
            return;
        }

        if (isPreparing()) {
            mPreparedAction = new Runnable() {
                @Override
                public void run() {
                    play();
                }
            };
            return;
        }

        if (requestAudioFocusFailed()) {
            return;
        }

        mMediaSession.setActive(true);
        if (isPrepared()) {
            assert mMusicPlayer != null;
            mMusicPlayer.setSpeed(mPlayerState.getSpeed());
            mMusicPlayer.start();
            notifyPlaying(mMusicPlayer.isStalled(), mMusicPlayer.getProgress(), SystemClock.elapsedRealtime());
            return;
        }

        prepareMusicPlayer(true, null);
    }

    @Override
    public void pause() {
        mResumePlay = false;

        if (isPreparing()) {
            mPlayOnPrepared = false;
            mPlayOnSeekComplete = false;
            return;
        }

        if (!isPlaying()) {
            return;
        }

        assert mMusicPlayer != null;
        mMusicPlayer.pause();

        notifyPaused();
    }

    @Override
    public void stop() {
        if (getPlaybackState() == PlaybackState.STOPPED) {
            return;
        }

        if (isPrepared()) {
            assert mMusicPlayer != null;
            mMusicPlayer.stop();
        }

        releaseMusicPlayer();
        notifyStopped();
    }

    @Override
    public void playPause() {
        if (isPreparing() && mPlayOnPrepared) {
            pause();
            return;
        }

        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    private void seekTo(final int progress, final Runnable seekCompleteAction) {
        if (mPlayerState.isForbidSeek()) {
            return;
        }

        if (isPreparing()) {
            mPlayOnSeekComplete = mPlayOnPrepared;
            mPlayOnPrepared = false;
            mPreparedAction = new Runnable() {
                @Override
                public void run() {
                    seekTo(progress, seekCompleteAction);
                }
            };
            return;
        }

        if (isPrepared()) {
            assert mMusicPlayer != null;
            mSeekCompleteAction = seekCompleteAction;
            mMusicPlayer.seekTo(progress);
            return;
        }

        if (getMusicItem() != null) {
            notifySeekComplete(Math.min(progress, getMusicItemDuration()), SystemClock.elapsedRealtime(), false);
        }
    }

    @Override
    public void seekTo(final int progress) {
        seekTo(progress, null);
    }

    @Override
    public void fastForward() {
        if (mPlayerState.isForbidSeek()) {
            return;
        }

        if (isPreparing()) {
            mPreparedAction = new Runnable() {
                @Override
                public void run() {
                    fastForward();
                }
            };
            return;
        }

        if (isPrepared()) {
            assert mMusicPlayer != null;
            int progress = Math.min(mMusicPlayer.getDuration(),
                    mMusicPlayer.getProgress() + FORWARD_STEP);

            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_FAST_FORWARDING));
            seekTo(progress);
        }
    }

    @Override
    public void rewind() {
        if (mPlayerState.isForbidSeek()) {
            return;
        }

        if (isPreparing()) {
            mPreparedAction = new Runnable() {
                @Override
                public void run() {
                    rewind();
                }
            };
            return;
        }

        if (isPrepared()) {
            assert mMusicPlayer != null;
            int progress = Math.max(0, mMusicPlayer.getProgress() - FORWARD_STEP);

            mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_REWINDING));
            seekTo(progress);
        }
    }

    public final void notifySoundQualityChanged() {
        if (isPrepared()) {
            assert mMusicPlayer != null;
            boolean playing = mMusicPlayer.isPlaying();
            final int position = mMusicPlayer.getProgress();

            releaseMusicPlayer();
            prepareMusicPlayer(playing, new Runnable() {
                @Override
                public void run() {
                    if (position > 0) {
                        seekTo(position);
                    }
                }
            });
        }
    }

    public final void notifyAudioEffectEnableChanged() {
        if (!isPrepared() || mAudioEffectManager == null) {
            return;
        }

        if (mPlayerConfig.isAudioEffectEnabled()) {
            mAudioEffectManager.attachAudioEffect(getAudioSessionId());
            return;
        }

        mAudioEffectManager.detachAudioEffect();
    }

    public final void notifyOnlyWifiNetworkChanged() {
        if (!isPrepared()) {
            return;
        }

        checkNetworkType(mPlayerConfig.isOnlyWifiNetwork(), mNetworkHelper.isWifiNetwork());
    }

    public void notifyIgnoreAudioFocusChanged() {
        if (requestAudioFocusFailed() && (isPlayingState() || mPlayOnPrepared)) {
            pause();
        }
    }

    private boolean requestAudioFocusFailed() {
        if (mPlayerConfig.isIgnoreAudioFocus()) {
            mAudioFocusHelper.abandonAudioFocus();
            mPhoneCallStateHelper.registerCallStateListener();
            return !mPhoneCallStateHelper.isCallIDLE();
        }

        return AudioManager.AUDIOFOCUS_REQUEST_FAILED ==
                mAudioFocusHelper.requestAudioFocus(AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void checkNetworkType(boolean onlyWifiNetwork, boolean isWifiNetwork) {
        disposeCheckCached();

        if (!mNetworkHelper.networkAvailable()) {
            return;
        }

        mCheckCachedDisposable = playingMusicIsCached()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(checkNetworkTypeConsumer(onlyWifiNetwork, isWifiNetwork));
    }

    private void disposeCheckCached() {
        if (mCheckCachedDisposable != null) {
            mCheckCachedDisposable.dispose();
            mCheckCachedDisposable = null;
        }
    }

    private Single<Boolean> playingMusicIsCached() {
        return Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(@NonNull final SingleEmitter<Boolean> emitter) {
                MusicItem musicItem = getMusicItem();
                if (musicItem == null) {
                    emitter.onSuccess(false);
                    return;
                }

                isCached(musicItem, mPlayerConfig.getSoundQuality(), new AsyncResult<Boolean>() {
                    @Override
                    public void onSuccess(@NonNull Boolean aBoolean) {
                        emitter.onSuccess(aBoolean);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        throwable.printStackTrace();
                        emitter.onSuccess(false);
                    }

                    @Override
                    public boolean isCancelled() {
                        return emitter.isDisposed();
                    }

                    @Override
                    public synchronized void setOnCancelListener(@Nullable OnCancelListener listener) {
                        super.setOnCancelListener(listener);

                        emitter.setCancellable(new Cancellable() {
                            @Override
                            public void cancel() {
                                notifyCancelled();
                            }
                        });
                    }
                });
            }
        });
    }

    private Consumer<Boolean> checkNetworkTypeConsumer(final boolean onlyWifiNetwork, final boolean isWifiNetwork) {
        return new Consumer<Boolean>() {
            @Override
            public void accept(Boolean cached) {
                if (onlyWifiNetwork && !isWifiNetwork && !cached) {
                    pause();
                    releaseMusicPlayer();
                    notifyError(ErrorCode.ONLY_WIFI_NETWORK,
                            ErrorCode.getErrorMessage(mApplicationContext, ErrorCode.ONLY_WIFI_NETWORK));
                }
            }
        };
    }

    private void reloadPlaylist() {
        mLoadingPlaylist = true;
        mPlaylistManager.getPlaylist(new PlaylistManager.Callback() {
            @Override
            public void onFinished(@NonNull final Playlist playlist) {
                if (mReleased) {
                    return;
                }

                mPlaylist = playlist;
                mLoadingPlaylist = false;

                if (mPlaylistLoadedAction != null) {
                    mPlaylistLoadedAction.run();
                    mPlaylistLoadedAction = null;
                }
            }
        });
    }

    private int getRandomPosition(int exclude) {
        if (mPlaylist == null || getPlaylistSize() < 2) {
            return 0;
        }

        if (mRandom == null) {
            mRandom = new Random();
        }

        int position = mRandom.nextInt(getPlaylistSize());

        if (position != exclude) {
            return position;
        }

        return getRandomPosition(exclude);
    }

    private void notifyPlayModeChanged(PlayMode playMode) {
        mPlayerStateHelper.onPlayModeChanged(playMode);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPlayModeChanged(playMode);
        }

        mOnStateChangeListener.onPlayModeChanged(playMode);
    }

    private void notifySpeedChanged(float speed) {
        mPlayerStateHelper.onSpeedChanged(speed);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onSpeedChanged(speed);
        }
    }

    private void notifyPlaylistChanged(int position) {
        mPlayerStateHelper.onPlaylistChanged(position);

        if (mPlayerStateListener != null) {
            mPlayerStateListener.onPlaylistChanged(null, position);
        }
    }

    protected final int getPlaylistSize() {
        return mPlaylistManager.getPlaylistSize();
    }

    @Nullable
    public final Bundle getPlaylistExtra() {
        if (mPlaylist == null) {
            return null;
        }

        return mPlaylist.getExtra();
    }

    private boolean isLooping() {
        return mPlayerState.getPlayMode() == PlayMode.LOOP;
    }

    @Override
    public void skipToNext() {
        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    skipToNext();
                }
            };
            return;
        }

        if (getPlaylistSize() < 1) {
            return;
        }

        int position = getNextPosition(mPlayerState.getPlayPosition());

        notifyPlayingMusicItemChanged(mPlaylist.get(position), position, true);
        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT));
    }

    @Override
    public void skipToPosition(int position) {
        if (position == mPlayerState.getPlayPosition()) {
            return;
        }

        playPause(position);
    }

    private int getNextPosition(int currentPosition) {
        PlayMode playMode = mPlayerState.getPlayMode();
        if (playMode == PlayMode.LOOP){
            return currentPosition;
        }
        if (mConfirmNextPlay || playMode == PlayMode.PLAYLIST_LOOP || playMode == PlayMode.SINGLE_ONCE) {
            mConfirmNextPlay = false;
            int position = currentPosition + 1;
            if (position >= getPlaylistSize()) {
                return 0;
            }
            return position;
        }

        return getRandomPosition(currentPosition);
    }

    @Override
    public void skipToPrevious() {
        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    skipToPrevious();
                }
            };
            return;
        }

        if (getPlaylistSize() < 1) {
            return;
        }

        int position = getPreviousPosition(mPlayerState.getPlayPosition());

        notifyPlayingMusicItemChanged(mPlaylist.get(position), position, true);
        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS));
    }

    private int getPreviousPosition(int currentPosition) {
        int position = 0;

        switch (mPlayerState.getPlayMode()) {
            case PLAYLIST_LOOP:
                position = currentPosition - 1;
                if (position < 0) {
                    return getPlaylistSize() - 1;
                }
                break;
            case LOOP:
                position = currentPosition;
                break;
            case SHUFFLE:
                position = getRandomPosition(currentPosition);
                break;
        }

        return position;
    }

    @Override
    public void playPause(final int position) {
        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    playPause(position);
                }
            };
            return;
        }

        if (position == mPlayerState.getPlayPosition()) {
            playPause();
            return;
        }

        mMediaSession.setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM));
        notifyPlayingMusicItemChanged(mPlaylist.get(position), position, true);
    }

    @Override
    public void setPlayMode(@NonNull PlayMode playMode) {
        Preconditions.checkNotNull(playMode);
        if (playMode == mPlayerState.getPlayMode()) {
            return;
        }

        if (isPrepared()) {
            assert mMusicPlayer != null;
            mMusicPlayer.setLooping(playMode == PlayMode.LOOP);
        }

        notifyPlayModeChanged(playMode);
    }

    @Override
    public void setSpeed(float speed) {
        if (speed < 0.1F) {
            speed = 0.1F;
        }

        if (speed > 10.0F) {
            speed = 10.0F;
        }

        if (speed == mPlayerState.getSpeed()) {
            return;
        }

        if (isPrepared()) {
            assert mMusicPlayer != null;
            mMusicPlayer.setSpeed(speed);
        }

        notifySpeedChanged(speed);
    }

    @Override
    public void setPlaylist(Playlist playlist, final int position, final boolean play) {
        final MusicItem musicItem = playlist.get(position);
        updatePlaylist(playlist, playlist.getAllMusicItem(), new Runnable() {
            @Override
            public void run() {
                stop();
                notifyPlaylistChanged(position);
                notifyPlayingMusicItemChanged(musicItem, position, play);
            }
        });
    }

    private void onMusicItemMoved(int fromPosition, int toPosition) {
        int playPosition = mPlayerState.getPlayPosition();
        if (notInRegion(playPosition, fromPosition, toPosition)) {
            notifyPlaylistChanged(playPosition);
            return;
        }

        if (fromPosition < playPosition) {
            playPosition -= 1;
        } else if (fromPosition == playPosition) {
            playPosition = toPosition;
        } else {
            playPosition += 1;
        }

        mPlayerState.setPlayPosition(playPosition);
    }

    private void onMusicItemInserted(int position) {
        int playPosition = mPlayerState.getPlayPosition();

        if (position <= playPosition) {
            playPosition += 1;
        }

        mPlayerState.setPlayPosition(playPosition);
    }

    private void onMusicItemRemoved(int removePosition, int playPosition) {
        if (removePosition < playPosition) {
            playPosition -= 1;
        } else if (removePosition == playPosition) {
            playPosition = getNextPosition(playPosition - 1);
        }

        mPlayerState.setPlayPosition(playPosition);
    }

    private boolean notInRegion(int position, int fromPosition, int toPosition) {
        return position > Math.max(fromPosition, toPosition) || position < Math.min(fromPosition, toPosition);
    }

    private void startRecordProgress() {
        cancelRecordProgress();

        if (mPlayerState.isForbidSeek()) {
            return;
        }

        mRecordProgressDisposable = Observable.interval(3, 3, TimeUnit.SECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) {
                        if (isPrepared()) {
                            assert mMusicPlayer != null;
                            mPlayerStateHelper.updatePlayProgress(mMusicPlayer.getProgress(), SystemClock.elapsedRealtime());
                        }
                    }
                });
    }

    private void cancelRecordProgress() {
        if (mRecordProgressDisposable == null || mRecordProgressDisposable.isDisposed()) {
            return;
        }

        mRecordProgressDisposable.dispose();
    }

    @Override
    public void insertMusicItem(final int position, @NonNull final MusicItem musicItem) {
        if (!mPlaylistManager.isPlaylistEditable()) {
            return;
        }

        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    insertMusicItem(position, musicItem);
                }
            };
            return;
        }

        List<MusicItem> musicItems = mPlaylist.getAllMusicItem();
        int index = musicItems.indexOf(musicItem);
        if (index > -1) {
            moveMusicItem(index, Math.min(position, getPlaylistSize() - 1));
            return;
        }

        musicItems.add(position, musicItem);

        onMusicItemInserted(position);
        updatePlaylist(mPlaylist, musicItems, new Runnable() {
            @Override
            public void run() {
                notifyPlaylistChanged(mPlayerState.getPlayPosition());
            }
        });
    }

    @Override
    public void appendMusicItem(@NonNull MusicItem musicItem) {
        if (!mPlaylistManager.isPlaylistEditable()) {
            return;
        }

        insertMusicItem(getPlaylistSize(), musicItem);
    }

    @Override
    public void moveMusicItem(final int fromPosition, final int toPosition) {
        if (!mPlaylistManager.isPlaylistEditable()) {
            return;
        }

        if (fromPosition == toPosition) {
            return;
        }

        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    moveMusicItem(fromPosition, toPosition);
                }
            };
            return;
        }

        int size = mPlaylist.size();
        if (fromPosition < 0 || fromPosition >= size) {
            throw new IndexOutOfBoundsException("fromPosition: " + fromPosition + ", size: " + size);
        }

        if (toPosition < 0 || toPosition >= size) {
            throw new IndexOutOfBoundsException("toPosition: " + toPosition + ", size: " + size);
        }

        List<MusicItem> musicItems = mPlaylist.getAllMusicItem();

        if (toPosition == getPlaylistSize() - 1) {
            musicItems.add(musicItems.remove(fromPosition));
        } else {
            MusicItem musicItem = musicItems.get(fromPosition);
            musicItems.add(toPosition, musicItem);

            musicItems.remove(fromPosition < toPosition ? fromPosition : fromPosition + 1);
        }

        onMusicItemMoved(fromPosition, toPosition);
        updatePlaylist(mPlaylist, musicItems, new Runnable() {
            @Override
            public void run() {
                notifyPlaylistChanged(mPlayerState.getPlayPosition());
            }
        });
    }

    @Override
    public void removeMusicItem(@NonNull final MusicItem musicItem) {
        if (!mPlaylistManager.isPlaylistEditable()) {
            return;
        }

        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    removeMusicItem(musicItem);
                }
            };
            return;
        }

        List<MusicItem> musicItems = mPlaylist.getAllMusicItem();
        if (!musicItems.contains(musicItem)) {
            return;
        }

        final int index = musicItems.indexOf(musicItem);
        final int oldPlayPosition = mPlayerState.getPlayPosition();

        musicItems.remove(musicItem);

        onMusicItemRemoved(index, oldPlayPosition);
        updatePlaylist(mPlaylist, musicItems, new Runnable() {
            @Override
            public void run() {
                int playPosition = mPlayerState.getPlayPosition();
                notifyPlaylistChanged(playPosition);

                if (mPlaylist.isEmpty()) {
                    notifyPlayingMusicItemChanged(null, playPosition, false);
                    notifyStopped();
                    return;
                }

                if (index == oldPlayPosition) {
                    playPosition = playPosition < mPlaylist.size() ? playPosition : 0;
                    notifyPlayingMusicItemChanged(mPlaylist.get(playPosition), playPosition, isPlaying());
                }
            }
        });
    }

    @Override
    public void removeMusicItem(final int position) {
        if (!mPlaylistManager.isPlaylistEditable()) {
            return;
        }

        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    removeMusicItem(position);
                }
            };
            return;
        }

        if (position < 0 || position >= mPlaylist.size()) {
            return;
        }

        removeMusicItem(mPlaylist.get(position));
    }

    @Override
    public void setNextPlay(@NonNull final MusicItem musicItem) {
        if (!mPlaylistManager.isPlaylistEditable()) {
            return;
        }

        if (musicItem.equals(getMusicItem())) {
            return;
        }

        if (mLoadingPlaylist) {
            mPlaylistLoadedAction = new Runnable() {
                @Override
                public void run() {
                    setNextPlay(musicItem);
                }
            };
            return;
        }

        insertMusicItem(mPlayerState.getPlayPosition() + 1, musicItem);
        mConfirmNextPlay = true;
    }

    private void updatePlaylist(Playlist playlist, List<MusicItem> musicItems, Runnable doOnSaved) {
        mPlaylist = new Playlist.Builder()
                .setName(playlist.getName())
                .appendAll(musicItems)
                .setEditable(playlist.isEditable())
                .setExtra(playlist.getExtra())
                .build();
        mPlaylistManager.save(mPlaylist, doOnSaved);
    }

    interface OnStateChangeListener {
        void onPreparing();

        void onPrepared(int audioSessionId);

        void onPlaying(int progress, long updateTime);

        void onPaused();

        void onStalledChanged(boolean stalled);

        void onStopped();

        void onError(int errorCode, String errorMessage);

        void onPlayingMusicItemChanged(@Nullable MusicItem musicItem);

        void onPlayModeChanged(@NonNull PlayMode playMode);
    }
}
