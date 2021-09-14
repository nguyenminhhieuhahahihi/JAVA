package accepted.music.util;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import accepted.music.fragment.ringtone.RingtoneUtilFragment;
import accepted.music.store.Music;
import accepted.player.audio.MusicItem;
import accepted.player.playlist.Playlist;

public final class MusicUtil {
    private static final String KEY_ADD_TIME = "add_time";

    private MusicUtil() {
        throw new AssertionError();
    }

    public static Music asMusic(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);

        return new Music(
                Long.parseLong(musicItem.getMusicId()),
                musicItem.getTitle(),
                musicItem.getArtist(),
                musicItem.getAlbum(),
                musicItem.getUri(),
                musicItem.getIconUri(),
                musicItem.getDuration(),
                getAddTime(musicItem)
        );
    }

    public static MusicItem asMusicItem(@NonNull Music music) {
        Preconditions.checkNotNull(music);

        MusicItem musicItem = new MusicItem.Builder()
                .setMusicId(String.valueOf(music.getId()))
                .setTitle(music.getTitle())
                .setArtist(music.getArtist())
                .setAlbum(music.getAlbum())
                .setUri(music.getUri())
                .setIconUri(music.getIconUri())
                .setDuration(music.getDuration())
                .build();

        putAddTime(musicItem, music);

        return musicItem;
    }

    public static long getId(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        return Long.parseLong(musicItem.getMusicId());
    }

    public static Single<byte[]> getEmbeddedPicture(@NonNull Context context, @NonNull Music music) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(music);
        return getEmbeddedPicture(context, music.getUri());
    }

    public static Single<byte[]> getEmbeddedPicture(@NonNull Context context, @NonNull String uri) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(uri);

        return Single.create(emitter -> {
            if (uri.isEmpty()) {
                emitter.onSuccess(new byte[0]);
                return;
            }

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(context, Uri.parse(uri));
                byte[] picture = retriever.getEmbeddedPicture();
                retriever.release();

                if (emitter.isDisposed()) {
                    return;
                }

                if (picture == null) {
                    emitter.onSuccess(new byte[0]);
                } else {
                    emitter.onSuccess(picture);
                }

            } catch (Exception e) {
                emitter.onSuccess(new byte[0]);
            } finally {
                retriever.release();
            }
        });
    }

    @NonNull
    public static Playlist asPlaylist(int position, @NonNull List<Music> musicList, @NonNull String name) {
        Preconditions.checkNotNull(musicList);
        Preconditions.checkNotNull(name);
        if (position < 0 || position >= musicList.size()) {
            throw new IndexOutOfBoundsException("position out of bound, position: " + position + ", size: " + musicList.size());
        }

        int start = 0;
        int end = musicList.size();
        List<MusicItem> musicItemList = new ArrayList<>(Math.min(musicList.size(), Playlist.MAX_SIZE));

        if (end > Playlist.MAX_SIZE) {
            int value = end - position;
            if (value >= Playlist.MAX_SIZE) {
                end = position + Playlist.MAX_SIZE;
                start = position;
            } else {
                start = position - (Playlist.MAX_SIZE - value);
            }
        }

        for (int i = start; i < end; i++) {
            musicItemList.add(MusicUtil.asMusicItem(musicList.get(i)));
        }

        return new Playlist.Builder()
                .setName(name)
                .appendAll(musicItemList)
                .build();
    }

    public static void setAsRingtone(@NonNull FragmentManager fm, @NonNull Music music) {
        Preconditions.checkNotNull(fm);
        Preconditions.checkNotNull(music);

        RingtoneUtilFragment.setAsRingtone(fm, music);
    }

    private static long getAddTime(MusicItem musicItem) {
        Bundle extra = musicItem.getExtra();
        if (extra == null) {
            return System.currentTimeMillis();
        }

        return extra.getLong(KEY_ADD_TIME, System.currentTimeMillis());
    }

    private static void putAddTime(MusicItem musicItem, Music music) {
        Bundle extra = new Bundle();
        extra.putLong(KEY_ADD_TIME, music.getAddTime());
        musicItem.setExtra(extra);
    }
}
