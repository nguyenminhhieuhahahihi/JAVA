package accepted.player;

import channel.helper.Channel;
import channel.helper.UseOrdinal;

@Channel
public interface SleepTimer {
    void startSleepTimer(long time, @UseOrdinal TimeoutAction action);

    void cancelSleepTimer();

    @Channel
    interface OnStateChangeListener {
        void onTimerStart(long time, long startTime, @UseOrdinal TimeoutAction action);

        void onTimerEnd();
    }

    enum TimeoutAction {
        PAUSE,
        STOP,
        SHUTDOWN
    }
}
