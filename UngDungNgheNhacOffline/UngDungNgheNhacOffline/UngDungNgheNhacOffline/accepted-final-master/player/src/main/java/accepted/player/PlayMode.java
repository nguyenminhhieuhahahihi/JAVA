package accepted.player;

public enum PlayMode {
    PLAYLIST_LOOP(0),
    LOOP(1),
    SHUFFLE(2),
    SINGLE_ONCE(3);

    final int serialId;

    PlayMode(int serialId) {
        this.serialId = serialId;
    }

    public static PlayMode getBySerialId(int serialId) {
        PlayMode playMode = PLAYLIST_LOOP;

        if (serialId == LOOP.serialId) {
            playMode = LOOP;
        } else if (serialId == SHUFFLE.serialId) {
            playMode = SHUFFLE;
        } else if (serialId == SINGLE_ONCE.serialId) {
            playMode = SINGLE_ONCE;
        }

        return playMode;
    }
}
