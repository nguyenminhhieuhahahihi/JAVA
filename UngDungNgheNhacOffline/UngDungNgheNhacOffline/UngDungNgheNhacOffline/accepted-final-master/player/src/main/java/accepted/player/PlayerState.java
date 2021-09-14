package accepted.player;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import accepted.player.audio.MusicItem;
import accepted.player.audio.ErrorCode;

class PlayerState implements Parcelable {
    private int playProgress;
    @Nullable
    private MusicItem musicItem;
    private int playPosition;
    private PlayMode playMode;
    private float speed;

    // no persistent
    private long playProgressUpdateTime;
    private PlaybackState playbackState;
    private boolean preparing;
    private boolean prepared;
    private int audioSessionId;
    private int bufferedProgress;
    private boolean stalled;
    private int errorCode;
    private String errorMessage;
    private boolean sleepTimerStarted;
    private long sleepTimerTime;
    private long sleepTimerStartTime;
    private SleepTimer.TimeoutAction timeoutAction;

    public PlayerState() {
        playProgress = 0;
        playProgressUpdateTime = 0;
        playPosition = 0;
        playMode = PlayMode.PLAYLIST_LOOP;
        speed = 1.0F;

        playbackState = PlaybackState.NONE;
        preparing = false;
        prepared = false;
        audioSessionId = 0;
        bufferedProgress = 0;
        stalled = false;
        errorCode = ErrorCode.NO_ERROR;
        errorMessage = "";
        sleepTimerStarted = false;
        sleepTimerTime = 0;
        sleepTimerStartTime = 0;
        timeoutAction = SleepTimer.TimeoutAction.PAUSE;
    }

    public PlayerState(PlayerState source) {
        playProgress = source.playProgress;
        playProgressUpdateTime = source.playProgressUpdateTime;
        if (source.musicItem != null) {
            musicItem = new MusicItem(source.musicItem);
        }
        playPosition = source.playPosition;
        playMode = source.playMode;
        speed = source.speed;

        playbackState = source.playbackState;
        preparing = source.preparing;
        prepared = source.prepared;
        audioSessionId = source.audioSessionId;
        bufferedProgress = source.bufferedProgress;
        stalled = source.stalled;
        errorCode = source.errorCode;
        errorMessage = source.errorMessage;
        sleepTimerStarted = source.sleepTimerStarted;
        sleepTimerTime = source.sleepTimerTime;
        sleepTimerStartTime = source.sleepTimerStartTime;
        timeoutAction = source.timeoutAction;
    }

    public int getPlayProgress() {
        return playProgress;
    }

    public void setPlayProgress(int playProgress) {
        if (playProgress < 0) {
            this.playProgress = 0;
            return;
        }

        this.playProgress = playProgress;
    }

    public long getPlayProgressUpdateTime() {
        return playProgressUpdateTime;
    }

    public void setPlayProgressUpdateTime(long updateTime) {
        playProgressUpdateTime = updateTime;
    }

    @Nullable
    public MusicItem getMusicItem() {
        return musicItem;
    }

    public void setMusicItem(@Nullable MusicItem musicItem) {
        this.musicItem = musicItem;
    }

    public int getPlayPosition() {
        return playPosition;
    }

    public void setPlayPosition(int playPosition) {
        if (playPosition < 0) {
            this.playPosition = 0;
            return;
        }

        this.playPosition = playPosition;
    }

    public PlayMode getPlayMode() {
        return playMode;
    }

