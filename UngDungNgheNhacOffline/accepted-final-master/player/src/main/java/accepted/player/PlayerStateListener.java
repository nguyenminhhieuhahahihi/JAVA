package accepted.player;

import channel.helper.Channel;

@Channel
public interface PlayerStateListener extends Player.OnPlaybackStateChangeListener,
        Player.OnPrepareListener,
        Player.OnStalledChangeListener,
        Player.OnBufferedProgressChangeListener,
        Player.OnPlayingMusicItemChangeListener,
        Player.OnSeekCompleteListener,
        Player.OnPlaylistChangeListener,
        Player.OnPlayModeChangeListener,
        Player.OnRepeatListener,
        Player.OnSpeedChangeListener {
    void onShutdown();
}
