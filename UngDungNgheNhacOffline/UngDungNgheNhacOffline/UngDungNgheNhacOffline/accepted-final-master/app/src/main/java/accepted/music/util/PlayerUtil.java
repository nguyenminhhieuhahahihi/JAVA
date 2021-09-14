package accepted.music.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import accepted.player.PlayerClient;
import accepted.player.PlayerService;
import accepted.player.lifecycle.PlayerViewModel;

public final class PlayerUtil {
    private PlayerUtil() {
        throw new AssertionError();
    }

    public static void initPlayerViewModel(@NonNull Context context,
                                           @NonNull PlayerViewModel playerViewModel,
                                           @NonNull Class<? extends PlayerService> playerService) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerViewModel);
        Preconditions.checkNotNull(playerService);

        if (playerViewModel.isInitialized()) {
            return;
        }

        PlayerClient playerClient = PlayerClient.newInstance(context, playerService);
        playerClient.setAutoConnect(true);
        playerClient.connect();

        playerViewModel.init(context, playerClient);
        playerViewModel.setAutoDisconnect(true);
    }
}
