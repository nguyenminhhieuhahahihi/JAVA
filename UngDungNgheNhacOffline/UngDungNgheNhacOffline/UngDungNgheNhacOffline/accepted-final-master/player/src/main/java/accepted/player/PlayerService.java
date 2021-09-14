package accepted.player;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import channel.helper.ChannelHelper;
import channel.helper.Dispatcher;
import channel.helper.DispatcherUtil;
import channel.helper.pipe.CustomActionPipe;

import channel.helper.pipe.SessionEventPipe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import media.helper.HeadsetHookHelper;

import accepted.player.annotation.PersistenceId;
import accepted.player.effect.AudioEffectManager;
import accepted.player.audio.MediaMusicPlayer;
import accepted.player.audio.MusicItem;
import accepted.player.audio.MusicPlayer;
import accepted.player.playlist.Playlist;
import accepted.player.playlist.PlaylistEditor;
import accepted.player.audio.ErrorCode;
import accepted.player.playlist.PlaylistManager;
import accepted.player.util.MusicItemUtil;
import accepted.player.util.AsyncResult;

@SuppressWarnings("SameReturnValue")
public class PlayerService extends MediaBrowserServiceCompat
        implements PlayerManager, PlaylistManager, PlaylistEditor, SleepTimer {

    public static final String DEFAULT_MEDIA_ROOT_ID = "root";

    public static final String CUSTOM_ACTION_SHUTDOWN = "accepted.player.custom_action.SHUTDOWN";

    public static final String SESSION_EVENT_ON_SHUTDOWN = "accepted.player.session_event.ON_SHUTDOWN";

    private static final String CUSTOM_ACTION_NAME = "accepted.player.action.ACTION_NAME";

    private String mPersistentId;

    private PlayerConfig mPlayerConfig;
    private PlayerState mPlayerState;

    private PlaylistManagerImp mPlaylistManager;
    private PlayerImp mPlayer;
    private CustomActionPipe mCustomActionDispatcher;

    private PlayerStateListener mPlayerStateListener;
    private PlayerStateSynchronizer.OnSyncPlayerStateListener mSyncPlayerStateListener;

    private boolean mForeground;

    private NotificationManager mNotificationManager;

    private Map<String, CustomAction> mAllCustomAction;

    private MediaSessionCompat mMediaSession;

    private HeadsetHookHelper mHeadsetHookHelper;

    @Nullable
    private NotificationView mNotificationView;

    @Nullable
    private AudioEffectManager mAudioEffectManager;

    @Nullable
    private HistoryRecorder mHistoryRecorder;

    private OnStateChangeListener mSleepTimerStateChangedListener;
    private Disposable mSleepTimerDisposable;
    private PlayerStateHelper mPlayerStateHelper;

    private int mMaxIDLEMinutes = -1;
    private Disposable mIDLETimerDisposable;

    private Intent mKeepAliveIntent;
    private KeepAliveConnection mKeepAliveConnection;
    private boolean mKeepServiceAlive;

    private BroadcastReceiver mCustomActionReceiver;
    private PlayerStateSynchronizer mPlayerStateSynchronizer;

    private AbstractPlayer.OnStateChangeListener mOnStateChangeListener;

    @Override
    public void onCreate() {
        super.onCreate();

        mPersistentId = getPersistenceId(this.getClass());
        mAllCustomAction = new HashMap<>();
        mKeepAliveIntent = new Intent(this, this.getClass());
        mKeepAliveConnection = new KeepAliveConnection();
        mPlayerStateSynchronizer = new PlayerStateSynchronizer() {
            @Override
            public void syncPlayerState(String clientToken) {
                mSyncPlayerStateListener.onSyncPlayerState(clientToken, new PlayerState(mPlayerState));
            }
        };

        initNotificationManager();
        initPlayerConfig();
        initPlayerState();
        initPlaylistManager();
        initNotificationView();
        initOnStateChangeListener();
        initPlayer();
        initAudioEffectManager();
        initCustomActionDispatcher();
        initHeadsetHookHelper();
        initMediaSession();
        initSessionEventEmitter();
        initHistoryRecorder();
        initCustomActionReceiver();

        keepServiceAlive();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mMediaSession, intent);
        }

        return START_NOT_STICKY;
    }

    private void handleCustomAction(String action, Bundle extras) {
        CustomAction customAction = mAllCustomAction.get(action);
        if (customAction != null) {
            customAction.doAction(getPlayer(), extras);
        }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(DEFAULT_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(Collections.<MediaBrowserCompat.MediaItem>emptyList());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (!noNotificationView()) {
            stopForegroundEx(true);
            mNotificationView.release();
            mNotificationManager.cancel(mNotificationView.getNotificationId());
        }

        cancelIDLETimer();

        unregisterReceiver(mCustomActionReceiver);
        mMediaSession.release();
        mPlayer.release();

        mPlayer = null;

        if (mAudioEffectManager != null) {
            mAudioEffectManager.release();
        }
    }

    private void keepServiceAlive() {
        if (mKeepServiceAlive) {
            return;
        }

        mKeepServiceAlive = true;
        bindService(mKeepAliveIntent, mKeepAliveConnection, BIND_AUTO_CREATE);
    }

    private void dismissKeepServiceAlive() {
        if (mKeepServiceAlive) {
            mKeepServiceAlive = false;
            unbindService(mKeepAliveConnection);
        }
    }

    private void initNotificationManager() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NotificationView.CHANNEL_ID,
                    getString(R.string.accepted_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private void initPlayerConfig() {
        mPlayerConfig = new PlayerConfig(this, mPersistentId);
    }

    private void initPlayerState() {
        mPlayerState = new PersistentPlayerState(this, mPersistentId);
        mPlayerStateHelper = new PlayerStateHelper(mPlayerState);
    }

    private void initPlaylistManager() {
        mPlaylistManager = new PlaylistManagerImp(this, mPersistentId);
    }

    private void initOnStateChangeListener() {
        mOnStateChangeListener = new AbstractPlayer.OnStateChangeListener() {
            @Override
            public void onPreparing() {
                PlayerService.this.updateNotificationView();
                PlayerService.this.cancelIDLETimer();
            }

            @Override
            public void onPrepared(int audioSessionId) {
                PlayerService.this.updateNotificationView();
            }

            @Override
            public void onPlaying(int progress, long updateTime) {
                PlayerService.this.updateNotificationView();
                PlayerService.this.cancelIDLETimer();
            }

            @Override
            public void onPaused() {
                PlayerService.this.updateNotificationView();
                PlayerService.this.startIDLETimer();
            }

            @Override
            public void onStalledChanged(boolean stalled) {
                PlayerService.this.updateNotificationView();
            }

            @Override
            public void onStopped() {
                PlayerService.this.onStopped();
                PlayerService.this.startIDLETimer();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                PlayerService.this.updateNotificationView();
            }

            @Override
            public void onPlayingMusicItemChanged(@Nullable MusicItem musicItem) {
                PlayerService.this.onPlayingMusicItemChanged(musicItem);
            }

            @Override
            public void onPlayModeChanged(@NonNull PlayMode playMode) {
                PlayerService.this.notifyPlayModeChanged(playMode);
            }
        };
    }

    private void initPlayer() {
        mPlayer = new PlayerImp(this,
                mPlayerConfig,
                mPlayerState,
                mPlaylistManager,
                this.getClass(),
                mOnStateChangeListener);
    }

    private void initCustomActionDispatcher() {
        final Dispatcher playerStateSynchronizerDispatcher =
                ChannelHelper.newDispatcher(PlayerStateSynchronizer.class, mPlayerStateSynchronizer);

        final Dispatcher playerManagerDispatcher =
                ChannelHelper.newDispatcher(PlayerManager.class, this);

        final Dispatcher playerDispatcher =
                ChannelHelper.newDispatcher(Player.class, mPlayer);

        final Dispatcher playlistEditorDispatcher =
                ChannelHelper.newDispatcher(PlaylistEditor.class, mPlayer);

        final Dispatcher sleepTimerDispatcher =
                ChannelHelper.newDispatcher(SleepTimer.class, this);

        mCustomActionDispatcher = new CustomActionPipe(
                DispatcherUtil.merge(
                        playerStateSynchronizerDispatcher,
                        playerManagerDispatcher,
                        playerDispatcher,
                        playlistEditorDispatcher,
                        sleepTimerDispatcher
                ));
    }

    private void initNotificationView() {
        NotificationView notificationView = onCreateNotificationView();

        if (notificationView == null) {
            return;
        }

        notificationView.init(this);
        MusicItem musicItem = getPlayingMusicItem();

        if (musicItem != null) {
            notificationView.setPlayingMusicItem(musicItem);
        }

        mNotificationView = notificationView;
    }

    private void initHeadsetHookHelper() {
        mHeadsetHookHelper = new HeadsetHookHelper(new HeadsetHookHelper.OnHeadsetHookClickListener() {
            @Override
            public void onHeadsetHookClicked(int clickCount) {
                PlayerService.this.onHeadsetHookClicked(clickCount);
            }
        });
    }

    private void initMediaSession() {
        mMediaSession = new MediaSessionCompat(this, this.getClass().getName());
        mPlayer.setMediaSession(mMediaSession);

        mMediaSession.setCallback(onCreateMediaSessionCallback());

        setSessionToken(mMediaSession.getSessionToken());
    }

    private void initSessionEventEmitter() {
        SessionEventPipe sessionEventEmitter = new SessionEventPipe(mMediaSession);
        mPlayerStateListener = ChannelHelper.newEmitter(PlayerStateListener.class, sessionEventEmitter);
        mSyncPlayerStateListener = ChannelHelper.newEmitter(PlayerStateSynchronizer.OnSyncPlayerStateListener.class, sessionEventEmitter);

        mPlayer.setPlayerStateListener(mPlayerStateListener);
        mSleepTimerStateChangedListener = ChannelHelper.newEmitter(OnStateChangeListener.class, sessionEventEmitter);
    }

    private void initAudioEffectManager() {
        mAudioEffectManager = onCreateAudioEffectManager();

        if (mAudioEffectManager == null) {
            return;
        }

        Bundle config = mPlayerConfig.getAudioEffectConfig();
        mAudioEffectManager.init(config);
        mPlayer.setAudioEffectManager(mAudioEffectManager);
    }

    private void initHistoryRecorder() {
        mHistoryRecorder = onCreateHistoryRecorder();
    }

    private void initCustomActionReceiver() {
        mCustomActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    onCustomAction(intent.getStringExtra(CUSTOM_ACTION_NAME), intent.getExtras());
                }
            }
        };

        IntentFilter filter = new IntentFilter(this.getClass().getName());
        registerReceiver(mCustomActionReceiver, filter);
    }

    protected void setMediaSessionFlags(int flags) {
        mMediaSession.setFlags(flags);
    }

    @NonNull
    protected MediaSessionCallback onCreateMediaSessionCallback() {
        return new MediaSessionCallback(this);
    }

    @Nullable
    protected NotificationView onCreateNotificationView() {
        return new MediaNotificationView();
    }

    @Nullable
    protected AudioEffectManager onCreateAudioEffectManager() {
        return null;
    }

    @Nullable
    protected HistoryRecorder onCreateHistoryRecorder() {
        return null;
    }

    @Override
    public void setSoundQuality(SoundQuality soundQuality) {
        if (soundQuality == mPlayerConfig.getSoundQuality()) {
            return;
        }

        mPlayerConfig.setSoundQuality(soundQuality);
        mPlayer.notifySoundQualityChanged();
    }

    @Override
    public void setAudioEffectEnabled(boolean enabled) {
        if (mPlayerConfig.isAudioEffectEnabled() == enabled) {
            return;
        }

        mPlayerConfig.setAudioEffectEnabled(enabled);
        notifyAudioEffectEnableChanged();
    }

    @Override
    public void setAudioEffectConfig(Bundle config) {
        if (noAudioEffectManager() || !mPlayerConfig.isAudioEffectEnabled()) {
            return;
        }

        mAudioEffectManager.updateConfig(config);
        mPlayerConfig.setAudioEffectConfig(config);
    }

    protected void onHeadsetHookClicked(int clickCount) {
        switch (clickCount) {
            case 1:
                getPlayer().playPause();
                break;
            case 2:
                getPlayer().skipToNext();
                break;
            case 3:
                getPlayer().skipToPrevious();
                break;
        }
    }

    private boolean noAudioEffectManager() {
        return mAudioEffectManager == null;
    }

    @Deprecated
    protected void attachAudioEffect(int audioSessionId) {
        if (noAudioEffectManager()) {
            return;
        }

        mAudioEffectManager.attachAudioEffect(audioSessionId);
    }

    @Deprecated
    protected void detachAudioEffect() {
        if (noAudioEffectManager()) {
            return;
        }

        mAudioEffectManager.detachAudioEffect();
    }

    private void notifyAudioEffectEnableChanged() {
        mPlayer.notifyAudioEffectEnableChanged();
    }

    @Override
    public void setOnlyWifiNetwork(boolean onlyWifiNetwork) {
        if (mPlayerConfig.isOnlyWifiNetwork() == onlyWifiNetwork) {
            return;
        }

        mPlayerConfig.setOnlyWifiNetwork(onlyWifiNetwork);
        mPlayer.notifyOnlyWifiNetworkChanged();
    }

    @Override
    public void setIgnoreAudioFocus(boolean ignoreAudioFocus) {
        if (ignoreAudioFocus == mPlayerConfig.isIgnoreAudioFocus()) {
            return;
        }

        mPlayerConfig.setIgnoreAudioFocus(ignoreAudioFocus);
        mPlayer.notifyIgnoreAudioFocusChanged();
    }

    @Override
    public final void shutdown() {
        if (mPlayer.getPlaybackState() == PlaybackState.PLAYING) {
            getPlayer().pause();
        }

        stopSelf();
        notifyOnShutdown();
        dismissKeepServiceAlive();
    }

    public boolean isIgnoreAudioFocus() {
        return mPlayerConfig.isIgnoreAudioFocus();
    }

    public final void setMaxIDLETime(int minutes) {
        mMaxIDLEMinutes = minutes;

        if (minutes <= 0) {
            cancelIDLETimer();
            return;
        }

        startIDLETimer();
    }

    private void startIDLETimer() {
        cancelIDLETimer();
        if (mMaxIDLEMinutes <= 0 || notIDLE()) {
            return;
        }

        mIDLETimerDisposable = Observable.timer(mMaxIDLEMinutes, TimeUnit.MINUTES)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) {
                        shutdown();
                    }
                });
    }

    private boolean notIDLE() {
        return (isPreparing() || isStalled()) || (getPlaybackState() == PlaybackState.PLAYING);
    }

    private void cancelIDLETimer() {
        if (mIDLETimerDisposable != null && !mIDLETimerDisposable.isDisposed()) {
            mIDLETimerDisposable.dispose();
        }
    }

    protected final MediaSessionCompat getMediaSession() {
        return mMediaSession;
    }

    protected final void addCustomAction(@NonNull String action, @NonNull CustomAction customAction) {
        mAllCustomAction.put(action, customAction);
    }

    public Intent buildCustomActionIntent(@NonNull String actionName) {
        Preconditions.checkNotNull(actionName);
        return buildCustomActionIntent(actionName, this.getClass());
    }

    public static Intent buildCustomActionIntent(@NonNull String actionName,
                                                 @NonNull Class<? extends PlayerService> service) {
        Preconditions.checkNotNull(actionName);
        Preconditions.checkNotNull(service);

        Intent intent = new Intent(service.getName());
        intent.putExtra(CUSTOM_ACTION_NAME, actionName);

        return intent;
    }

    @NonNull
    public static String getPersistenceId(@NonNull Class<? extends PlayerService> service)
            throws IllegalArgumentException {
        Preconditions.checkNotNull(service);

        PersistenceId annotation = service.getAnnotation(PersistenceId.class);
        if (annotation == null) {
            return service.getName();
        }

        String persistenceId = annotation.value();
        if (persistenceId.isEmpty()) {
            throw new IllegalArgumentException("Persistence ID is empty.");
        }

        return persistenceId;
    }

    protected final void removeCustomAction(@NonNull String action) {
        mAllCustomAction.remove(action);
    }

    private void notifyOnShutdown() {
        if (mPlayerState.isSleepTimerStarted()) {
            cancelSleepTimer();
        }

        mPlayerStateListener.onShutdown();
        mMediaSession.sendSessionEvent(SESSION_EVENT_ON_SHUTDOWN, null);
    }

    public final PlayMode getPlayMode() {
        return mPlayerState.getPlayMode();
    }

    @Nullable
    public final Bundle getPlaylistExtra() {
        return mPlayer.getPlaylistExtra();
    }

    @NonNull
    public final PlaybackState getPlaybackState() {
        return mPlayer.getPlaybackState();
    }

    public final boolean isStalled() {
        return mPlayer.isStalled();
    }

    public final MusicItem getPlayingMusicItem() {
        return mPlayerState.getMusicItem();
    }

    public final boolean isError() {
        return getErrorCode() != ErrorCode.NO_ERROR;
    }

    public final int getErrorCode() {
        return mPlayerState.getErrorCode();
    }

    public final String getErrorMessage() {
        return ErrorCode.getErrorMessage(this, getErrorCode());
    }

    public final void updateNotificationView() {
        if (noNotificationView()) {
            return;
        }

        MusicItem musicItem = getPlayingMusicItem();
        if (musicItem == null || shouldClearNotification()) {
            stopForegroundEx(true);
            return;
        }

        if (shouldBeForeground() && !isForeground()) {
            startForeground();
            return;
        }

        if (!shouldBeForeground() && isForeground()) {
            stopForegroundEx(false);
        }

        updateNotification();
    }

    private boolean shouldClearNotification() {
        if (mNotificationView == null) {
            return true;
        }

        return mPlayerState.getPlaybackState() == PlaybackState.STOPPED;
    }

    private boolean noNotificationView() {
        return mNotificationView == null;
    }

    private boolean shouldBeForeground() {
        return mPlayer.getPlaybackState() == PlaybackState.PLAYING;
    }

    protected final boolean isForeground() {
        return mForeground;
    }

    protected final void startForeground() {
        if (noNotificationView()) {
            return;
        }

        if (getPlayingMusicItem() == null) {
            stopForegroundEx(true);
            return;
        }

        if (isBackgroundRestricted()) {
            mForeground = false;
            updateNotification();
            return;
        }

        mForeground = true;
        startForeground(mNotificationView.getNotificationId(),
                mNotificationView.createNotification());
    }

    private boolean isBackgroundRestricted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            return activityManager.isBackgroundRestricted();
        }

        return false;
    }

    protected final void stopForegroundEx(boolean removeNotification) {
        mForeground = false;
        stopForeground(removeNotification);
    }

    public final void setPlaylist(@NonNull Playlist playlist) {
        setPlaylist(playlist, 0, false);
    }

    public final void setPlaylist(@NonNull Playlist playlist, boolean play) {
        setPlaylist(playlist, 0, play);
    }

    @Override
    public void insertMusicItem(int position, @NonNull MusicItem musicItem) throws IllegalArgumentException {
        if (position < 0) {
            throw new IllegalArgumentException("position must >= 0.");
        }
        Preconditions.checkNotNull(musicItem);
        mPlayer.insertMusicItem(position, musicItem);
    }

    @Override
    public void appendMusicItem(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        mPlayer.appendMusicItem(musicItem);
    }

    @Override
    public void moveMusicItem(int fromPosition, int toPosition) throws IllegalArgumentException {
        if (fromPosition < 0) {
            throw new IllegalArgumentException("fromPosition must >= 0.");
        }

        if (toPosition < 0) {
            throw new IllegalArgumentException("toPosition must >= 0.");
        }

        mPlayer.moveMusicItem(fromPosition, toPosition);
    }

    @Override
    public void removeMusicItem(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        mPlayer.removeMusicItem(musicItem);
    }

    @Override
    public void removeMusicItem(int position) {
        mPlayer.removeMusicItem(position);
    }

    @Override
    public void setNextPlay(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        mPlayer.setNextPlay(musicItem);
    }

    @Override
    public final void setPlaylist(@NonNull Playlist playlist, final int position, final boolean play)
            throws IllegalArgumentException {
        Preconditions.checkNotNull(playlist);
        if (position < 0) {
            throw new IllegalArgumentException("position must >= 0.");
        }

        mPlayer.setPlaylist(playlist, position, play);
    }

    private void updateNotification() {
        if (noNotificationView()) {
            return;
        }

        if (getPlayingMusicItem() == null) {
            stopForegroundEx(true);
            return;
        }

        mNotificationManager.notify(mNotificationView.getNotificationId(),
                mNotificationView.createNotification());
    }

    @Deprecated
    protected boolean isCached(MusicItem musicItem, SoundQuality soundQuality) {
        return false;
    }

    protected void isCached(@NonNull MusicItem musicItem, @NonNull SoundQuality soundQuality, @NonNull AsyncResult<Boolean> result) {
        result.onSuccess(isCached(musicItem, soundQuality));
    }

    @NonNull
    protected MusicPlayer onCreateMusicPlayer(@NonNull Context context, @NonNull MusicItem musicItem, @NonNull Uri uri) {
        return new MediaMusicPlayer(context, uri);
    }

    @Nullable
    protected AudioManager.OnAudioFocusChangeListener onCreateAudioFocusChangeListener() {
        return null;
    }

    @Deprecated
    @SuppressWarnings("RedundantThrows")
    protected Uri onRetrieveMusicItemUri(@NonNull MusicItem musicItem, @NonNull SoundQuality soundQuality) throws Exception {
        return Uri.parse(musicItem.getUri());
    }

    protected void onRetrieveMusicItemUri(@NonNull MusicItem musicItem,
                                          @NonNull SoundQuality soundQuality,
                                          @NonNull AsyncResult<Uri> result) {
        try {
            result.onSuccess(onRetrieveMusicItemUri(musicItem, soundQuality));
        } catch (Exception e) {
            if (!result.isCancelled()) {
                result.onError(e);
            }
        }
    }

    @NonNull
    public final Player getPlayer() {
        return mPlayer;
    }

    private void onStopped() {
        if (noNotificationView()) {
            return;
        }

        stopForegroundEx(true);
    }

    private void onPlayingMusicItemChanged(@Nullable MusicItem musicItem) {
        if (mNotificationView != null && musicItem != null) {
            mNotificationView.setPlayingMusicItem(musicItem);
        }

        updateNotificationView();

        if (mHistoryRecorder != null && musicItem != null) {
            mHistoryRecorder.recordHistory(musicItem);
        }
    }

    protected boolean onMediaButtonEvent(Intent mediaButtonEvent) {
        return mHeadsetHookHelper.handleMediaButton(mediaButtonEvent);
    }

    protected void onCustomAction(String action, Bundle extras) {
        if (CUSTOM_ACTION_SHUTDOWN.equals(action)) {
            shutdown();
            return;
        }

        if (mCustomActionDispatcher.dispatch(action, extras)) {
            return;
        }

        handleCustomAction(action, extras);
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

    @Override
    public void getPlaylist(@NonNull Callback callback) {
        Preconditions.checkNotNull(callback);
        mPlaylistManager.getPlaylist(callback);
    }

    @Override
    public long getLastModified() {
        return mPlaylistManager.getLastModified();
    }

    @Override
    public void startSleepTimer(long time, @NonNull final TimeoutAction action) throws IllegalArgumentException {
        if (time < 0) {
            throw new IllegalArgumentException("time must >= 0");
        }
        Preconditions.checkNotNull(action);

        disposeLastSleepTimer();

        if (getPlayingMusicItem() == null) {
            return;
        }

        if (time == 0) {
            getPlayer().pause();
            return;
        }

        mSleepTimerDisposable = Observable.timer(time, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) {
                        switch (action) {
                            case PAUSE:
                                PlayerService.this.getPlayer().pause();
                                break;
                            case STOP:
                                PlayerService.this.getPlayer().stop();
                                break;
                            case SHUTDOWN:
                                PlayerService.this.shutdown();
                                break;
                        }
                        notifySleepTimerEnd();
                    }
                });

        long startTime = SystemClock.elapsedRealtime();
        mPlayerStateHelper.onSleepTimerStart(time, startTime, action);
        mSleepTimerStateChangedListener.onTimerStart(time, startTime, action);
    }

    @Override
    public void cancelSleepTimer() {
        disposeLastSleepTimer();
        notifySleepTimerEnd();
    }

    private void disposeLastSleepTimer() {
        if (mSleepTimerDisposable == null || mSleepTimerDisposable.isDisposed()) {
            return;
        }

        mSleepTimerDisposable.dispose();
    }

    private void notifySleepTimerEnd() {
        mPlayerStateHelper.onSleepTimerEnd();
        mSleepTimerStateChangedListener.onTimerEnd();
    }

    private void notifyPlayModeChanged(@NonNull PlayMode playMode) {
        if (mNotificationView != null) {
            mNotificationView.onPlayModeChanged(playMode);
        }
    }

    private class PlayerImp extends AbstractPlayer {

        public PlayerImp(@NonNull Context context,
                         @NonNull PlayerConfig playerConfig,
                         @NonNull PlayerState playlistState,
                         @NonNull PlaylistManagerImp playlistManager,
                         @NonNull Class<? extends PlayerService> playerService,
                         @NonNull AbstractPlayer.OnStateChangeListener listener) {
            super(context, playerConfig, playlistState, playlistManager, playerService, listener);
        }

        @Override
        protected void isCached(@NonNull MusicItem musicItem, @NonNull SoundQuality soundQuality, @NonNull AsyncResult<Boolean> result) {
            PlayerService.this.isCached(musicItem, soundQuality, result);
        }

        @NonNull
        @Override
        protected MusicPlayer onCreateMusicPlayer(@NonNull Context context, @NonNull MusicItem musicItem, @NonNull Uri uri) {
            return PlayerService.this.onCreateMusicPlayer(context, musicItem, uri);
        }

        @Nullable
        @Override
        protected AudioManager.OnAudioFocusChangeListener onCreateAudioFocusChangeListener() {
            return PlayerService.this.onCreateAudioFocusChangeListener();
        }

        @Override
        protected void retrieveMusicItemUri(@NonNull MusicItem musicItem,
                                            @NonNull SoundQuality soundQuality,
                                            @NonNull AsyncResult<Uri> result) throws Exception {
            PlayerService.this.onRetrieveMusicItemUri(musicItem, soundQuality, result);
        }
    }

    public static class MediaSessionCallback extends MediaSessionCompat.Callback {
        private final PlayerService mPlayerService;
        private final Player mPlayer;

        public MediaSessionCallback(@NonNull PlayerService playerService) {
            Preconditions.checkNotNull(playerService);
            mPlayerService = playerService;
            mPlayer = mPlayerService.getPlayer();
        }

        @NonNull
        public PlayerService getPlayerService() {
            return mPlayerService;
        }

        public MediaSessionCompat getMediaSession() {
            return mPlayerService.getMediaSession();
        }

        public Player getPlayer() {
            return mPlayer;
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            if (mPlayerService.onMediaButtonEvent(mediaButtonEvent)) {
                return true;
            }

            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            mPlayerService.onCustomAction(action, extras);
        }

        @Override
        public void onPlay() {
            mPlayer.play();
        }

        @Override
        public void onSkipToQueueItem(long id) {
            mPlayer.skipToPosition((int) id);
        }

        @Override
        public void onPause() {
            mPlayer.pause();
        }

        @Override
        public void onSkipToNext() {
            mPlayer.skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            mPlayer.skipToPrevious();
        }

        @Override
        public void onFastForward() {
            mPlayer.fastForward();
        }

        @Override
        public void onRewind() {
            mPlayer.rewind();
        }

        @Override
        public void onStop() {
            mPlayer.stop();
        }

        @Override
        public void onSeekTo(long pos) {
            mPlayer.seekTo((int) pos);
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
                mPlayer.setPlayMode(PlayMode.LOOP);
                return;
            }

            mPlayer.setPlayMode(PlayMode.PLAYLIST_LOOP);
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE ||
                    shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_INVALID) {
                mPlayer.setPlayMode(PlayMode.PLAYLIST_LOOP);
                return;
            }

            mPlayer.setPlayMode(PlayMode.SHUFFLE);
        }
    }

    public static abstract class NotificationView {
        public static final String CHANNEL_ID = "player";

        private PlayerService mPlayerService;

        private MusicItem mPlayingMusicItem;
        private boolean mExpire;

        private Bitmap mDefaultIcon;
        private int mIconWidth;
        private int mIconHeight;

        private Bitmap mIcon;
        private BetterIconLoader mBetterIconLoader;
        private Disposable mIconLoaderDisposable;

        private boolean mReleased;

        private int mPendingIntentRequestCode;

        void init(PlayerService playerService) {
            mPlayerService = playerService;
            mPlayingMusicItem = new MusicItem();
            mDefaultIcon = loadDefaultIcon();
            mIcon = mDefaultIcon;
            mBetterIconLoader = onCreateBetterIconLoader(playerService);
            Preconditions.checkNotNull(mBetterIconLoader);

            setIconSize(playerService.getResources().getDimensionPixelSize(R.dimen.accepted_notif_icon_size_big));
            onInit(mPlayerService);
        }

        @NonNull
        private Bitmap loadDefaultIcon() {
            Context context = getContext();
            BitmapDrawable drawable = (BitmapDrawable) ResourcesCompat.getDrawable(
                    context.getResources(),
                    R.mipmap.accepted_notif_default_icon,
                    context.getTheme());

            if (drawable == null) {
                throw new NullPointerException();
            }

            return drawable.getBitmap();
        }

        private void reloadIcon() {
            disposeLastLoading();
            mIconLoaderDisposable = Single.create(new SingleOnSubscribe<Bitmap>() {
                @Override
                public void subscribe(@NonNull final SingleEmitter<Bitmap> emitter) {
                    mBetterIconLoader.loadIcon(getPlayingMusicItem(), mIconWidth, mIconHeight, new AsyncResult<Bitmap>() {
                        @Override
                        public void onSuccess(@NonNull Bitmap bitmap) {
                            emitter.onSuccess(bitmap);
                        }

                        @Override
                        public void onError(@NonNull Throwable throwable) {
                            throwable.printStackTrace();
                            emitter.onSuccess(getDefaultIcon());
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
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<Bitmap>() {
                        @Override
                        public void accept(Bitmap bitmap) {
                            setIcon(bitmap);
                        }
                    });
        }

        private void disposeLastLoading() {
            if (mIconLoaderDisposable != null && !mIconLoaderDisposable.isDisposed()) {
                mIconLoaderDisposable.dispose();
            }
        }

        protected void onInit(Context context) {
        }

        @Deprecated
        @NonNull
        protected IconLoader onCreateIconLoader(@NonNull Context context) {
            return new IconLoaderImp(context, getDefaultIcon());
        }

        @NonNull
        protected BetterIconLoader onCreateBetterIconLoader(@NonNull Context context) {
            return new IconLoaderCompat(onCreateIconLoader(context));
        }

        protected void onPlayModeChanged(@NonNull PlayMode playMode) {
        }

        @SuppressWarnings("EmptyMethod")
        protected void onRelease() {
        }

        public final PendingIntent buildCustomAction(String actionName, CustomAction customAction) {
            addCustomAction(actionName, customAction);

            mPendingIntentRequestCode += 1;

            Intent intent = mPlayerService.buildCustomActionIntent(actionName);
            return PendingIntent.getBroadcast(getContext(),
                    mPendingIntentRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        @NonNull
        public abstract Notification onCreateNotification();

        public abstract int getNotificationId();

        public final void shutdown() {
            mPlayerService.shutdown();
        }

        public final Bitmap getIcon() {
            return mIcon;
        }

        public final void setIcon(@NonNull Bitmap icon) {
            mIcon = icon;
            invalidate();
        }

        public final void setIconSize(int size) {
            setIconSize(size, size);
        }

        public final void setIconSize(int width, int height) {
            mIconWidth = Math.max(0, width);
            mIconHeight = Math.max(0, height);
        }

        public final void setDefaultIcon(@NonNull Bitmap bitmap) {
            Preconditions.checkNotNull(bitmap);
            mDefaultIcon = bitmap;

            if (mBetterIconLoader instanceof IconLoaderCompat) {
                IconLoaderCompat iconLoaderCompat = (IconLoaderCompat) mBetterIconLoader;
                iconLoaderCompat.setDefaultIcon(bitmap);
            }
        }

        @NonNull
        public final Bitmap getDefaultIcon() {
            return mDefaultIcon;
        }

        public final CharSequence getContentText(String contentText) {
            CharSequence text = contentText;

            if (isError()) {
                text = getErrorMessage();
                Resources res = getContext().getResources();
                SpannableString colorText = new SpannableString(text);
                colorText.setSpan(new ForegroundColorSpan(res.getColor(android.R.color.holo_red_dark)), 0, text.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                return colorText;
            }

            return text;
        }

        public final Context getContext() {
            return mPlayerService;
        }

        public final String getPackageName() {
            return getContext().getPackageName();
        }

        public final PlayMode getPlayMode() {
            return mPlayerService.getPlayMode();
        }

        @Nullable
        public final Bundle getPlaylistExtra() {
            return mPlayerService.getPlaylistExtra();
        }

        public final void addCustomAction(@NonNull String action, @NonNull CustomAction customAction) {
            mPlayerService.addCustomAction(action, customAction);
        }

        public final boolean isPreparing() {
            return mPlayerService.isPreparing();
        }

        public final boolean isPrepared() {
            return mPlayerService.isPrepared();
        }

        public final boolean isStalled() {
            return mPlayerService.isStalled();
        }

        @NonNull
        public final PlaybackState getPlaybackState() {
            return mPlayerService.getPlaybackState();
        }

        public final boolean isPlayingState() {
            return getPlaybackState() == PlaybackState.PLAYING;
        }

        public final boolean isError() {
            return getPlaybackState() == PlaybackState.ERROR;
        }

        @NonNull
        public final String getErrorMessage() {
            return mPlayerService.getErrorMessage();
        }

        public final boolean isReleased() {
            return mReleased;
        }

        @NonNull
        public final MusicItem getPlayingMusicItem() {
            return mPlayingMusicItem;
        }

        public final MediaSessionCompat getMediaSession() {
            return mPlayerService.getMediaSession();
        }

        public final boolean isExpire() {
            return mExpire;
        }

        public final void invalidate() {
            if (mReleased) {
                return;
            }

            mPlayerService.updateNotificationView();
        }

        @NonNull
        Notification createNotification() {
            if (mExpire) {
                reloadIcon();
            }
            Notification notification = onCreateNotification();
            mExpire = false;
            return notification;
        }

        void setPlayingMusicItem(@NonNull MusicItem musicItem) {
            Preconditions.checkNotNull(musicItem);
            if (mPlayingMusicItem.equals(musicItem)) {
                return;
            }

            mPlayingMusicItem = musicItem;
            mExpire = true;
        }

        void release() {
            onRelease();
            mReleased = true;
            disposeLastLoading();
        }

        @Deprecated
        public abstract static class IconLoader {
            private int mWidth;
            private int mHeight;
            private Bitmap mDefaultIcon;

            public IconLoader(@NonNull Bitmap defaultIcon) {
                Preconditions.checkNotNull(defaultIcon);
                mDefaultIcon = defaultIcon;
            }

            public abstract void loadIcon(@NonNull MusicItem musicItem, @NonNull Callback callback);

            public abstract void cancel();

            public void setWidth(int width) {
                mWidth = width;
            }

            public int getWidth() {
                return mWidth;
            }

            public void setHeight(int height) {
                mHeight = height;
            }

            public int getHeight() {
                return mHeight;
            }

            public void setDefaultIcon(@NonNull Bitmap bitmap) {
                Preconditions.checkNotNull(bitmap);
                mDefaultIcon = bitmap;
            }

            @NonNull
            public Bitmap getDefaultIcon() {
                return mDefaultIcon;
            }

            @Deprecated
            public interface Callback {
                void onIconLoaded(Bitmap bitmap);
            }
        }

        public interface BetterIconLoader {
            void loadIcon(@NonNull MusicItem musicItem, int width, int height, @NonNull AsyncResult<Bitmap> result);
        }

        private static class IconLoaderImp extends IconLoader {
            private Context mContext;
            private Disposable mLoadIconDisposable;
            private FutureTarget<Bitmap> mFutureTarget;

            IconLoaderImp(Context context, Bitmap defaultIcon) {
                super(defaultIcon);
                mContext = context;
            }

            @Override
            public void loadIcon(@NonNull final MusicItem musicItem, @NonNull final Callback callback) {
                cancelLastLoading();
                mLoadIconDisposable = Single.create(new SingleOnSubscribe<Bitmap>() {
                    @Override
                    public void subscribe(@NonNull SingleEmitter<Bitmap> emitter) {
                        // 1. load icon from internet
                        Bitmap bitmap = loadIconFromInternet(musicItem);

                        // check disposed
                        if (emitter.isDisposed()) {
                            return;
                        }

                        // 2. load embedded picture
                        if (bitmap == null) {
                            bitmap = loadEmbeddedPicture(musicItem);
                        }

                        // check disposed
                        if (emitter.isDisposed()) {
                            return;
                        }

                        // 3. load default icon
                        if (bitmap == null) {
                            bitmap = getDefaultIcon();
                        }

                        // check disposed
                        if (emitter.isDisposed()) {
                            return;
                        }

                        emitter.onSuccess(bitmap);
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<Bitmap>() {
                            @Override
                            public void accept(Bitmap bitmap) {
                                callback.onIconLoaded(bitmap);
                            }
                        });
            }

            @Override
            public void cancel() {
                cancelLastLoading();
            }

            private Bitmap loadIconFromInternet(MusicItem musicItem) {
                mFutureTarget = Glide.with(mContext)
                        .asBitmap()
                        .load(musicItem.getIconUri())
                        .submit(getWidth(), getHeight());

                try {
                    return mFutureTarget.get();
                } catch (ExecutionException | InterruptedException e) {
                    return null;
                }
            }

            private Bitmap loadEmbeddedPicture(MusicItem musicItem) {
                if (notLocaleMusic(musicItem)) {
                    return null;
                }

                MediaMetadataRetriever retriever = new MediaMetadataRetriever();

                try {
                    retriever.setDataSource(mContext, Uri.parse(musicItem.getUri()));
                    byte[] pictureData = retriever.getEmbeddedPicture();
                    return Glide.with(mContext)
                            .asBitmap()
                            .load(pictureData)
                            .submit(getWidth(), getHeight())
                            .get();
                } catch (IllegalArgumentException | ExecutionException | InterruptedException e) {
                    return null;
                } finally {
                    retriever.release();
                }
            }

            private boolean notLocaleMusic(MusicItem musicItem) {
                String stringUri = musicItem.getUri();
                String scheme = Uri.parse(stringUri).getScheme();

                return "http".equalsIgnoreCase(scheme) | "https".equalsIgnoreCase(scheme);
            }

            private void cancelLastLoading() {
                if (mLoadIconDisposable != null && !mLoadIconDisposable.isDisposed()) {
                    mLoadIconDisposable.dispose();
                }

                if (mFutureTarget != null && !mFutureTarget.isDone()) {
                    mFutureTarget.cancel(true);
                }
            }
        }

        private static class IconLoaderCompat implements BetterIconLoader {
            private IconLoader mIconLoader;

            IconLoaderCompat(IconLoader iconLoader) {
                mIconLoader = iconLoader;
            }

            @Override
            public void loadIcon(@NonNull MusicItem musicItem, int width, int height, @NonNull final AsyncResult<Bitmap> result) {
                mIconLoader.setWidth(width);
                mIconLoader.setHeight(height);
                mIconLoader.loadIcon(musicItem, new IconLoader.Callback() {
                    @Override
                    public void onIconLoaded(Bitmap bitmap) {
                        result.onSuccess(bitmap);
                    }
                });

                result.setOnCancelListener(new AsyncResult.OnCancelListener() {
                    @Override
                    public void onCancelled() {
                        mIconLoader.cancel();
                    }
                });
            }

            void setDefaultIcon(Bitmap defaultIcon) {
                mIconLoader.setDefaultIcon(defaultIcon);
            }
        }
    }

    public final boolean isPreparing() {
        return mPlayer.isPreparing();
    }

    public final boolean isPrepared() {
        return mPlayer.isPrepared();
    }

    public static class MediaNotificationView extends NotificationView {
        private static final String ACTION_SKIP_TO_PREVIOUS = "__skip_to_previous";
        private static final String ACTION_PLAY_PAUSE = "__play_pause";
        private static final String ACTION_SKIP_TO_NEXT = "__skip_to_next";

        private PendingIntent mSkipToPrevious;
        private PendingIntent mPlayPause;
        private PendingIntent mSkipToNext;

        @Override
        protected void onInit(Context context) {
            initAllPendingIntent();
        }

        private void initAllPendingIntent() {
            mSkipToPrevious = buildCustomAction(ACTION_SKIP_TO_PREVIOUS, new CustomAction() {
                @Override
                public void doAction(@NonNull Player player, @Nullable Bundle extras) {
                    player.skipToPrevious();
                }
            });

            mPlayPause = buildCustomAction(ACTION_PLAY_PAUSE, new CustomAction() {
                @Override
                public void doAction(@NonNull Player player, @Nullable Bundle extras) {
                    player.playPause();
                }
            });

            mSkipToNext = buildCustomAction(ACTION_SKIP_TO_NEXT, new CustomAction() {
                @Override
                public void doAction(@NonNull Player player, @Nullable Bundle extras) {
                    player.skipToNext();
                }
            });
        }

        public final PendingIntent doSkipToPrevious() {
            return mSkipToPrevious;
        }

        public final PendingIntent doPlayPause() {
            return mPlayPause;
        }

        public final PendingIntent doSkipToNext() {
            return mSkipToNext;
        }

        @NonNull
        @Override
        public Notification onCreateNotification() {
            androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                    new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(getMediaSession().getSessionToken());

            onBuildMediaStyle(mediaStyle);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                    .setSmallIcon(getSmallIconId())
                    .setLargeIcon(getIcon())
                    .setContentTitle(MusicItemUtil.getTitle(getContext(), getPlayingMusicItem()))
                    .setContentText(getContentText(MusicItemUtil.getArtist(getContext(), getPlayingMusicItem())))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setShowWhen(false)
                    .setAutoCancel(false)
                    .setStyle(mediaStyle);

            onBuildNotification(builder);

            return builder.build();
        }

        @Override
        public int getNotificationId() {
            return 1024;
        }

        @DrawableRes
        public int getSmallIconId() {
            return R.mipmap.accepted_ic_notif_small_icon;
        }

        protected void onBuildMediaStyle(androidx.media.app.NotificationCompat.MediaStyle mediaStyle) {
            mediaStyle.setShowActionsInCompactView(1, 2);
        }

        protected void onBuildNotification(NotificationCompat.Builder builder) {
            builder.addAction(R.mipmap.accepted_ic_notif_skip_to_previous, "skip_to_previous", doSkipToPrevious());

            if (isPlayingState()) {
                builder.addAction(R.mipmap.accepted_ic_notif_pause, "pause", doPlayPause());
            } else {
                builder.addAction(R.mipmap.accepted_ic_notif_play, "play", doPlayPause());
            }

            builder.addAction(R.mipmap.accepted_ic_notif_skip_to_next, "skip_to_next", doSkipToNext());
        }
    }

    public interface CustomAction {
        void doAction(@NonNull Player player, @Nullable Bundle extras);
    }

    private static class KeepAliveConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // ignore
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // ignore
        }
    }
}
