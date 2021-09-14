package accepted.player;

import androidx.annotation.NonNull;

import accepted.player.audio.MusicItem;

public interface HistoryRecorder {
    void recordHistory(@NonNull MusicItem musicItem);
}
