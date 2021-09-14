package accepted.music;

import androidx.multidex.MultiDexApplication;

import com.tencent.mmkv.MMKV;

import accepted.music.store.MusicStore;
import accepted.music.util.NightModeUtil;

public class Application extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        MMKV.initialize(this);
        NightModeUtil.applyNightMode(this);
        MusicStore.init(this);
    }
}
