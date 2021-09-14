package accepted.player;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import accepted.player.audio.MusicItemTest;
import accepted.player.playlist.PlaylistTest;

@Suite.SuiteClasses({
        // accepted.player
        PlayerStateTest.class,
        PersistentPlayerStateTest.class,
        PlayerConfigTest.class,
        // accepted.player.media
        MusicItemTest.class,
        // accepted.player.playlist
        PlaylistTest.class
})
@RunWith(Suite.class)
public class RunAllTest {
}
