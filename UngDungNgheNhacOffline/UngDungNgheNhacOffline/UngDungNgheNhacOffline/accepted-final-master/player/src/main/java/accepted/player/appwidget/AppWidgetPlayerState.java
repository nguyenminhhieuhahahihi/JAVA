package accepted.player.appwidget;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.tencent.mmkv.MMKV;

import accepted.player.PlayMode;
import accepted.player.PlaybackState;
import accepted.player.PlayerService;
import accepted.player.audio.MusicItem;

public class AppWidgetPlayerState implements Parcelable {
    public static final String ACTION_PLAYER_STATE_CHANGED = "accepted.player.appwidget.action.PLAYER_STATE_CHANGED";

    private static final String KEY_PLAYER_STATE = "PLAYER_STATE";
    private static boolean sMMKVInitialized;

    private final PlaybackState mPlaybackState;
    @Nullable
    private final MusicItem mPlayingMusicItem;
    private final PlayMode mPlayMode;
    private final long mPlayProgress;
    private final float mSpeed;
    private final long mPlayProgressUpdateTime;
    private final boolean mPreparing;
    private final boolean mPrepared;
    private final boolean mStalled;
    private final String mErrorMessage;

    public AppWidgetPlayerState(@NonNull PlaybackState playbackState,
                                @Nullable MusicItem playingMusicItem,
                                @NonNull PlayMode playMode,
                                float speed,
                                long playProgress,
                                long playProgressUpdateTime,
                                boolean preparing,
                                boolean prepared,
                                boolean stalled,
                                @NonNull String errorMessage) {
        Preconditions.checkNotNull(playbackState);
        Preconditions.checkNotNull(playMode);
        Preconditions.checkNotNull(errorMessage);

        mPlaybackState = playbackState;
        mPlayingMusicItem = playingMusicItem;
        mPlayMode = playMode;
        mSpeed = speed;
        mPlayProgress = playProgress;
        mPlayProgressUpdateTime = playProgressUpdateTime;
        mPreparing = preparing;
        mPrepared = prepared;
        mStalled = stalled;
        mErrorMessage = errorMessage;
    }

    public static AppWidgetPlayerState emptyState() {
        return new AppWidgetPlayerState(
                PlaybackState.NONE,
                new MusicItem(),
                PlayMode.PLAYLIST_LOOP,
                1.0F,
                0,
                0,
                false,
                false,
                false,
                ""
        );
    }

    public static AppWidgetPlayerState getPlayerState(@NonNull Context context, @NonNull Class<? extends PlayerService> playerService) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerService);

        MMKV mmkv = getMMKV(context, playerService);
        return mmkv.decodeParcelable(KEY_PLAYER_STATE, AppWidgetPlayerState.class, emptyState());
    }

    public static void updatePlayerState(@NonNull Context context,
                                         @NonNull Class<? extends PlayerService> playerService,
                                         @NonNull AppWidgetPlayerState playerState) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerService);
        Preconditions.checkNotNull(playerState);

        MMKV mmkv = getMMKV(context, playerService);
        mmkv.encode(KEY_PLAYER_STATE, playerState);

        Intent intent = new Intent(ACTION_PLAYER_STATE_CHANGED);
        intent.addCategory(playerService.getName());
        context.sendBroadcast(intent);
    }

    private static MMKV getMMKV(@NonNull Context context, @NonNull Class<? extends PlayerService> playerService) {
        if (!sMMKVInitialized) {
            sMMKVInitialized = true;
            MMKV.initialize(context);
        }

        String persistenceId = PlayerService.getPersistenceId(playerService);
        return MMKV.mmkvWithID("AppWidgetPlayerState:" + persistenceId, MMKV.MULTI_PROCESS_MODE);
    }

    @NonNull
    public PlaybackState getPlaybackState() {
        return mPlaybackState;
    }

    @Nullable
    public MusicItem getPlayingMusicItem() {
        return mPlayingMusicItem;
    }

    @NonNull
    public PlayMode getPlayMode() {
        return mPlayMode;
    }

    public float getSpeed() {
        return mSpeed;
    }

    public long getPlayProgress() {
        return mPlayProgress;
    }

    public long getPlayProgressUpdateTime() {
        return mPlayProgressUpdateTime;
    }

    public boolean isPreparing() {
        return mPreparing;
    }

    public boolean isPrepared() {
        return mPrepared;
    }

    public boolean isStalled() {
        return mStalled;
    }

    @NonNull
    public String getErrorMessage() {
        return mErrorMessage;
    }

    public static boolean isServiceAlive(Context context, Class<? extends PlayerService> playerService) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo serviceInfo : am.getRunningServices(100)) {
            if (serviceInfo.service.equals(new ComponentName(context, playerService))) {
                return true;
            }
        }

        return false;
    }

    // ----------------------- Parcelable -----------------------

    protected AppWidgetPlayerState(Parcel in) {
        mPlaybackState = PlaybackState.values()[in.readInt()];
        mPlayingMusicItem = in.readParcelable(MusicItem.class.getClassLoader());
        mPlayMode = PlayMode.values()[in.readInt()];
        mSpeed = in.readFloat();
        mPlayProgress = in.readLong();
        mPlayProgressUpdateTime = in.readLong();
        mPreparing = in.readByte() != 0;
        mPrepared = in.readByte() != 0;
        mStalled = in.readByte() != 0;
        mErrorMessage = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPlaybackState.ordinal());
        dest.writeParcelable(mPlayingMusicItem, flags);
        dest.writeInt(mPlayMode.ordinal());
        dest.writeFloat(mSpeed);
        dest.writeLong(mPlayProgress);
        dest.writeLong(mPlayProgressUpdateTime);
        dest.writeByte((byte) (mPreparing ? 1 : 0));
        dest.writeByte((byte) (mPrepared ? 1 : 0));
        dest.writeByte((byte) (mStalled ? 1 : 0));
        dest.writeString(mErrorMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AppWidgetPlayerState> CREATOR = new Creator<AppWidgetPlayerState>() {
        @Override
        public AppWidgetPlayerState createFromParcel(Parcel in) {
            return new AppWidgetPlayerState(in);
        }

        @Override
        public AppWidgetPlayerState[] newArray(int size) {
            return new AppWidgetPlayerState[size];
        }
    };
}
