package accepted.music.fragment.ringtone;

import androidx.lifecycle.ViewModel;

import accepted.music.store.Music;

public class RingtoneViewModel extends ViewModel {
    private Music mRingtoneMusic;

    public Music getRingtoneMusic() {
        return mRingtoneMusic;
    }

    public void setRingtoneMusic(Music ringtoneMusic) {
        mRingtoneMusic = ringtoneMusic;
    }
}
