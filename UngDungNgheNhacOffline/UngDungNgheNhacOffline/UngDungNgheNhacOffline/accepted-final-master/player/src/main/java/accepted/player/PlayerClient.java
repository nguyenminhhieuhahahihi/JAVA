package accepted.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import channel.helper.ChannelHelper;
import channel.helper.DispatcherUtil;
import channel.helper.pipe.CustomActionPipe;
import channel.helper.pipe.SessionEventPipe;
import accepted.player.audio.MusicItem;
import accepted.player.playlist.Playlist;
import accepted.player.playlist.PlaylistEditor;
import accepted.player.playlist.PlaylistManager;
import accepted.player.audio.ErrorCode;

public class PlayerClient implements Player, PlayerManager, PlaylistManager, PlaylistEditor, SleepTimer {
    private final Context mApplicationContext;
    private final Class<? extends PlayerService> mPlayerService;
    private final String mClientToken;
    private final String mPersistentId;

    private MediaBrowserCompat mMediaBrowser;
    private MediaControllerCompat mMediaController;
    private MediaControllerCompat.Callback mMediaControllerCallback;
    private SessionEventPipe mSessionEventDispatcher;

    private final PlayerConfig mPlayerConfig;
    private PlayerManager mPlayerManager;
    private PlayerStateSynchronizer mPlayerStateSynchronizer;
    private PlayerStateSynchronizer.OnSyncPlayerStateListener mSyncPlayerStateListener;
    private SleepTimer mSleepTimer;

    private OnConnectCallback mConnectCallback;

    private PlayerState mPlayerState;
    private PlayerStateHelper mPlayerStateHelper;

    private Player mPlayer;
    private PlaylistEditor mPlaylistEditor;
    private PlaylistManagerImp mPlaylistManager;
    private PlayerStateListenerImpl mPlayerStateListener;

    private boolean mConnecting;
    private boolean mAutoConnect;
    private Runnable mConnectedAction;

    private final List<Player.OnPlaybackStateChangeListener> mAllPlaybackStateChangeListener;
    private final List<Player.OnPrepareListener> mAllPrepareListener;
    private final List<Player.OnStalledChangeListener> mAllStalledChangeListener;
    private final List<Player.OnBufferedProgressChangeListener> mAllBufferedProgressChangeListener;
    private final List<Player.OnPlayingMusicItemChangeListener> mAllPlayingMusicItemChangeListener;
    private final List<Player.OnSeekCompleteListener> mAllSeekListener;
    private final List<Player.OnPlaylistChangeListener> mAllPlaylistChangeListener;
    private final List<Player.OnPlayModeChangeListener> mAllPlayModeChangeListener;

    private final List<PlayerClient.OnPlaybackStateChangeListener> mClientAllPlaybackStateChangeListener;
    private final List<PlayerClient.OnAudioSessionChangeListener> mAllAudioSessionChangeListener;
    private final List<SleepTimer.OnStateChangeListener> mAllSleepTimerStateChangeListener;
    private final List<Player.OnRepeatListener> mAllRepeatListener;
    private final List<Player.OnSpeedChangeListener> mAllSpeedChangeListener;

    private final List<OnConnectStateChangeListener> mAllConnectStateChangeListener;

    private PlayerClient(Context context, Class<? extends PlayerService> playerService) {
        mApplicationContext = context.getApplicationContext();
        mPlayerService = playerService;
        mClientToken = UUID.randomUUID().toString();
        mPersistentId = PlayerService.getPersistenceId(playerService);

        mPlayerConfig = new PlayerConfig(context, mPersistentId);

        mAllPlaybackStateChangeListener = new ArrayList<>();
        mAllPrepareListener = new ArrayList<>();
        mAllStalledChangeListener = new ArrayList<>();
        mAllBufferedProgressChangeListener = new ArrayList<>();
        mAllPlayingMusicItemChangeListener = new ArrayList<>();
        mAllSeekListener = new ArrayList<>();
        mAllPlaylistChangeListener = new ArrayList<>();
        mAllPlayModeChangeListener = new ArrayList<>();
        mClientAllPlaybackStateChangeListener = new ArrayList<>();
        mAllAudioSessionChangeListener = new ArrayList<>();
        mAllSleepTimerStateChangeListener = new ArrayList<>();
        mAllRepeatListener = new ArrayList<>();
        mAllSpeedChangeListener = new ArrayList<>();
        mAllConnectStateChangeListener = new ArrayList<>();

        initMediaBrowser();
        initPlaylistManager();
        initPlayerStateHolder();
        initCommandCallback();
        initSessionEventDispatcher();
        initMediaControllerCallback();
        initPlayerState(new PlayerState());
    }

