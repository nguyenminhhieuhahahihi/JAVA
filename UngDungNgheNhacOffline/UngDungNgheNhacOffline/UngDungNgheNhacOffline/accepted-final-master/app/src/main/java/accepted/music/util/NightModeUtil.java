package accepted.music.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.common.base.Preconditions;
import com.tencent.mmkv.MMKV;

public class NightModeUtil {
    private static final String KEY_MODE = "mode";

    public static void applyNightMode(@NonNull Context context) {
        Preconditions.checkNotNull(context);

        MMKV mmkv = MMKV.mmkvWithID(getMMapId(context));
        Mode mode = Mode.getModeById(mmkv.decodeInt(KEY_MODE, 0));
        AppCompatDelegate.setDefaultNightMode(mode.getModeValue());
    }

    public static void setDefaultNightMode(@NonNull Context context, @NonNull Mode mode) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(mode);

        MMKV mmkv = MMKV.mmkvWithID(getMMapId(context));
        mmkv.encode(KEY_MODE, mode.id);
        AppCompatDelegate.setDefaultNightMode(mode.getModeValue());
    }

    @NonNull
    public static Mode getNightMode(@NonNull Context context) {
        Preconditions.checkNotNull(context);

        MMKV mmkv = MMKV.mmkvWithID(getMMapId(context));
        return Mode.getModeById(mmkv.decodeInt(KEY_MODE, 0));
    }

    private static String getMMapId(Context context) {
        return context.getPackageName() + ".NIGHT_MODE";
    }

    public enum Mode {
        NIGHT_FOLLOW_SYSTEM(0),
        NIGHT_NO(1),
        NIGHT_YES(2),
        NIGHT_AUTO_BATTERY(3);

        private final int id;

        Mode(int id) {
            this.id = id;
        }

        private static Mode getModeById(int id) {
            switch (id) {
                case 1:
                    return NIGHT_NO;
                case 2:
                    return NIGHT_YES;
                case 3:
                    return NIGHT_AUTO_BATTERY;
                default:
                    return NIGHT_FOLLOW_SYSTEM;
            }
        }

        private int getModeValue() {
            switch (this) {
                case NIGHT_NO:
                    return AppCompatDelegate.MODE_NIGHT_NO;
                case NIGHT_YES:
                    return AppCompatDelegate.MODE_NIGHT_YES;
                case NIGHT_AUTO_BATTERY:
                    return AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
                default:
                    return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }
        }
    }

}