    public void setPlayMode(@NonNull PlayMode playMode) {
        this.playMode = playMode;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public PlaybackState getPlaybackState() {
        return playbackState;
    }

    public void setPlaybackState(@NonNull PlaybackState playbackState) {
        Preconditions.checkNotNull(playbackState);
        this.playbackState = playbackState;

        if (playbackState != PlaybackState.ERROR) {
            errorCode = ErrorCode.NO_ERROR;
            errorMessage = "";
        }
    }

    public boolean isPreparing() {
        return preparing;
    }

    public void setPreparing(boolean preparing) {
        this.preparing = preparing;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    public int getAudioSessionId() {
        return audioSessionId;
    }

    public void setAudioSessionId(int audioSessionId) {
        this.audioSessionId = audioSessionId;
    }

    public int getBufferedProgress() {
        return bufferedProgress;
    }

    public void setBufferedProgress(int bufferedProgress) {
        if (bufferedProgress < 0) {
            this.bufferedProgress = 0;
            return;
        }

        this.bufferedProgress = bufferedProgress;
    }

    public boolean isStalled() {
        return stalled;
    }

    public void setStalled(boolean stalled) {
        this.stalled = stalled;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@NonNull String errorMessage) {
        Preconditions.checkNotNull(errorMessage);
        this.errorMessage = errorMessage;
    }

    public boolean isForbidSeek() {
        if (musicItem == null) {
            return true;
        }

        return musicItem.isForbidSeek();
    }

    public boolean isSleepTimerStarted() {
        return sleepTimerStarted;
    }

    public void setSleepTimerStarted(boolean sleepTimerStarted) {
        this.sleepTimerStarted = sleepTimerStarted;
    }

    public long getSleepTimerTime() {
        return sleepTimerTime;
    }

    public void setSleepTimerTime(long time) {
        this.sleepTimerTime = time;
    }

    public long getSleepTimerStartTime() {
        return sleepTimerStartTime;
    }

    public void setSleepTimerStartTime(long sleepTimerStartTime) {
        this.sleepTimerStartTime = sleepTimerStartTime;
    }

    @NonNull
    public SleepTimer.TimeoutAction getTimeoutAction() {
        return timeoutAction;
    }

    public void setTimeoutAction(@NonNull SleepTimer.TimeoutAction action) {
        Preconditions.checkNotNull(timeoutAction);
        this.timeoutAction = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerState)) return false;

        PlayerState other = (PlayerState) o;

        return Objects.equal(playProgress, other.playProgress)
                && Objects.equal(playProgressUpdateTime, other.playProgressUpdateTime)
                && Objects.equal(musicItem, other.musicItem)
                && Objects.equal(playPosition, other.playPosition)
                && Objects.equal(playMode, other.playMode)
                && Objects.equal(speed, other.speed)
                && Objects.equal(playbackState, other.playbackState)
                && Objects.equal(preparing, other.preparing)
                && Objects.equal(prepared, other.prepared)
                && Objects.equal(audioSessionId, other.audioSessionId)
                && Objects.equal(bufferedProgress, other.bufferedProgress)
                && Objects.equal(stalled, other.stalled)
                && Objects.equal(errorCode, other.errorCode)
                && Objects.equal(errorMessage, other.errorMessage)
                && Objects.equal(sleepTimerStarted, other.sleepTimerStarted)
                && Objects.equal(sleepTimerTime, other.sleepTimerTime)
                && Objects.equal(sleepTimerStartTime, other.sleepTimerStartTime)
                && Objects.equal(timeoutAction, other.timeoutAction);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(playProgress,
                playProgressUpdateTime,
                musicItem,
                playPosition,
                playMode,
                speed,
                playbackState,
                preparing,
                prepared,
                audioSessionId,
                bufferedProgress,
                stalled,
                errorCode,
                errorMessage,
                sleepTimerStarted,
                sleepTimerTime,
                sleepTimerStartTime,
                timeoutAction);
    }

    @NonNull
    @Override
    public String toString() {
        return "PlayerState{" +
                "playProgress=" + playProgress +
                ", playProgressUpdateTime=" + playProgressUpdateTime +
                ", musicItem=" + musicItem +
                ", playPosition=" + playPosition +
                ", playMode=" + playMode +
                ", speed=" + speed +
                ", playbackState=" + playbackState +
                ", preparing=" + preparing +
                ", prepared=" + prepared +
                ", audioSessionId=" + audioSessionId +
                ", bufferingPercent=" + bufferedProgress +
                ", stalled=" + stalled +
                ", errorCode=" + errorCode +
                ", errorMessage='" + errorMessage + '\'' +
                ", sleepTimerStarted=" + sleepTimerStarted +
                ", sleepTimerTime=" + sleepTimerTime +
                ", sleepTimerStartTime=" + sleepTimerStartTime +
                ", timeoutAction=" + timeoutAction +
                '}';
    }

    protected PlayerState(Parcel in) {
        playProgress = in.readInt();
        playProgressUpdateTime = in.readLong();
        musicItem = in.readParcelable(Thread.currentThread().getContextClassLoader());
        playPosition = in.readInt();
        playMode = PlayMode.values()[in.readInt()];
        speed = in.readFloat();

        playbackState = PlaybackState.values()[in.readInt()];
        preparing = in.readByte() != 0;
        prepared = in.readByte() != 0;
        audioSessionId = in.readInt();
        bufferedProgress = in.readInt();
        stalled = in.readByte() != 0;
        errorCode = in.readInt();
        errorMessage = in.readString();
        sleepTimerStarted = in.readByte() != 0;
        sleepTimerTime = in.readLong();
        sleepTimerStartTime = in.readLong();
        timeoutAction = SleepTimer.TimeoutAction.values()[in.readInt()];
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(playProgress);
        dest.writeLong(playProgressUpdateTime);
        dest.writeParcelable(musicItem, flags);
        dest.writeInt(playPosition);
        dest.writeInt(playMode.ordinal());
        dest.writeFloat(speed);

        dest.writeInt(playbackState.ordinal());
        dest.writeByte((byte) (preparing ? 1 : 0));
        dest.writeByte((byte) (prepared ? 1 : 0));
        dest.writeInt(audioSessionId);
        dest.writeInt(bufferedProgress);
        dest.writeByte((byte) (stalled ? 1 : 0));
        dest.writeInt(errorCode);
        dest.writeString(errorMessage);
        dest.writeByte((byte) (sleepTimerStarted ? 1 : 0));
        dest.writeLong(sleepTimerTime);
        dest.writeLong(sleepTimerStartTime);
        dest.writeInt(timeoutAction.ordinal());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PlayerState> CREATOR = new Creator<PlayerState>() {
        @Override
        public PlayerState createFromParcel(Parcel in) {
            return new PlayerState(in);
        }

        @Override
        public PlayerState[] newArray(int size) {
            return new PlayerState[size];
        }
    };
}