    public static PlayerClient newInstance(@NonNull Context context,
                                           @NonNull Class<? extends PlayerService> playerService) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerService);

        if (serviceNotFound(context, playerService)) {
            throw new IllegalArgumentException("PlayerService not found, Please check your 'AndroidManifest.xml'");
        }

        return new PlayerClient(context, playerService);
    }

    private static boolean serviceNotFound(Context context, Class<? extends PlayerService> playerService) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(context, playerService);
        return pm.resolveService(intent, 0) == null;
    }

    private void initMediaBrowser() {
        mMediaBrowser = new MediaBrowserCompat(mApplicationContext,
                new ComponentName(mApplicationContext, mPlayerService),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        try {
                            mMediaController = new MediaControllerCompat(mApplicationContext, mMediaBrowser.getSessionToken());

                            mMediaController.registerCallback(mMediaControllerCallback, new Handler(Looper.getMainLooper()));
                            initCustomActionEmitter(mMediaController);
                            mPlayerStateSynchronizer.syncPlayerState(mClientToken);
                        } catch (Exception e) {
                            mMediaBrowser.disconnect();
                            onConnectionFailed();
                        }
                    }

                    @Override
                    public void onConnectionFailed() {
                        onDisconnected();

                        if (mConnectCallback != null) {
                            mConnectCallback.onConnected(false);
                            mConnectCallback = null;
                        }
                    }
                }, null);
    }

    private void initPlaylistManager() {
        mPlaylistManager = new PlaylistManagerImp(mApplicationContext, mPersistentId);
    }

    private void initPlayerStateHolder() {
        mPlayerStateListener = new PlayerStateListenerImpl();
    }

    private void initCommandCallback() {
        mSyncPlayerStateListener = new PlayerStateSynchronizer.OnSyncPlayerStateListener() {
            @Override
            public void onSyncPlayerState(@NonNull String clientToken, @NonNull PlayerState playerState) {
                if (!clientToken.equals(mClientToken)) {
                    return;
                }

                initPlayerState(playerState);

                if (mConnectCallback != null) {
                    mConnectCallback.onConnected(true);
                    mConnectCallback = null;
                }

                notifyConnectStateChanged(true);
                notifySticky();
            }
        };
    }

    private void initCustomActionEmitter(MediaControllerCompat mediaController) {
        CustomActionPipe customActionEmitter = new CustomActionPipe(mediaController.getTransportControls());

        mPlayer = ChannelHelper.newEmitter(Player.class, customActionEmitter);
        mPlaylistEditor = ChannelHelper.newEmitter(PlaylistEditor.class, customActionEmitter);

        mPlayerManager = ChannelHelper.newEmitter(PlayerManager.class, customActionEmitter);
        mPlayerStateSynchronizer = ChannelHelper.newEmitter(PlayerStateSynchronizer.class, customActionEmitter);

        mSleepTimer = ChannelHelper.newEmitter(SleepTimer.class, customActionEmitter);
    }

    private void initSessionEventDispatcher() {
        mSessionEventDispatcher = new SessionEventPipe(DispatcherUtil.merge(
                ChannelHelper.newDispatcher(PlayerStateSynchronizer.OnSyncPlayerStateListener.class, mSyncPlayerStateListener),
                ChannelHelper.newDispatcher(PlayerStateListener.class, mPlayerStateListener),
                ChannelHelper.newDispatcher(SleepTimer.OnStateChangeListener.class, mPlayerStateListener)
        ));
    }

    private void initMediaControllerCallback() {
        mMediaControllerCallback = new MediaControllerCompat.Callback() {
            @Override
            public void onSessionEvent(String event, Bundle extras) {
                mSessionEventDispatcher.dispatch(event, extras);
            }
        };
    }

    private void onDisconnected() {
        notifyConnectStateChanged(false);
    }

    private void notifyConnectStateChanged(boolean connected) {
        mConnecting = false;
        for (OnConnectStateChangeListener listener : mAllConnectStateChangeListener) {
            listener.onConnectStateChanged(connected);
        }

        if (connected && mConnectedAction != null) {
            mConnectedAction.run();
        }
    }

    public void connect() {
        if (mConnecting || isConnected()) {
            return;
        }

        mConnecting = true;
        mMediaBrowser.connect();
    }

    public void connect(OnConnectCallback callback) {
        if (isConnected()) {
            callback.onConnected(true);
            return;
        }

        mConnectCallback = callback;
        connect();
    }

    public void disconnect() {
        if (!isConnected()) {
            return;
        }

        onDisconnected();
        mMediaController.unregisterCallback(mMediaControllerCallback);
        mMediaBrowser.disconnect();
    }

    public boolean isConnected() {
        return mMediaBrowser.isConnected();
    }

    public void setAutoConnect(boolean autoConnect) {
        mAutoConnect = autoConnect;
    }

    public boolean isAutoConnect() {
        return mAutoConnect;
    }

    public void addOnConnectStateChangeListener(@NonNull OnConnectStateChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllConnectStateChangeListener.contains(listener)) {
            return;
        }

        mAllConnectStateChangeListener.add(listener);
        listener.onConnectStateChanged(isConnected());
    }

    public void addOnConnectStateChangeListener(@NonNull LifecycleOwner owner,
                                                @NonNull final OnConnectStateChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnConnectStateChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnConnectStateChangeListener(listener);
            }
        }));
    }

    public void removeOnConnectStateChangeListener(OnConnectStateChangeListener listener) {
        mAllConnectStateChangeListener.remove(listener);
    }

    @Nullable
    public MediaControllerCompat getMediaController() {
        return mMediaController;
    }

    public void sendCustomAction(@NonNull String action, @Nullable Bundle args) {
        Preconditions.checkNotNull(action);

        if (notConnected() || getMediaController() == null) {
            return;
        }

        getMediaController().getTransportControls().sendCustomAction(action, args);
    }

    @Override
    public void setSoundQuality(@NonNull SoundQuality soundQuality) {
        Preconditions.checkNotNull(soundQuality);
        if (!isConnected()) {
            return;
        }

        mPlayerManager.setSoundQuality(soundQuality);
    }

    @Override
    public void setAudioEffectConfig(@NonNull Bundle config) {
        Preconditions.checkNotNull(config);
        if (!isConnected()) {
            return;
        }

        mPlayerManager.setAudioEffectConfig(config);
    }

    @Override
    public void setAudioEffectEnabled(boolean enabled) {
        if (!isConnected()) {
            return;
        }

        mPlayerManager.setAudioEffectEnabled(enabled);
    }

    @Override
    public void setOnlyWifiNetwork(boolean onlyWifiNetwork) {
        if (!isConnected()) {
            return;
        }

        mPlayerManager.setOnlyWifiNetwork(onlyWifiNetwork);
    }

    @Override
    public void setIgnoreAudioFocus(boolean ignoreAudioFocus) {
        if (!isConnected()) {
            return;
        }

        mPlayerManager.setIgnoreAudioFocus(ignoreAudioFocus);
    }

    public SoundQuality getSoundQuality() {
        return mPlayerConfig.getSoundQuality();
    }

    @NonNull
    public Bundle getAudioEffectConfig() {
        return mPlayerConfig.getAudioEffectConfig();
    }

    public boolean isAudioEffectEnabled() {
        return mPlayerConfig.isAudioEffectEnabled();
    }

    public boolean isOnlyWifiNetwork() {
        return mPlayerConfig.isOnlyWifiNetwork();
    }

    public boolean isIgnoreAudioFocus() {
        return mPlayerConfig.isIgnoreAudioFocus();
    }

    @Override
    public void shutdown() {
        if (isConnected()) {
            mPlayerManager.shutdown();
        }
    }

    private boolean notConnected() {
        return !mMediaBrowser.isConnected();
    }

    public PlaylistManager getPlaylistManager() {
        return mPlaylistManager;
    }

    public void setPlaylist(@NonNull Playlist playlist) {
        setPlaylist(playlist, 0, false);
    }

    public void setPlaylist(@NonNull Playlist playlist, boolean play) {
        setPlaylist(playlist, 0, play);
    }

    @Override
    public void setPlaylist(@NonNull final Playlist playlist, final int position, final boolean play) throws IllegalArgumentException {
        Preconditions.checkNotNull(playlist);
        if (position < 0) {
            throw new IllegalArgumentException("position must >= 0.");
        }

        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    setPlaylist(playlist, position, play);
                }
            });
            return;
        }

        mPlaylistEditor.setPlaylist(playlist, position, play);
    }

    @Override
    public void getPlaylist(@NonNull PlaylistManager.Callback callback) {
        Preconditions.checkNotNull(callback);
        mPlaylistManager.getPlaylist(callback);
    }

    @Override
    public long getLastModified() {
        return mPlaylistManager.getLastModified();
    }

    @NonNull
    @Override
    public String getPlaylistName() {
        return mPlaylistManager.getPlaylistName();
    }

    @Override
    public int getPlaylistSize() {
        return mPlaylistManager.getPlaylistSize();
    }

    @NonNull
    @Override
    public String getPlaylistToken() {
        return mPlaylistManager.getPlaylistToken();
    }

    @Override
    public boolean isPlaylistEditable() {
        return mPlaylistManager.isPlaylistEditable();
    }

    public int getPlayProgress() {
        return mPlayerState.getPlayProgress();
    }

    public long getPlayProgressUpdateTime() {
        return mPlayerState.getPlayProgressUpdateTime();
    }

    public boolean isLooping() {
        return getPlayMode() == PlayMode.LOOP;
    }

    @Nullable
    public MusicItem getPlayingMusicItem() {
        return mPlayerState.getMusicItem();
    }

    public int getPlayingMusicItemDuration() {
        MusicItem musicItem = getPlayingMusicItem();
        if (musicItem == null) {
            return 0;
        }

        return musicItem.getDuration();
    }

    public PlaybackState getPlaybackState() {
        return mPlayerState.getPlaybackState();
    }

    public int getAudioSessionId() {
        return mPlayerState.getAudioSessionId();
    }

    public int getBufferedProgress() {
        return mPlayerState.getBufferedProgress();
    }

    public boolean isPlaying() {
        return mPlayerState.getPlaybackState() == PlaybackState.PLAYING;
    }

    public boolean isStalled() {
        return mPlayerState.isStalled();
    }

    public boolean isPreparing() {
        return mPlayerState.isPreparing();
    }

    public boolean isPrepared() {
        return mPlayerState.isPrepared();
    }

    public boolean isError() {
        return getErrorCode() != ErrorCode.NO_ERROR;
    }

    public int getErrorCode() {
        return mPlayerState.getErrorCode();
    }

    public String getErrorMessage() {
        return mPlayerState.getErrorMessage();
    }

    public PlayMode getPlayMode() {
        return mPlayerState.getPlayMode();
    }

    public float getSpeed() {
        return mPlayerState.getSpeed();
    }

    public int getPlayPosition() {
        return mPlayerState.getPlayPosition();
    }

    public boolean isSleepTimerStarted() {
        return mPlayerState.isSleepTimerStarted();
    }

    public long getSleepTimerTime() {
        return mPlayerState.getSleepTimerTime();
    }

    public long getSleepTimerStartedTime() {
        return mPlayerState.getSleepTimerStartTime();
    }

    public long getSleepTimerElapsedTime() {
        if (isSleepTimerStarted()) {
            return SystemClock.elapsedRealtime() - getSleepTimerStartedTime();
        }

        return 0;
    }

    @NonNull
    public SleepTimer.TimeoutAction getTimeoutAction() {
        return mPlayerState.getTimeoutAction();
    }

    @Override
    public void skipToNext() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    skipToNext();
                }
            });
            return;
        }

        mPlayer.skipToNext();
    }

    @Override
    public void skipToPrevious() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    skipToPrevious();
                }
            });
            return;
        }

        mPlayer.skipToPrevious();
    }

    @Override
    public void skipToPosition(final int position) throws IllegalArgumentException {
        if (position < 0) {
            throw new IllegalArgumentException("position music >= 0");
        }

        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    skipToPosition(position);
                }
            });
            return;
        }

        mPlayer.skipToPosition(position);
    }

    @Override
    public void playPause(final int position) throws IllegalArgumentException {
        if (position < 0) {
            throw new IllegalArgumentException("position music >= 0");
        }

        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    playPause(position);
                }
            });
            return;
        }

        mPlayer.playPause(position);
    }

    @Override
    public void setPlayMode(@NonNull final PlayMode playMode) {
        Preconditions.checkNotNull(playMode);
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    setPlayMode(playMode);
                }
            });
            return;
        }

        mPlayer.setPlayMode(playMode);
    }

    @Override
    public void setSpeed(final float speed) {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    setSpeed(speed);
                }
            });
            return;
        }

        mPlayer.setSpeed(speed);
    }

    private void tryAutoConnect(@Nullable Runnable connectedAction) {
        if (!mAutoConnect) {
            return;
        }

        if (isConnected() && connectedAction != null) {
            connectedAction.run();
            return;
        }

        mConnectedAction = connectedAction;
        connect();
    }

    @Override
    public void play() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    play();
                }
            });
            return;
        }

        mPlayer.play();
    }

    @Override
    public void pause() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    pause();
                }
            });
            return;
        }

        mPlayer.pause();
    }

    @Override
    public void stop() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    stop();
                }
            });
            return;
        }

        mPlayer.stop();
    }

    @Override
    public void playPause() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    playPause();
                }
            });
            return;
        }

        mPlayer.playPause();
    }

    @Override
    public void seekTo(final int progress) {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    seekTo(progress);
                }
            });
            return;
        }

        mPlayer.seekTo(progress);
    }

    public boolean isForbidSeek() {
        if (notConnected()) {
            return false;
        }

        return mPlayerState.isForbidSeek();
    }

    @Override
    public void fastForward() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    fastForward();
                }
            });
            return;
        }

        mPlayer.fastForward();
    }

    @Override
    public void rewind() {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    rewind();
                }
            });
            return;
        }

        mPlayer.rewind();
    }

    public void startSleepTimer(long time) throws IllegalArgumentException {
        startSleepTimer(time, SleepTimer.TimeoutAction.PAUSE);
    }

    @Override
    public void startSleepTimer(long time, @NonNull SleepTimer.TimeoutAction action) throws IllegalArgumentException {
        if (time < 0) {
            throw new IllegalArgumentException("time music >= 0");
        }
        Preconditions.checkNotNull(action);

        if (notConnected()) {
            return;
        }

        mSleepTimer.startSleepTimer(time, action);
    }

    @Override
    public void cancelSleepTimer() {
        if (notConnected()) {
            return;
        }

        mSleepTimer.cancelSleepTimer();
    }

    public void addOnPlaybackStateChangeListener(@NonNull Player.OnPlaybackStateChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllPlaybackStateChangeListener.contains(listener)) {
            return;
        }

        mAllPlaybackStateChangeListener.add(listener);
        notifyPlaybackStateChanged(listener);
    }

    public void addOnPlaybackStateChangeListener(@NonNull LifecycleOwner owner,
                                                 @NonNull final Player.OnPlaybackStateChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnPlaybackStateChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnPlaybackStateChangeListener(listener);
            }
        }));
    }

    public void removeOnPlaybackStateChangeListener(Player.OnPlaybackStateChangeListener listener) {
        mAllPlaybackStateChangeListener.remove(listener);
    }

    public void addOnPrepareListener(@NonNull Player.OnPrepareListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllPrepareListener.contains(listener)) {
            return;
        }

        mAllPrepareListener.add(listener);
        notifyPrepareStateChanged(listener);
    }

    public void addOnPrepareListener(@NonNull LifecycleOwner owner, @NonNull final Player.OnPrepareListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnPrepareListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnPrepareListener(listener);
            }
        }));
    }

    public void removeOnPrepareListener(Player.OnPrepareListener listener) {
        mAllPrepareListener.remove(listener);
    }

    public void addOnStalledChangeListener(@NonNull Player.OnStalledChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllStalledChangeListener.contains(listener)) {
            return;
        }

        mAllStalledChangeListener.add(listener);
        notifyStalledChanged(listener);
    }

    public void addOnStalledChangeListener(@NonNull LifecycleOwner owner,
                                           @NonNull final Player.OnStalledChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnStalledChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnStalledChangeListener(listener);
            }
        }));
    }

    public void removeOnStalledChangeListener(Player.OnStalledChangeListener listener) {
        mAllStalledChangeListener.remove(listener);
    }

    public void addOnBufferedProgressChangeListener(@NonNull OnBufferedProgressChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllBufferedProgressChangeListener.contains(listener)) {
            return;
        }

        mAllBufferedProgressChangeListener.add(listener);
        notifyOnBufferedProgressChanged(listener);
    }

    public void addOnBufferedProgressChangeListener(@NonNull LifecycleOwner owner,
                                                    @NonNull final OnBufferedProgressChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnBufferedProgressChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnBufferedProgressChangeListener(listener);
            }
        }));
    }

    public void removeOnBufferedProgressChangeListener(OnBufferedProgressChangeListener listener) {
        mAllBufferedProgressChangeListener.remove(listener);
    }

    public void addOnPlayingMusicItemChangeListener(@NonNull Player.OnPlayingMusicItemChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllPlayingMusicItemChangeListener.contains(listener)) {
            return;
        }

        mAllPlayingMusicItemChangeListener.add(listener);
        notifyPlayingMusicItemChanged(listener);
    }

    public void addOnPlayingMusicItemChangeListener(@NonNull LifecycleOwner owner,
                                                    @NonNull final Player.OnPlayingMusicItemChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnPlayingMusicItemChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnPlayingMusicItemChangeListener(listener);
            }
        }));
    }

    public void removeOnPlayingMusicItemChangeListener(Player.OnPlayingMusicItemChangeListener listener) {
        mAllPlayingMusicItemChangeListener.remove(listener);
    }

    public void addOnSeekCompleteListener(@NonNull OnSeekCompleteListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllSeekListener.contains(listener)) {
            return;
        }

        mAllSeekListener.add(listener);
        notifySeekComplete(listener);
    }

    public void addOnSeekCompleteListener(@NonNull LifecycleOwner owner,
                                          @NonNull final OnSeekCompleteListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnSeekCompleteListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnSeekCompleteListener(listener);
            }
        }));
    }

    public void removeOnSeekCompleteListener(OnSeekCompleteListener listener) {
        mAllSeekListener.remove(listener);
    }

    public void addOnPlaylistChangeListener(@NonNull Player.OnPlaylistChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllPlaylistChangeListener.contains(listener)) {
            return;
        }

        mAllPlaylistChangeListener.add(listener);
        notifyPlaylistChanged(listener);
    }

    public void addOnPlaylistChangeListener(@NonNull LifecycleOwner owner,
                                            @NonNull final Player.OnPlaylistChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnPlaylistChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnPlaylistChangeListener(listener);
            }
        }));
    }

    public void removeOnPlaylistChangeListener(Player.OnPlaylistChangeListener listener) {
        mAllPlaylistChangeListener.remove(listener);
    }

    public void addOnPlayModeChangeListener(@NonNull Player.OnPlayModeChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllPlayModeChangeListener.contains(listener)) {
            return;
        }

        mAllPlayModeChangeListener.add(listener);
        notifyPlayModeChanged(listener);
    }

    public void addOnPlayModeChangeListener(@NonNull LifecycleOwner owner,
                                            @NonNull final Player.OnPlayModeChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnPlayModeChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnPlayModeChangeListener(listener);
            }
        }));
    }

    public void removeOnPlayModeChangeListener(Player.OnPlayModeChangeListener listener) {
        mAllPlayModeChangeListener.remove(listener);
    }

    public void addOnSpeedChangeListener(@NonNull Player.OnSpeedChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllSpeedChangeListener.contains(listener)) {
            return;
        }

        mAllSpeedChangeListener.add(listener);
        notifySpeedChanged(listener);
    }

    public void addOnSpeedChangeListener(@NonNull LifecycleOwner owner,
                                         @NonNull final Player.OnSpeedChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnSpeedChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnSpeedChangeListener(listener);
            }
        }));
    }

    public void removeOnSpeedChangeListener(Player.OnSpeedChangeListener listener) {
        mAllSpeedChangeListener.remove(listener);
    }

    public void addOnPlaybackStateChangeListener(@NonNull OnPlaybackStateChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mClientAllPlaybackStateChangeListener.contains(listener)) {
            return;
        }

        mClientAllPlaybackStateChangeListener.add(listener);
        notifyClientPlaybackStateChanged(listener);
    }

    public void addOnPlaybackStateChangeListener(@NonNull LifecycleOwner owner,
                                                 @NonNull final OnPlaybackStateChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnPlaybackStateChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnPlaybackStateChangeListener(listener);
            }
        }));
    }

    public void removeOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        mClientAllPlaybackStateChangeListener.remove(listener);
    }

    public void addOnAudioSessionChangeListener(@NonNull OnAudioSessionChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllAudioSessionChangeListener.contains(listener)) {
            return;
        }

        mAllAudioSessionChangeListener.add(listener);
        notifyAudioSessionChanged(listener);
    }

    public void addOnAudioSessionChangeListener(@NonNull LifecycleOwner owner,
                                                @NonNull final OnAudioSessionChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnAudioSessionChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnAudioSessionChangeListener(listener);
            }
        }));
    }

    public void removeOnAudioSessionChangeListener(OnAudioSessionChangeListener listener) {
        mAllAudioSessionChangeListener.remove(listener);
    }

    public void addOnSleepTimerStateChangeListener(@NonNull SleepTimer.OnStateChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllSleepTimerStateChangeListener.contains(listener)) {
            return;
        }

        mAllSleepTimerStateChangeListener.add(listener);
        if (mPlayerState.isSleepTimerStarted()) {
            notifySleepTimerStateChanged(listener);
        }
    }

    public void addOnSleepTimerStateChangeListener(@NonNull LifecycleOwner owner,
                                                   @NonNull final SleepTimer.OnStateChangeListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnSleepTimerStateChangeListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnSleepTimerStateChangeListener(listener);
            }
        }));
    }

    public void removeOnSleepTimerStateChangeListener(SleepTimer.OnStateChangeListener listener) {
        mAllSleepTimerStateChangeListener.remove(listener);
    }

    public void addOnRepeatListener(@NonNull Player.OnRepeatListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllRepeatListener.contains(listener)) {
            return;
        }

        mAllRepeatListener.add(listener);
    }

    public void addOnRepeatListener(@NonNull LifecycleOwner owner,
                                    @NonNull final Player.OnRepeatListener listener) {
        Preconditions.checkNotNull(owner);
        Preconditions.checkNotNull(listener);

        if (isDestroyed(owner)) {
            return;
        }

        addOnRepeatListener(listener);
        owner.getLifecycle().addObserver(new DestroyObserver(new Runnable() {
            @Override
            public void run() {
                removeOnRepeatListener(listener);
            }
        }));
    }

    public void removeOnRepeatListener(Player.OnRepeatListener listener) {
        mAllRepeatListener.remove(listener);
    }

    private boolean isDestroyed(LifecycleOwner owner) {
        return owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED;
    }

    @Override
    public void insertMusicItem(final int position, @NonNull final MusicItem musicItem) throws IllegalArgumentException {
        if (position < 0) {
            throw new IllegalArgumentException("position must >= 0.");
        }
        Preconditions.checkNotNull(musicItem);

        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    insertMusicItem(position, musicItem);
                }
            });
            return;
        }

        mPlaylistEditor.insertMusicItem(position, musicItem);
    }

    @Override
    public void appendMusicItem(@NonNull final MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    appendMusicItem(musicItem);
                }
            });
            return;
        }

        mPlaylistEditor.appendMusicItem(musicItem);
    }

    @Override
    public void moveMusicItem(final int fromPosition, final int toPosition) throws IndexOutOfBoundsException {
        int size = getPlaylistSize();
        if (fromPosition < 0 || fromPosition >= size) {
            throw new IndexOutOfBoundsException("fromPosition: " + fromPosition + ", size: " + size);
        }

        if (toPosition < 0 || toPosition >= size) {
            throw new IndexOutOfBoundsException("toPosition: " + toPosition + ", size: " + size);
        }

        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    moveMusicItem(fromPosition, toPosition);
                }
            });
            return;
        }

        mPlaylistEditor.moveMusicItem(fromPosition, toPosition);
    }

    @Override
    public void removeMusicItem(@NonNull final MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    removeMusicItem(musicItem);
                }
            });
            return;
        }

        mPlaylistEditor.removeMusicItem(musicItem);
    }

    @Override
    public void removeMusicItem(final int position) {
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    removeMusicItem(position);
                }
            });
            return;
        }

        mPlaylistEditor.removeMusicItem(position);
    }

    @Override
    public void setNextPlay(@NonNull final MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        if (notConnected()) {
            tryAutoConnect(new Runnable() {
                @Override
                public void run() {
                    setNextPlay(musicItem);
                }
            });
            return;
        }

        mPlaylistEditor.setNextPlay(musicItem);
    }

    public interface OnConnectCallback {
        void onConnected(boolean success);
    }

    public interface OnConnectStateChangeListener {
        void onConnectStateChanged(boolean connected);
    }

    public interface OnPlaybackStateChangeListener {
        void onPlaybackStateChanged(PlaybackState playbackState, boolean stalled);
    }

    public interface OnAudioSessionChangeListener {
        void onAudioSessionChanged(int audioSessionId);
    }

    private void notifySticky() {
        if (notConnected()) {
            return;
        }

        notifyPlaylistChanged();
        notifyPlayModeChanged();
        notifySpeedChanged();
        notifyPlayingMusicItemChanged();
        notifyPrepareStateChanged();
        notifyAudioSessionChanged();
        notifyPlaybackStateChanged();
        notifyOnBufferedProgressChanged();

        if (mPlayerState.isStalled()) {
            notifyStalledChanged();
        }

        if (mPlayerState.isSleepTimerStarted()) {
            notifySleepTimerStateChanged();
        }
    }

    void initPlayerState(PlayerState playerState) {
        mPlayerState = playerState;
        mPlayerStateHelper = new PlayerStateHelper(mPlayerState);
    }

    private void notifyPlaybackStateChanged(Player.OnPlaybackStateChangeListener listener) {
        if (notConnected()) {
            return;
        }

        switch (mPlayerState.getPlaybackState()) {
            case PLAYING:
                listener.onPlay(mPlayerState.isStalled(), mPlayerState.getPlayProgress(), mPlayerState.getPlayProgressUpdateTime());
                break;
            case PAUSED:
                listener.onPause(mPlayerState.getPlayProgress(), mPlayerState.getPlayProgressUpdateTime());
                break;
            case STOPPED:
                listener.onStop();
                break;
            case ERROR:
                listener.onError(mPlayerState.getErrorCode(), mPlayerState.getErrorMessage());
                break;
            default:
                break;
        }
    }

    private void notifyPlaybackStateChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnPlaybackStateChangeListener listener : mAllPlaybackStateChangeListener) {
            notifyPlaybackStateChanged(listener);
        }

        notifyClientPlaybackStateChanged();
    }

    private void notifyPrepareStateChanged(Player.OnPrepareListener listener) {
        if (notConnected()) {
            return;
        }

        if (mPlayerState.isPreparing()) {
            listener.onPreparing();
            return;
        }

        if (mPlayerState.isPrepared()) {
            listener.onPrepared(mPlayerState.getAudioSessionId());
        }
    }

    private void notifyPrepareStateChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnPrepareListener listener : mAllPrepareListener) {
            notifyPrepareStateChanged(listener);
        }
    }

    private void notifyStalledChanged(Player.OnStalledChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onStalledChanged(mPlayerState.isStalled(),
                mPlayerState.getPlayProgress(),
                mPlayerState.getPlayProgressUpdateTime());
    }

    private void notifyStalledChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnStalledChangeListener listener : mAllStalledChangeListener) {
            notifyStalledChanged(listener);
        }
    }

    private void notifyOnBufferedProgressChanged(OnBufferedProgressChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onBufferedProgressChanged(mPlayerState.getBufferedProgress());
    }

    private void notifyOnBufferedProgressChanged() {
        if (notConnected()) {
            return;
        }

        for (OnBufferedProgressChangeListener listener : mAllBufferedProgressChangeListener) {
            notifyOnBufferedProgressChanged(listener);
        }
    }

    private void notifyPlayingMusicItemChanged(Player.OnPlayingMusicItemChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onPlayingMusicItemChanged(mPlayerState.getMusicItem(), mPlayerState.getPlayPosition(), mPlayerState.getPlayProgress());
    }

    private void notifyPlayingMusicItemChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnPlayingMusicItemChangeListener listener : mAllPlayingMusicItemChangeListener) {
            notifyPlayingMusicItemChanged(listener);
        }
    }

    private void notifySeekComplete(OnSeekCompleteListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onSeekComplete(mPlayerState.getPlayProgress(), mPlayerState.getPlayProgressUpdateTime(), mPlayerState.isStalled());
    }

    private void notifySeekComplete() {
        if (notConnected()) {
            return;
        }

        for (OnSeekCompleteListener listener : mAllSeekListener) {
            notifySeekComplete(listener);
        }
    }

    private void notifyPlaylistChanged(Player.OnPlaylistChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onPlaylistChanged(mPlaylistManager, mPlayerState.getPlayPosition());
    }

    private void notifyPlaylistChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnPlaylistChangeListener listener : mAllPlaylistChangeListener) {
            notifyPlaylistChanged(listener);
        }
    }

    private void notifyPlayModeChanged(Player.OnPlayModeChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onPlayModeChanged(mPlayerState.getPlayMode());
    }

    private void notifyPlayModeChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnPlayModeChangeListener listener : mAllPlayModeChangeListener) {
            notifyPlayModeChanged(listener);
        }
    }

    private void notifySpeedChanged(Player.OnSpeedChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onSpeedChanged(mPlayerState.getSpeed());
    }

    private void notifySpeedChanged() {
        if (notConnected()) {
            return;
        }

        for (Player.OnSpeedChangeListener listener : mAllSpeedChangeListener) {
            notifySpeedChanged(listener);
        }
    }

    private void notifyClientPlaybackStateChanged() {
        if (notConnected()) {
            return;
        }

        for (OnPlaybackStateChangeListener listener : mClientAllPlaybackStateChangeListener) {
            notifyClientPlaybackStateChanged(listener);
        }
    }

    private void notifyClientPlaybackStateChanged(OnPlaybackStateChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onPlaybackStateChanged(mPlayerState.getPlaybackState(), mPlayerState.isStalled());
    }

    private void notifyAudioSessionChanged() {
        if (notConnected()) {
            return;
        }

        for (OnAudioSessionChangeListener listener : mAllAudioSessionChangeListener) {
            notifyAudioSessionChanged(listener);
        }
    }

    private void notifyAudioSessionChanged(OnAudioSessionChangeListener listener) {
        if (notConnected()) {
            return;
        }

        listener.onAudioSessionChanged(mPlayerState.getAudioSessionId());
    }

    private void notifySleepTimerStateChanged() {
        if (notConnected()) {
            return;
        }

        for (SleepTimer.OnStateChangeListener listener : mAllSleepTimerStateChangeListener) {
            notifySleepTimerStateChanged(listener);
        }
    }

    private void notifySleepTimerStateChanged(SleepTimer.OnStateChangeListener listener) {
        if (notConnected()) {
            return;
        }

        if (mPlayerState.isSleepTimerStarted()) {
            listener.onTimerStart(
                    mPlayerState.getSleepTimerTime(),
                    mPlayerState.getSleepTimerStartTime(),
                    mPlayerState.getTimeoutAction());
        } else {
            listener.onTimerEnd();
        }
    }

    private void notifyRepeat(@NonNull MusicItem musicItem, long repeatTime) {
        for (OnRepeatListener listener : mAllRepeatListener) {
            listener.onRepeat(musicItem, repeatTime);
        }
    }

    private class PlayerStateListenerImpl implements PlayerStateListener, SleepTimer.OnStateChangeListener {

        @Override
        public void onPreparing() {
            boolean error = mPlayerState.getPlaybackState() == PlaybackState.ERROR;
            mPlayerStateHelper.onPreparing();

            if (error) {
                notifyPlaybackStateChanged();
            }

            notifyPrepareStateChanged();
        }

        @Override
        public void onPrepared(int audioSessionId) {
            mPlayerStateHelper.onPrepared(audioSessionId);

            notifyPrepareStateChanged();
            notifyAudioSessionChanged();
        }

        @Override
        public void onPlay(boolean stalled, int playProgress, long playProgressUpdateTime) {
            mPlayerStateHelper.onPlay(stalled, playProgress, playProgressUpdateTime);

            notifyPlaybackStateChanged();
        }

        @Override
        public void onPause(int playProgress, long updateTime) {
            mPlayerStateHelper.onPaused(playProgress, updateTime);

            notifyPlaybackStateChanged();
        }

        @Override
        public void onStop() {
            mPlayerStateHelper.onStopped();

            notifyPlaybackStateChanged();
        }

        @Override
        public void onError(int errorCode, String errorMessage) {
            mPlayerStateHelper.onError(errorCode, errorMessage);

            notifyPlaybackStateChanged();
        }

        @Override
        public void onSeekComplete(int progress, long updateTime, boolean stalled) {
            mPlayerStateHelper.onSeekComplete(progress, updateTime, stalled);

            notifySeekComplete();
        }

        @Override
        public void onBufferedProgressChanged(int bufferedProgress) {
            mPlayerStateHelper.onBufferedChanged(bufferedProgress);

            notifyOnBufferedProgressChanged();
        }

        @Override
        public void onPlayingMusicItemChanged(@Nullable MusicItem musicItem, int position, int playProgress) {
            boolean error = mPlayerState.getPlaybackState() == PlaybackState.ERROR;
            mPlayerStateHelper.onPlayingMusicItemChanged(musicItem, position, playProgress);

            notifyPlayingMusicItemChanged();

            if (error) {
                notifyPlaybackStateChanged();
            }
        }

        @Override
        public void onStalledChanged(boolean stalled, int playProgress, long updateTime) {
            mPlayerStateHelper.onStalled(stalled, playProgress, updateTime);

            notifyStalledChanged();
        }

        @Override
        public void onPlaylistChanged(PlaylistManager playlistManager, int position) {
            mPlayerStateHelper.onPlaylistChanged(position);

            notifyPlaylistChanged();
        }

        @Override
        public void onPlayModeChanged(PlayMode playMode) {
            mPlayerStateHelper.onPlayModeChanged(playMode);

            notifyPlayModeChanged();
        }

        @Override
        public void onTimerStart(long time, long startTime, SleepTimer.TimeoutAction action) {
            mPlayerStateHelper.onSleepTimerStart(time, startTime, action);
            notifySleepTimerStateChanged();
        }

        @Override
        public void onTimerEnd() {
            mPlayerStateHelper.onSleepTimerEnd();
            notifySleepTimerStateChanged();
        }

        @Override
        public void onShutdown() {
            disconnect();
        }

        @Override
        public void onRepeat(@NonNull MusicItem musicItem, long repeatTime) {
            mPlayerStateHelper.onRepeat(repeatTime);
            notifyRepeat(musicItem, repeatTime);
        }

        @Override
        public void onSpeedChanged(float speed) {
            mPlayerStateHelper.onSpeedChanged(speed);
            notifySpeedChanged();
        }
    }

    private static class DestroyObserver implements LifecycleObserver {
        private final Runnable mOnDestroyAction;

        DestroyObserver(@NonNull Runnable action) {
            Preconditions.checkNotNull(action);
            mOnDestroyAction = action;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        public void onDestroy() {
            mOnDestroyAction.run();
        }
    }
}
