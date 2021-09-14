package accepted.player.audio;

import accepted.player.helper.VolumeEaseHelper;

public abstract class AbstractMusicPlayer implements MusicPlayer {
    private final VolumeEaseHelper mVolumeEaseHelper;

    public AbstractMusicPlayer() {
        mVolumeEaseHelper = new VolumeEaseHelper(this, new VolumeEaseHelper.Callback() {
            @Override
            public void start() {
                startEx();
            }

            @Override
            public void pause() {
                pauseEx();
            }
        });
    }

    @Override
    public final void start() {
        mVolumeEaseHelper.start();
    }

    public abstract void startEx();

    @Override
    public final void pause() {
        mVolumeEaseHelper.pause();
    }

    public abstract void pauseEx();

    @Override
    public final void stop() {
        mVolumeEaseHelper.cancel();
        stopEx();
    }

    public abstract void stopEx();

    @Override
    public final void release() {
        mVolumeEaseHelper.cancel();
        releaseEx();
    }

    public abstract void releaseEx();

    @Override
    public void quiet() {
        mVolumeEaseHelper.quiet();
    }

    @Override
    public void dismissQuiet() {
        mVolumeEaseHelper.dismissQuiet();
    }
}
