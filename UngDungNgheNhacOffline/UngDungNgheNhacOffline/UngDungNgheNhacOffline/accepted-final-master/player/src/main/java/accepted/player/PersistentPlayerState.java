package accepted.player;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.tencent.mmkv.MMKV;

import accepted.player.audio.MusicItem;

class PersistentPlayerState extends PlayerState {
    private static final String KEY_PLAY_PROGRESS = "play_progress";
    private static final String KEY_MUSIC_ITEM = "music_item";
    private static final String KEY_PLAY_POSITION = "position";
    private static final String KEY_PLAY_MODE = "play_mode";
    private static final String KEY_SPEED = "speed";

    private final MMKV mMMKV;

    public PersistentPlayerState(@NonNull Context context, @NonNull String id) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(id);

        MMKV.initialize(context);

        mMMKV = MMKV.mmkvWithID("PlayerState:" + id);

        super.setMusicItem(mMMKV.decodeParcelable(KEY_MUSIC_ITEM, MusicItem.class));
        super.setPlayPosition(mMMKV.decodeInt(KEY_PLAY_POSITION, 0));
        super.setPlayMode(PlayMode.getBySerialId(mMMKV.decodeInt(KEY_PLAY_MODE, 0)));
        super.setSpeed(mMMKV.getFloat(KEY_SPEED, 1.0F));

        if (isForbidSeek()) {
            super.setPlayProgress(0);
            return;
        }

        super.setPlayProgress(mMMKV.decodeInt(KEY_PLAY_PROGRESS, 0));
    }

    @Override
    public void setPlayProgress(int playProgress) {
        super.setPlayProgress(playProgress);

        if (isForbidSeek()) {
            mMMKV.encode(KEY_PLAY_PROGRESS, 0);
            return;
        }

        mMMKV.encode(KEY_PLAY_PROGRESS, playProgress);
    }

    @Override
    public void setMusicItem(@Nullable MusicItem musicItem) {
        super.setMusicItem(musicItem);

        if (musicItem == null) {
            mMMKV.remove(KEY_MUSIC_ITEM);
            return;
        }

        mMMKV.encode(KEY_MUSIC_ITEM, musicItem);
    }

    @Override
    public void setPlayPosition(int playPosition) {
        super.setPlayPosition(playPosition);

        mMMKV.encode(KEY_PLAY_POSITION, playPosition);
    }

    @Override
    public void setPlayMode(@NonNull PlayMode playMode) {
        super.setPlayMode(playMode);

        mMMKV.encode(KEY_PLAY_MODE, playMode.serialId);
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);

        mMMKV.encode(KEY_SPEED, speed);
    }
}
