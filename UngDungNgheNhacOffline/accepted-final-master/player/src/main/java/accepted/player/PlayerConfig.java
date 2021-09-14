package accepted.player;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.tencent.mmkv.MMKV;

class PlayerConfig {
    private static final String KEY_SOUND_QUALITY = "sound_quality";
    private static final String KEY_AUDIO_EFFECT_CONFIG = "audio_effect_config";
    private static final String KEY_AUDIO_EFFECT_ENABLED = "audio_effect_enabled";
    private static final String KEY_ONLY_WIFI_NETWORK = "only_wifi_network";
    private static final String KEY_IGNORE_AUDIO_FOCUS = "ignore_audio_focus";

    private final MMKV mMMKV;

    public PlayerConfig(@NonNull Context context, @NonNull String id) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(id);

        MMKV.initialize(context);
        mMMKV = MMKV.mmkvWithID("PlayerConfig:" + id, MMKV.MULTI_PROCESS_MODE);
    }

    @NonNull
    public SoundQuality getSoundQuality() {
        return SoundQuality.values()[mMMKV.decodeInt(KEY_SOUND_QUALITY, 0)];
    }

    public void setSoundQuality(@NonNull SoundQuality soundQuality) {
        Preconditions.checkNotNull(soundQuality);
        mMMKV.encode(KEY_SOUND_QUALITY, soundQuality.ordinal());
    }

    @NonNull
    public Bundle getAudioEffectConfig() {
        return mMMKV.decodeParcelable(KEY_AUDIO_EFFECT_CONFIG, Bundle.class, new Bundle());
    }

    public void setAudioEffectConfig(@NonNull Bundle audioEffectConfig) {
        Preconditions.checkNotNull(audioEffectConfig);
        mMMKV.encode(KEY_AUDIO_EFFECT_CONFIG, audioEffectConfig);
    }

    public boolean isAudioEffectEnabled() {
        return mMMKV.decodeBool(KEY_AUDIO_EFFECT_ENABLED, false);
    }

    public void setAudioEffectEnabled(boolean audioEffectEnabled) {
        mMMKV.encode(KEY_AUDIO_EFFECT_ENABLED, audioEffectEnabled);
    }

    public boolean isOnlyWifiNetwork() {
        return mMMKV.decodeBool(KEY_ONLY_WIFI_NETWORK, false);
    }

    public void setOnlyWifiNetwork(boolean onlyWifiNetwork) {
        mMMKV.encode(KEY_ONLY_WIFI_NETWORK, onlyWifiNetwork);
    }

    public boolean isIgnoreAudioFocus() {
        return mMMKV.decodeBool(KEY_IGNORE_AUDIO_FOCUS, false);
    }

    public void setIgnoreAudioFocus(boolean ignoreAudioFocus) {
        mMMKV.encode(KEY_IGNORE_AUDIO_FOCUS, ignoreAudioFocus);
    }
}
