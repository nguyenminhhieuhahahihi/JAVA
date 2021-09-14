package accepted.player;

import androidx.annotation.NonNull;

import channel.helper.Channel;

@Channel
public interface PlayerStateSynchronizer {
    void syncPlayerState(String clientToken);

    @Channel
    interface OnSyncPlayerStateListener {
        void onSyncPlayerState(@NonNull String clientToken, @NonNull PlayerState playerState);
    }
}
