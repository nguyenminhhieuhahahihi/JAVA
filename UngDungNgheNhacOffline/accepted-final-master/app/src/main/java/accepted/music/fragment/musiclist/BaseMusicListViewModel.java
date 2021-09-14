package accepted.music.fragment.musiclist;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import accepted.music.activity.multichoice.MultiChoiceStateHolder;
import accepted.music.store.Music;
import accepted.music.store.MusicList;
import accepted.player.util.MusicItemUtil;

public abstract class BaseMusicListViewModel extends ViewModel {
    private final MutableLiveData<List<Music>> mMusicListItems;
    private final MutableLiveData<Boolean> mLoadingMusicList;
    private String mMusicListName = "";
    private String mMusicListToken = "";

    private boolean mInitialized;
    private Disposable mLoadMusicListDisposable;

    private boolean mIgnoreDiffUtil;

    public BaseMusicListViewModel() {
        mMusicListItems = new MutableLiveData<>(Collections.emptyList());
        mLoadingMusicList = new MutableLiveData<>(false);
    }

    public void init(@NonNull String musicListName) {
        Preconditions.checkNotNull(musicListName);

        if (mInitialized) {
            return;
        }

        mInitialized = true;
        mMusicListName = musicListName;
        loadMusicList();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (!mInitialized) {
            return;
        }

        cancelLastLoading();

        MultiChoiceStateHolder.getInstance()
                .release();
    }

    @NonNull
    public LiveData<List<Music>> getMusicListItems() {
        if (!mInitialized) {
            throw new IllegalStateException("MusicListViewModel not init yet.");
        }

        return mMusicListItems;
    }

    public LiveData<Boolean> getLoadingMusicList() {
        return mLoadingMusicList;
    }

    public void setMusicListItems(@NonNull List<Music> musicListItems) {
        Preconditions.checkNotNull(musicListItems);
        mMusicListItems.setValue(new ArrayList<>(musicListItems));
    }

    @NonNull
    public String getMusicListName() {
        return mMusicListName;
    }

    @NonNull
    public String getMusicListToken() {
        return mMusicListToken;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public void setIgnoreDiffUtil(boolean ignoreDiffUtil) {
        mIgnoreDiffUtil = ignoreDiffUtil;
    }

    public boolean consumeIgnoreDiffUtil() {
        boolean result = mIgnoreDiffUtil;
        mIgnoreDiffUtil = false;

        return result;
    }


    private void loadMusicList() {
        cancelLastLoading();

        mLoadingMusicList.setValue(true);
        mLoadMusicListDisposable = Single.create((SingleOnSubscribe<List<Music>>) emitter -> {
            List<Music> musicList = loadMusicListItems();
            if (emitter.isDisposed()) {
                return;
            }
            emitter.onSuccess(musicList);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(musicList -> {
                    mLoadingMusicList.setValue(false);
                    notifyMusicItemsChanged(musicList);
                });
    }

    private void cancelLastLoading() {
        if (mLoadMusicListDisposable != null && mLoadMusicListDisposable.isDisposed()) {
            mLoadMusicListDisposable.dispose();
        }
    }

    protected final void reloadMusicList() {
        loadMusicList();
    }

    protected void notifyMusicItemsChanged(@NonNull List<Music> musicListItems) {
        Preconditions.checkNotNull(musicListItems);
        mMusicListToken = MusicItemUtil.generateToken(musicListItems, item -> {
            String uri = item.getUri();
            return uri == null ? "" : uri;
        });
        mMusicListItems.setValue(new ArrayList<>(musicListItems));
    }

    protected int indexOf(Music music) {
        List<Music> musicList = mMusicListItems.getValue();
        return Objects.requireNonNull(musicList).indexOf(music);
    }

    public void sortMusicList(@NonNull MusicList.SortOrder sortOrder) {
        Preconditions.checkNotNull(sortOrder);
        mIgnoreDiffUtil = true;
        onSortMusicList(sortOrder);
    }

    @NonNull
    protected abstract List<Music> loadMusicListItems();

    protected abstract void removeMusic(@NonNull Music music);

    protected abstract void onSortMusicList(@NonNull MusicList.SortOrder sortOrder);

    @NonNull
    protected abstract MusicList.SortOrder getSortOrder();
}
