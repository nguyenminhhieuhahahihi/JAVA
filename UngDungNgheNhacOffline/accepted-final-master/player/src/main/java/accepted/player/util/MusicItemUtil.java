package accepted.player.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.util.List;

import accepted.player.R;
import accepted.player.audio.MusicItem;

public final class MusicItemUtil {
    private MusicItemUtil() {
        throw new AssertionError();
    }

    public static String getTitle(@NonNull MusicItem musicItem, @NonNull String defaultTitle) {
        Preconditions.checkNotNull(musicItem);
        Preconditions.checkNotNull(defaultTitle);

        return getStringOrDefault(musicItem.getTitle(), defaultTitle);
    }

    public static String getArtist(@NonNull MusicItem musicItem, @NonNull String defaultArtist) {
        Preconditions.checkNotNull(musicItem);
        Preconditions.checkNotNull(defaultArtist);

        return getStringOrDefault(musicItem.getArtist(), defaultArtist);
    }

    public static String getAlbum(@NonNull MusicItem musicItem, @NonNull String defaultAlbum) {
        Preconditions.checkNotNull(musicItem);
        Preconditions.checkNotNull(defaultAlbum);

        return getStringOrDefault(musicItem.getAlbum(), defaultAlbum);
    }

    public static String getTitle(@NonNull Context context, @NonNull MusicItem musicItem) {
        return getTitle(musicItem, context.getString(R.string.accepted_music_item_unknown_title));
    }

    public static String getArtist(@NonNull Context context, @NonNull MusicItem musicItem) {
        return getArtist(musicItem, context.getString(R.string.accepted_music_item_unknown_artist));
    }

    public static String getAlbum(@NonNull Context context, @NonNull MusicItem musicItem) {
        return getAlbum(musicItem, context.getString(R.string.accepted_music_item_unknown_album));
    }

    private static String getStringOrDefault(String value, String defaultValue) {
        return value.isEmpty() ? defaultValue : value;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static <T> String generateToken(List<T> items, GetUriFunction<T> function) {
        Hasher hasher = Hashing.sha256().newHasher();

        for (T item : items) {
            hasher.putString(function.getUri(item), Charsets.UTF_8);
        }

        return hasher.hash().toString();
    }

    public interface GetUriFunction<T> {
        @NonNull
        String getUri(T item);
    }
}
