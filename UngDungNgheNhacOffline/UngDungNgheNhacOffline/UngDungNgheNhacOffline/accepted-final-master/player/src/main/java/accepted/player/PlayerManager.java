package accepted.player;

import android.os.Bundle;

import androidx.annotation.NonNull;

import channel.helper.Channel;
import channel.helper.UseOrdinal;

@Channel
public interface PlayerManager {

    void setSoundQuality(@UseOrdinal SoundQuality soundQuality);

    void setAudioEffectConfig(Bundle config);

    void setAudioEffectEnabled(boolean enabled);

    void setOnlyWifiNetwork(boolean onlyWifiNetwork);

    void setIgnoreAudioFocus(boolean ignoreAudioFocus);

    void shutdown();
}
