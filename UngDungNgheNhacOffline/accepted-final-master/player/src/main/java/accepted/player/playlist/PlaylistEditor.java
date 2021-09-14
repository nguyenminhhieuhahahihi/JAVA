package accepted.player.playlist;

import androidx.annotation.NonNull;

import channel.helper.Channel;
import accepted.player.audio.MusicItem;

@Channel
public interface PlaylistEditor {
    @SuppressWarnings("NullableProblems")
    void insertMusicItem(int position, @NonNull MusicItem musicItem);

    @SuppressWarnings("NullableProblems")
    void appendMusicItem(@NonNull MusicItem musicItem);

    void moveMusicItem(int fromPosition, int toPosition);

    @SuppressWarnings("NullableProblems")
    void removeMusicItem(@NonNull MusicItem musicItem);

    void removeMusicItem(int position);

    @SuppressWarnings("NullableProblems")
    void setNextPlay(@NonNull MusicItem musicItem);

    void setPlaylist(Playlist playlist, int position, boolean play);
}
