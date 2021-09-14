package accepted.music.util;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import accepted.music.store.Music;
import accepted.player.audio.MusicItem;

@RunWith(AndroidJUnit4.class)
public class MusicUtilTest {

    @Test
    public void asMusic_asMusicItem() {
        Music music1 = new Music(1024,
                "TestTitle",
                "TestArtist",
                "TestAlbum",
                "https://www.test.com/test.mp3",
                "https://www.test.com/test.png",
                60_000,
                System.currentTimeMillis());

        MusicItem musicItem1 = MusicUtil.asMusicItem(music1);

        Music music2 = MusicUtil.asMusic(musicItem1);
        MusicItem musicItem2 = MusicUtil.asMusicItem(music2);

        // assert
        assertEquals(music1, music2);
        assertEquals(musicItem1, musicItem2);
    }
}
