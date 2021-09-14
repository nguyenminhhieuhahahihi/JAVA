package accepted.player.effect;

import android.os.Bundle;

import androidx.annotation.NonNull;

public interface AudioEffectManager {

    void init(@NonNull Bundle config);

    void updateConfig(@NonNull Bundle config);

    void attachAudioEffect(int audioSessionId);

    void detachAudioEffect();

    void release();
}
