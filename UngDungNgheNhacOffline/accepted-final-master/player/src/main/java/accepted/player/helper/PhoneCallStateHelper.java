package accepted.player.helper;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

public final class PhoneCallStateHelper {
    private final TelephonyManager mTelephonyManager;
    private final PhoneStateListener mPhoneStateListener;
    private final OnStateChangeListener mCallStateListener;
    private boolean mRegistered;

    public PhoneCallStateHelper(@NonNull Context context, @NonNull OnStateChangeListener listener) {
        Preconditions.checkNotNull(listener);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mCallStateListener = listener;

        mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_IDLE:
                        mCallStateListener.onIDLE();
                        break;
                    case TelephonyManager.CALL_STATE_RINGING:
                        mCallStateListener.onRinging();
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        mCallStateListener.onOffHook();
                        break;
                }
            }
        };
    }

    public boolean isCallIDLE() {
        return mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE;
    }

    public void registerCallStateListener() {
        if (mRegistered) {
            return;
        }

        mRegistered = true;
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    public void unregisterCallStateListener() {
        if (mRegistered) {
            mRegistered = false;
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    public interface OnStateChangeListener {
        void onIDLE();

        void onRinging();

        void onOffHook();
    }
}
