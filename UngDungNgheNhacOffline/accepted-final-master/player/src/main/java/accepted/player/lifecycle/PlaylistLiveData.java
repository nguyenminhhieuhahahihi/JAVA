package accepted.player.lifecycle;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.google.common.base.Preconditions;

import accepted.player.Player;
import accepted.player.PlayerClient;
import accepted.player.playlist.Playlist;
import accepted.player.playlist.PlaylistManager;

public class PlaylistLiveData extends LiveData<Playlist>
        implements Player.OnPlaylistChangeListener {
    private static final String TAG = "PlaylistLiveData";
    private PlayerClient mPlayerClient;
    private boolean mLazy;

    public PlaylistLiveData(@NonNull PlayerClient playerClient, Playlist value) {
        this(playerClient, value, true);
    }

    public PlaylistLiveData(@NonNull PlayerClient playerClient, Playlist value, boolean lazy) {
        super(value);
        Preconditions.checkNotNull(playerClient);

        mPlayerClient = playerClient;
        mLazy = lazy;

        if (mLazy) {
            return;
        }

        observePlaylist();
    }

    public boolean isLazy() {
        return mLazy;
    }

    @Override
    protected void onActive() {
        observePlaylist();
    }

    @Override
    protected void onInactive() {
        mPlayerClient.removeOnPlaylistChangeListener(this);
    }

    @Override
    public void onPlaylistChanged(PlaylistManager playlistManager, int position) {
        playlistManager.getPlaylist(new PlaylistManager.Callback() {
            @Override
            public void onFinished(@NonNull Playlist playlist) {
                setValue(playlist);
            }
        });
    }

    private void observePlaylist() {
        mPlayerClient.addOnPlaylistChangeListener(this);
    }
}
