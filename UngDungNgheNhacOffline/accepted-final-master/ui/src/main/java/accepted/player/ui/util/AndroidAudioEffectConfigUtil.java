package accepted.player.ui.util;

import android.content.Context;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;

import androidx.annotation.NonNull;

import accepted.player.ui.R;

public final class AndroidAudioEffectConfigUtil {
    public static final String KEY_SETTING_EQUALIZER = "setting_equalizer";
    public static final String KEY_SETTING_BASS_BOOST = "setting_bass_boost";
    public static final String KEY_SETTING_VIRTUALIZER = "setting_virtualizer";

    private AndroidAudioEffectConfigUtil() {
        throw new AssertionError();
    }

    public static void applySettings(@NonNull Bundle config, @NonNull Equalizer equalizer) {
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(equalizer);

        String settings = config.getString(KEY_SETTING_EQUALIZER);
        if (settings == null || settings.isEmpty()) {
            return;
        }

        try {
            equalizer.setProperties(new Equalizer.Settings(settings));
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            e.printStackTrace();
        }
    }

    public static void applySettings(@NonNull Bundle config, @NonNull BassBoost bassBoost) {
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(bassBoost);

        String settings = config.getString(KEY_SETTING_BASS_BOOST);
        if (settings == null || settings.isEmpty()) {
            return;
        }

        try {
            bassBoost.setProperties(new BassBoost.Settings(settings));
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            e.printStackTrace();
        }
    }

    public static void applySettings(@NonNull Bundle config, @NonNull Virtualizer virtualizer) {
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(virtualizer);

        String settings = config.getString(KEY_SETTING_VIRTUALIZER);
        if (settings == null || settings.isEmpty()) {
            return;
        }

        try {
            virtualizer.setProperties(new Virtualizer.Settings(settings));
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            e.printStackTrace();
        }
    }

    public static void updateSettings(@NonNull Bundle config, @NonNull Equalizer.Settings settings) {
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(settings);

        config.putString(KEY_SETTING_EQUALIZER, settings.toString());
    }

    public static void updateSettings(@NonNull Bundle config, @NonNull BassBoost.Settings settings) {
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(settings);

        config.putString(KEY_SETTING_BASS_BOOST, settings.toString());
    }

    public static void updateSettings(@NonNull Bundle config, @NonNull Virtualizer.Settings settings) {
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(settings);

        config.putString(KEY_SETTING_VIRTUALIZER, settings.toString());
    }

    public static String optimizeEqualizerPresetName(@NonNull Context context, @NonNull String presetName) {
        switch (presetName) {
            case "Normal":
                return context.getString(R.string.accepted_ui_equalizer_preset_normal);
            case "Classical":
                return context.getString(R.string.accepted_ui_equalizer_preset_classical);
            case "Dance":
                return context.getString(R.string.accepted_ui_equalizer_preset_dance);
            case "Flat":
                return context.getString(R.string.accepted_ui_equalizer_preset_flat);
            case "Folk":
                return context.getString(R.string.accepted_ui_equalizer_preset_folk);
            case "Heavy Metal":
                return context.getString(R.string.accepted_ui_equalizer_preset_heavy_metal);
            case "Hip Hop":
                return context.getString(R.string.accepted_ui_equalizer_preset_hip_hop);
            case "Jazz":
                return context.getString(R.string.accepted_ui_equalizer_preset_jazz);
            case "Pop":
                return context.getString(R.string.accepted_ui_equalizer_preset_pop);
            case "Rock":
                return context.getString(R.string.accepted_ui_equalizer_preset_rock);
        }

        return presetName;
    }
}
