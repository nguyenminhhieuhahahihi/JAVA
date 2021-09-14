package accepted.music.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import accepted.music.store.MusicStore;
import accepted.player.audio.MusicItem;

public class FavoriteObserver {
    private boolean mFavorite;
    @Nullable
    private MusicItem mMusicItem;
    private final OnFavoriteStateChangeListener mListener;

    private boolean mSubscribed;
    private final MusicStore.OnFavoriteChangeListener mFavoriteChangeListener;
    private Disposable mCheckFavoriteDisposable;

    public FavoriteObserver(@NonNull OnFavoriteStateChangeListener listener) {
        Preconditions.checkNotNull(listener);
        mListener = listener;

        mFavoriteChangeListener = this::checkMusicFavoriteState;
    }

    public synchronized void setMusicItem(@Nullable MusicItem musicItem) {
        mMusicItem = musicItem;
        checkMusicFavoriteState();
    }

    @Nullable
    public synchronized MusicItem getMusicItem() {
        return mMusicItem;
    }

    public synchronized boolean isFavorite() {
        return mFavorite;
    }

    public void subscribe() {
        if (mSubscribed) {
            return;
        }

        mSubscribed = true;
        MusicStore.getInstance().addOnFavoriteChangeListener(mFavoriteChangeListener);
    }

    public void unsubscribe() {
        mSubscribed = false;
        disposeCheckFavorite();
        MusicStore.getInstance().removeOnFavoriteChangeListener(mFavoriteChangeListener);
    }

    private synchronized void setFavorite(boolean favorite) {
        mFavorite = favorite;
        mListener.onFavoriteStateChanged(favorite);
    }

    private void checkMusicFavoriteState() {
        if (mMusicItem == null) {
            setFavorite(false);
            return;
        }

        disposeCheckFavorite();

        mCheckFavoriteDisposable = Single.create((SingleOnSubscribe<Boolean>) emitter -> {
            MusicItem musicItem = getMusicItem();

            boolean result;
            if (musicItem == null) {
                result = false;
            } else {
                result = MusicStore.getInstance().isFavorite(MusicUtil.getId(mMusicItem));
            }

            if (emitter.isDisposed()) {
                return;
            }

            emitter.onSuccess(result);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setFavorite);
    }

    private void disposeCheckFavorite() {
        if (mCheckFavoriteDisposable != null) {
            mCheckFavoriteDisposable.dispose();
        }
    }

    public interface OnFavoriteStateChangeListener {
        void onFavoriteStateChanged(boolean favorite);
    }
}
