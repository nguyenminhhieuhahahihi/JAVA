package accepted.player.ui.equalizer;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import accepted.player.PlayerService;
import accepted.player.effect.AudioEffectManager;
import accepted.player.ui.util.AndroidAudioEffectConfigUtil;

public final class AndroidAudioEffectManager implements AudioEffectManager {
    public static final int PRIORITY = 1;

    private Bundle mConfig;

    @Nullable
    private Equalizer mEqualizer;
    @Nullable
    private BassBoost mBassBoost;
    @Nullable
    private Virtualizer mVirtualizer;

    @Override
    public void init(@NonNull Bundle config) {
        mConfig = new Bundle(config);
    }

    @Override
    public void updateConfig(@NonNull Bundle config) {
        mConfig = new Bundle(config);

        if (mEqualizer != null && mEqualizer.hasControl()) {
            AndroidAudioEffectConfigUtil.applySettings(mConfig, mEqualizer);
        }

        if (mBassBoost != null && mBassBoost.hasControl()) {
            AndroidAudioEffectConfigUtil.applySettings(mConfig, mBassBoost);
        }

        if (mVirtualizer != null && mVirtualizer.hasControl()) {
            AndroidAudioEffectConfigUtil.applySettings(mConfig, mVirtualizer);
        }
    }

    @Override
    public void attachAudioEffect(int audioSessionId) {
        releaseAudioEffect();

        mEqualizer = new Equalizer(PRIORITY, audioSessionId);
        mBassBoost = new BassBoost(PRIORITY, audioSessionId);
        mVirtualizer = new Virtualizer(PRIORITY, audioSessionId);

        AndroidAudioEffectConfigUtil.applySettings(mConfig, mEqualizer);
        AndroidAudioEffectConfigUtil.applySettings(mConfig, mBassBoost);
        AndroidAudioEffectConfigUtil.applySettings(mConfig, mVirtualizer);

        mEqualizer.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
            @Override
            public void onControlStatusChange(AudioEffect effect, boolean controlGranted) {
                if (mEqualizer != null) {
                    AndroidAudioEffectConfigUtil.applySettings(mConfig, mEqualizer);
                }
            }
        });

        mBassBoost.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
            @Override
            public void onControlStatusChange(AudioEffect effect, boolean controlGranted) {
                if (mBassBoost != null) {
                    AndroidAudioEffectConfigUtil.applySettings(mConfig, mBassBoost);
                }
            }
        });

        mVirtualizer.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
            @Override
            public void onControlStatusChange(AudioEffect effect, boolean controlGranted) {
                if (mVirtualizer != null) {
                    AndroidAudioEffectConfigUtil.applySettings(mConfig, mVirtualizer);
                }
            }
        });

        mEqualizer.setEnabled(true);
        mBassBoost.setEnabled(true);
        mVirtualizer.setEnabled(true);
    }

    @Override
    public void detachAudioEffect() {
        releaseAudioEffect();
    }

    @Override
    public void release() {
        releaseAudioEffect();
    }

    private void releaseAudioEffect() {
        if (mEqualizer != null) {
            mEqualizer.release();
            mEqualizer = null;
        }

        if (mBassBoost != null) {
            mBassBoost.release();
            mBassBoost = null;
        }

        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }
    }
}
