package accepted.player.audio;

import android.content.Context;
import android.content.res.Resources;

import accepted.player.R;

public final class ErrorCode {
    public static final int NO_ERROR = 0;
    public static final int ONLY_WIFI_NETWORK = 1;
    public static final int PLAYER_ERROR = 2;
    public static final int NETWORK_ERROR = 3;
    public static final int FILE_NOT_FOUND = 4;
    public static final int DATA_LOAD_FAILED = 5;
    public static final int GET_URL_FAILED = 6;
    public static final int OUT_OF_MEMORY = 7;
    public static final int UNKNOWN_ERROR = 8;

    private ErrorCode() {
        throw new AssertionError();
    }

    public static String getErrorMessage(Context context, int errorCode) {
        Resources res = context.getResources();

        switch (errorCode) {
            case NO_ERROR:
                return res.getString(R.string.accepted_error_no_error);
            case ONLY_WIFI_NETWORK:
                return res.getString(R.string.accepted_error_only_wifi_network);
            case PLAYER_ERROR:
                return res.getString(R.string.accepted_error_player_error);
            case NETWORK_ERROR:
                return res.getString(R.string.accepted_error_network_error);
            case FILE_NOT_FOUND:
                return res.getString(R.string.accepted_error_file_not_found);
            case DATA_LOAD_FAILED:
                return res.getString(R.string.accepted_error_data_load_failed);
            case GET_URL_FAILED:
                return res.getString(R.string.accepted_error_get_url_failed);
            case OUT_OF_MEMORY:
                return res.getString(R.string.accepted_error_out_of_memory);
            case UNKNOWN_ERROR:
                return res.getString(R.string.accepted_error_unknown_error);
            default:
                return res.getString(R.string.accepted_error_unknown_error);
        }
    }
}
