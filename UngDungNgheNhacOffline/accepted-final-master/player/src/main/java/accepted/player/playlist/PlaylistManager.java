package accepted.player.playlist;

import androidx.annotation.NonNull;

public interface PlaylistManager {
    @NonNull
    String getPlaylistName();

    int getPlaylistSize();

    @NonNull
    String getPlaylistToken();

    boolean isPlaylistEditable();

    void getPlaylist(@NonNull Callback callback);

    long getLastModified();

    interface Callback {
        void onFinished(@NonNull Playlist playlist);
    }
}
