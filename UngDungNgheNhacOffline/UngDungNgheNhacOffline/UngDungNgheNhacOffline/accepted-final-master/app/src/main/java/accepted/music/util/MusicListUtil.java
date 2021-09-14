package accepted.music.util;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.List;

import accepted.music.store.Music;
import accepted.music.store.MusicList;
import accepted.player.playlist.Playlist;

public final class MusicListUtil {
    private MusicListUtil() {
        throw new AssertionError();
    }

    @NonNull
    public static Playlist asPlaylist(@NonNull String name, @NonNull List<Music> musicItems, int position) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(musicItems);

        Playlist.Builder builder = new Playlist.Builder()
                .setName(name)
                .setPosition(position);

        for (Music music : musicItems) {
            builder.append(MusicUtil.asMusicItem(music));
        }

        return builder.build();
    }
}
