package accepted.music.store;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import android.os.Handler;
import android.util.Log;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class MusicStore {
    private static final String TAG = "MusicStore";
    public static final String MUSIC_LIST_LOCAL_MUSIC = "__local_music";
    public static final String MUSIC_LIST_FAVORITE = "__favorite";

    public static final int NAME_MAX_LENGTH = 40;

    private static MusicStore mInstance;

    private final BoxStore mBoxStore;
    private final Box<Music> mMusicBox;
    private final Box<MusicListEntity> mMusicListEntityBox;
    private final Box<HistoryEntity> mHistoryEntityBox;

    private final Handler mMainHandler;

    private final List<OnFavoriteChangeListener> mAllFavoriteChangeListener;
    private final List<OnCustomMusicListUpdateListener> mAllCustomMusicListUpdateListener;
    private OnScanCompleteListener mOnScanCompleteListener;

    private final Set<String> mAllCustomMusicListName;

    private MusicStore(BoxStore boxStore) {
        mBoxStore = boxStore;
        mMusicBox = boxStore.boxFor(Music.class);
        mMusicListEntityBox = boxStore.boxFor(MusicListEntity.class);
        mHistoryEntityBox = boxStore.boxFor(HistoryEntity.class);
        mMainHandler = new Handler(Looper.getMainLooper());
        mAllFavoriteChangeListener = new ArrayList<>();
        mAllCustomMusicListUpdateListener = new ArrayList<>();
        mAllCustomMusicListName = new HashSet<>();

        loadAllMusicListName();
    }

    private void loadAllMusicListName() {
        Single.create(emitter -> {
            String[] allName = mMusicListEntityBox.query()
                    .notEqual(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC)
                    .notEqual(MusicListEntity_.name, MUSIC_LIST_FAVORITE)
                    .build()
                    .property(MusicListEntity_.name)
                    .findStrings();

            if (allName == null) {
                return;
            }

            mAllCustomMusicListName.addAll(Arrays.asList(allName));

        }).subscribeOn(Schedulers.io())
                .subscribe();
    }

    public synchronized static void init(@NonNull Context context) {
        Preconditions.checkNotNull(context);

        if (mInstance != null) {
            return;
        }

        BoxStore boxStore = MyObjectBox.builder()
                .directory(new File(context.getFilesDir(), "music_store"))
                .build();

        init(boxStore);
    }

    public synchronized static void init(@NonNull BoxStore boxStore) {
        Preconditions.checkNotNull(boxStore);

        mInstance = new MusicStore(boxStore);
    }

    public static MusicStore getInstance() throws IllegalStateException {
        if (mInstance == null) {
            throw new IllegalStateException("music store not init yet.");
        }

        return mInstance;
    }

    public synchronized void sort(@NonNull MusicList musicList, @NonNull MusicList.SortOrder sortOrder, @Nullable SortCallback callback) {
        Preconditions.checkNotNull(musicList);
        Preconditions.checkNotNull(sortOrder);

        BoxStore boxStore = getInstance().getBoxStore();
        boxStore.runInTxAsync(() -> {
            ArrayList<Music> items = new ArrayList<>(musicList.getMusicElements());
            Collections.sort(items, sortOrder.comparator());

            musicList.musicListEntity.sortOrder = sortOrder;
            musicList.getMusicElements().clear();
            musicList.getMusicElements().addAll(items);
            updateMusicList(musicList);
        }, (result, error) -> mMainHandler.post(() -> {
            if (callback != null) {
                callback.onSortFinished();
            }
        }));
    }

    public synchronized BoxStore getBoxStore() throws IllegalStateException {
        if (mBoxStore == null) {
            throw new IllegalStateException("MusicStore not init yet.");
        }

        return mBoxStore;
    }

    private void checkThread() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            Log.e(TAG, "Please do not access the database on the main thread.");
        }
    }

    public boolean isNameExists(@NonNull String name) {
        Preconditions.checkNotNull(name);

        name = trimName(name);
        return isBuiltInName(name) || mAllCustomMusicListName.contains(name);
    }

    private String trimName(String name) {
        return name.trim().substring(0, Math.min(name.length(), NAME_MAX_LENGTH));
    }

    @NonNull
    public Set<String> getAllCustomMusicListName() {
        return mAllCustomMusicListName;
    }

    @NonNull
    public synchronized List<String> getAllCustomMusicListName(@NonNull Music music) {
        Preconditions.checkNotNull(music);

        QueryBuilder<MusicListEntity> builder = mMusicListEntityBox.query()
                .notEqual(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC)
                .notEqual(MusicListEntity_.name, MUSIC_LIST_FAVORITE);

        builder.link(MusicListEntity_.musicElements)
                .equal(Music_.id, music.getId());

        String[] names = builder.build()
                .property(MusicListEntity_.name)
                .findStrings();

        if (names == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(Arrays.asList(names));
    }

    public synchronized boolean isMusicListExists(@NonNull String name) {
        Preconditions.checkNotNull(name);
        checkThread();

        long count = mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name)
                .build()
                .count();

        return count > 0;
    }

    private boolean isMusicListExists(long id) {
        long count = mMusicListEntityBox.query()
                .equal(MusicListEntity_.id, id)
                .build()
                .count();

        return count > 0;
    }

    @NonNull
    public synchronized MusicList createCustomMusicList(@NonNull String name) throws IllegalArgumentException {
        Preconditions.checkNotNull(name);
        Preconditions.checkArgument(!name.isEmpty(), "name must not empty");
        checkThread();

        if (isBuiltInName(name)) {
            throw new IllegalArgumentException("Illegal music list name, conflicts with built-in name.");
        }

        name = trimName(name);

        if (isMusicListExists(name)) {
            MusicList musicList = getCustomMusicList(name);
            assert musicList != null;
            return musicList;
        }

        mAllCustomMusicListName.add(name);
        MusicListEntity entity = new MusicListEntity(0, name, 0, MusicList.SortOrder.BY_ADD_TIME, new byte[0]);
        mMusicListEntityBox.put(entity);
        return new MusicList(entity);
    }

    @Nullable
    public synchronized MusicList getCustomMusicList(@NonNull String name) {
        Preconditions.checkNotNull(name);
        checkThread();

        if (isBuiltInName(name)) {
            return null;
        }

        MusicListEntity entity = mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name)
                .build()
                .findUnique();

        if (entity == null) {
            return null;
        }

        return new MusicList(entity);
    }

    public synchronized void updateMusicList(@NonNull MusicList musicList) {
        Preconditions.checkNotNull(musicList);
        checkThread();

        if (!isMusicListExists(musicList.getId())) {
            return;
        }

        String name = musicList.getName();
        if (!isBuiltInName(name)) {
            mAllCustomMusicListName.add(name);
            notifyCustomMusicListUpdated(name);
        }

        musicList.applyChanges();
        mMusicListEntityBox.put(musicList.musicListEntity);
    }

    public synchronized void deleteMusicList(@NonNull MusicList musicList) {
        Preconditions.checkNotNull(musicList);
        checkThread();

        if (isBuiltInMusicList(musicList)) {
            return;
        }

        mAllCustomMusicListName.remove(musicList.getName());
        mMusicListEntityBox.query()
                .equal(MusicListEntity_.id, musicList.getId())
                .build()
                .remove();
    }

    public synchronized void deleteMusicList(@NonNull String name) {
        Preconditions.checkNotNull(name);
        checkThread();

        if (isBuiltInName(name)) {
            return;
        }

        mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name)
                .build()
                .remove();
    }

    public synchronized void renameMusicList(@NonNull MusicList musicList, @NonNull String newName) {
        Preconditions.checkNotNull(musicList);
        Preconditions.checkNotNull(newName);

        if (newName.isEmpty() || !isMusicListExists(musicList.getName())) {
            return;
        }

        mAllCustomMusicListName.remove(musicList.getName());

        newName = trimName(newName);

        mAllCustomMusicListName.add(newName);
        musicList.musicListEntity.name = newName;
        updateMusicList(musicList);
    }

    @NonNull
    public synchronized List<MusicList> getAllCustomMusicList() {
        checkThread();

        List<MusicListEntity> allEntity = mMusicListEntityBox.query()
                .notEqual(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC)
                .and()
                .notEqual(MusicListEntity_.name, MUSIC_LIST_FAVORITE)
                .build()
                .find();

        if (allEntity.isEmpty()) {
            return Collections.emptyList();
        }

        List<MusicList> allMusicList = new ArrayList<>(allEntity.size());

        for (MusicListEntity entity : allEntity) {
            allMusicList.add(new MusicList(entity));
        }

        return allMusicList;
    }

    public synchronized void addToAllMusicList(@NonNull Music music, @NonNull List<String> allMusicListName) {
        Preconditions.checkNotNull(music);
        Preconditions.checkNotNull(allMusicListName);

        List<MusicListEntity> entityList = new ArrayList<>();
        for (String name : allMusicListName) {
            MusicList musicList = getCustomMusicList(name);
            if (musicList == null) {
                continue;
            }
            musicList.getMusicElements().add(music);
            musicList.applyChanges();
            entityList.add(musicList.musicListEntity);
        }

        mMusicListEntityBox.put(entityList);
    }

    public synchronized void addToAllMusicList(@NonNull List<Music> allMusic, @NonNull List<String> allMusicListName) {
        Preconditions.checkNotNull(allMusic);
        Preconditions.checkNotNull(allMusicListName);

        List<MusicListEntity> entityList = new ArrayList<>();
        for (String name : allMusicListName) {
            MusicList musicList = getCustomMusicList(name);
            if (musicList == null) {
                continue;
            }
            musicList.getMusicElements().addAll(allMusic);
            musicList.applyChanges();
            entityList.add(musicList.musicListEntity);
        }

        mMusicListEntityBox.put(entityList);
    }

    public synchronized boolean isFavorite(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        return isFavorite(music.getId());
    }

    public synchronized boolean isFavorite(long musicId) {
        checkThread();
        if (musicId <= 0) {
            return false;
        }

        QueryBuilder<Music> builder = mMusicBox.query().equal(Music_.id, musicId);
        builder.backlink(MusicListEntity_.musicElements)
                .equal(MusicListEntity_.name, MUSIC_LIST_FAVORITE);

        return builder.build().count() > 0;
    }

    public synchronized MusicList getLocalMusicList() {
        checkThread();
        return getBuiltInMusicList(MUSIC_LIST_LOCAL_MUSIC);
    }

    @NonNull
    public synchronized MusicList getFavoriteMusicList() {
        checkThread();
        return getBuiltInMusicList(MUSIC_LIST_FAVORITE);
    }

    public synchronized void addToFavorite(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        if (isFavorite(music)) {
            return;
        }

        MusicList favorite = getFavoriteMusicList();
        favorite.getMusicElements().add(music);
        updateMusicList(favorite);
        notifyFavoriteChanged();
    }

    public synchronized void removeFromFavorite(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        if (isFavorite(music)) {
            MusicList favorite = getFavoriteMusicList();
            favorite.getMusicElements().remove(music);
            updateMusicList(favorite);
            notifyFavoriteChanged();
        }
    }

    public synchronized void toggleFavorite(@NonNull Music music) {
        Objects.requireNonNull(music);
        checkThread();

        if (isFavorite(music)) {
            removeFromFavorite(music);
        } else {
            addToFavorite(music);
        }
    }

    private void notifyFavoriteChanged() {
        mMainHandler.post(() -> {
            for (OnFavoriteChangeListener listener : mAllFavoriteChangeListener) {
                listener.onFavoriteChanged();
            }
        });
    }

    private void notifyCustomMusicListUpdated(String name) {
        mMainHandler.post(() -> {
            for (OnCustomMusicListUpdateListener listener : mAllCustomMusicListUpdateListener) {
                listener.onCustomMusicListUpdate(name);
            }
        });
    }

    public synchronized void addOnFavoriteChangeListener(@NonNull OnFavoriteChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllFavoriteChangeListener.contains(listener)) {
            return;
        }

        mAllFavoriteChangeListener.add(listener);
    }

    public synchronized void removeOnFavoriteChangeListener(OnFavoriteChangeListener listener) {
        if (listener == null) {
            return;
        }

        mAllFavoriteChangeListener.remove(listener);
    }

    public synchronized void addOnCustomMusicListUpdateListener(@NonNull OnCustomMusicListUpdateListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllCustomMusicListUpdateListener.contains(listener)) {
            return;
        }

        mAllCustomMusicListUpdateListener.add(listener);
    }

    public synchronized void removeOnCustomMusicListUpdateListener(OnCustomMusicListUpdateListener listener) {
        if (listener == null) {
            return;
        }

        mAllCustomMusicListUpdateListener.remove(listener);
    }

    public boolean isBuiltInMusicList(@NonNull MusicList musicList) {
        String name = mMusicListEntityBox.query()
                .equal(MusicListEntity_.id, musicList.getId())
                .build()
                .property(MusicListEntity_.name)
                .unique()
                .findString();

        return isBuiltInName(name);
    }

    public static boolean isBuiltInName(String name) {
        return name.equalsIgnoreCase(MUSIC_LIST_LOCAL_MUSIC) ||
                name.equalsIgnoreCase(MUSIC_LIST_FAVORITE);
    }

    public synchronized void addHistory(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        HistoryEntity historyEntity = mHistoryEntityBox.query()
                .equal(HistoryEntity_.musicId, music.id)
                .build()
                .findFirst();

        if (historyEntity == null) {
            historyEntity = new HistoryEntity();
            historyEntity.music.setTarget(music);
        }
        historyEntity.timestamp = System.currentTimeMillis();

        mHistoryEntityBox.put(historyEntity);
    }

    public synchronized void removeHistory(@NonNull HistoryEntity historyEntity) {
        Preconditions.checkNotNull(historyEntity);
        checkThread();

        mHistoryEntityBox.query()
                .equal(HistoryEntity_.id, historyEntity.id)
                .build()
                .remove();
    }

    public synchronized void clearHistory() {
        checkThread();

        mHistoryEntityBox.query()
                .build()
                .remove();
    }

    @NonNull
    public synchronized List<HistoryEntity> getAllHistory() {
        checkThread();

        return mHistoryEntityBox.query()
                .orderDesc(HistoryEntity_.timestamp)
                .build()
                .find();
    }

    public synchronized void putMusic(@NonNull Music music) {
        checkThread();
        Preconditions.checkNotNull(music);
        mMusicBox.put(music);
    }

    @Nullable
    public synchronized Music getMusic(long id) {
        checkThread();
        return mMusicBox.get(id);
    }

    @NonNull
    public synchronized List<Music> getAllMusic() {
        checkThread();
        return mMusicBox.getAll();
    }

    @NonNull
    public synchronized List<Music> getAllMusic(long offset, long limit) {
        checkThread();
        return mMusicBox.query()
                .build()
                .find(offset, limit);
    }

    public synchronized long getMusicCount() {
        checkThread();
        return mMusicBox.count();
    }

    public synchronized boolean removeMusic(@NonNull Music music) {
        checkThread();
        return mMusicBox.remove(music.getId());
    }

    public synchronized void removeMusic(Collection<Music> musics) {
        checkThread();
        mMusicBox.remove(musics);
    }

    public synchronized void putAllMusic(@NonNull Collection<Music> musics) {
        Preconditions.checkNotNull(musics);
        checkThread();
        mMusicBox.put(musics);
    }

    public synchronized void addAllMusic(@NonNull String musicListName, @NonNull List<Music> allMusic) {
        Preconditions.checkNotNull(musicListName);
        Preconditions.checkNotNull(allMusic);

        MusicList musicList;
        if (isBuiltInName(musicListName)) {
            musicList = getBuiltInMusicList(musicListName);
        } else {
            musicList = getCustomMusicList(musicListName);
        }

        if (musicList == null) {
            return;
        }

        musicList.getMusicElements().addAll(allMusic);
        updateMusicList(musicList);
    }

    public synchronized void removeAllMusic(@NonNull String musicListName, @NonNull List<Music> allMusic) {
        Preconditions.checkNotNull(musicListName);
        Preconditions.checkNotNull(allMusic);

        MusicList musicList;
        if (isBuiltInName(musicListName)) {
            musicList = getBuiltInMusicList(musicListName);
        } else {
            musicList = getCustomMusicList(musicListName);
        }

        if (musicList == null) {
            return;
        }

        musicList.getMusicElements().removeAll(allMusic);
        updateMusicList(musicList);
    }

    public synchronized long getId(@NonNull String uri) {
        Preconditions.checkNotNull(uri);

        Long id = mMusicBox.query()
                .equal(Music_.uri, uri)
                .build()
                .property(Music_.id)
                .findLong();

        if (id == null) {
            return 0;
        }

        return id;
    }

    public synchronized boolean isLocalMusic(@NonNull String uri) {
        Preconditions.checkNotNull(uri);

        QueryBuilder<Music> builder = mMusicBox.query()
                .equal(Music_.uri, uri);

        builder.backlink(MusicListEntity_.musicElements)
                .equal(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC);

        return builder.build().count() > 0;
    }

    @NonNull
    public synchronized List<String> getAllArtist() {
        checkThread();
        return new ArrayList<>(Arrays.asList(mMusicBox.query()
                .build()
                .property(Music_.artist)
                .distinct()
                .findStrings()));
    }

    @NonNull
    public synchronized List<String> getAllAlbum() {
        checkThread();
        return new ArrayList<>(Arrays.asList(mMusicBox.query()
                .build()
                .property(Music_.album)
                .distinct()
                .findStrings()));
    }

    @NonNull
    public synchronized List<Music> getArtistAllMusic(@NonNull String artist) {
        Preconditions.checkNotNull(artist);
        checkThread();

        return mMusicBox.query()
                .equal(Music_.artist, artist)
                .build()
                .find();
    }

    public synchronized List<Music> getArtistAllMusic(@NonNull String artist, long offset, long limit) {
        Preconditions.checkNotNull(artist);
        checkThread();

        return mMusicBox.query()
                .equal(Music_.artist, artist)
                .build()
                .find(offset, limit);
    }

    @NonNull
    public synchronized List<Music> getAlbumAllMusic(@NonNull String album) {
        Preconditions.checkNotNull(album);
        checkThread();

        return mMusicBox.query()
                .equal(Music_.album, album)
                .build()
                .find();
    }

    public synchronized List<Music> getAlbumAllMusic(@NonNull String album, long offset, long limit) {
        Preconditions.checkNotNull(album);
        checkThread();

        return mMusicBox.query()
                .equal(Music_.album, album)
                .build()
                .find(offset, limit);
    }

    @NonNull
    private synchronized MusicList getBuiltInMusicList(String name) {
        if (!isBuiltInName(name)) {
            throw new IllegalArgumentException("not built-in name:" + name);
        }

        MusicListEntity entity = mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name)
                .build()
                .findUnique();

        if (entity != null) {
            return new MusicList(entity);
        }

        entity = createBuiltInMusicList(name);

        return new MusicList(entity);
    }

    private MusicListEntity createBuiltInMusicList(String name) {
        MusicListEntity entity = new MusicListEntity(0, name, 0, MusicList.SortOrder.BY_ADD_TIME, new byte[0]);
        mMusicListEntityBox.put(entity);
        return entity;
    }

    public void setOnScanCompleteListener(@Nullable OnScanCompleteListener listener) {
        mOnScanCompleteListener = listener;
    }

    public void notifyScanComplete() {
        if (mOnScanCompleteListener != null) {
            mOnScanCompleteListener.onScanComplete();
        }
    }

    @NonNull
    public List<Music> findMusicListMusic(@NonNull String musicListName, @NonNull String key) {
        Preconditions.checkNotNull(musicListName);
        Preconditions.checkNotNull(key);

        if (musicListName.isEmpty() || key.isEmpty()) {
            return Collections.emptyList();
        }

        QueryBuilder<Music> builder = mMusicBox.query();

        builder.backlink(MusicListEntity_.musicElements)
                .equal(MusicListEntity_.name, musicListName);

        return builder.contains(Music_.title, key)
                .build()
                .find();
    }

    @NonNull
    public List<Music> findArtistMusic(@NonNull String artistName, @NonNull String key) {
        Preconditions.checkNotNull(artistName);
        Preconditions.checkNotNull(key);

        if (artistName.isEmpty() || key.isEmpty()) {
            return Collections.emptyList();
        }

        return mMusicBox.query()
                .equal(Music_.artist, artistName)
                .and()
                .contains(Music_.title, key)
                .build()
                .find();
    }

    @NonNull
    public List<Music> findAlbumMusic(@NonNull String albumName, @NonNull String key) {
        Preconditions.checkNotNull(albumName);
        Preconditions.checkNotNull(key);

        if (albumName.isEmpty() || key.isEmpty()) {
            return Collections.emptyList();
        }

        return mMusicBox.query()
                .equal(Music_.album, albumName)
                .and()
                .contains(Music_.title, key)
                .build()
                .find();
    }

    public interface OnFavoriteChangeListener {
        void onFavoriteChanged();
    }

    public interface OnScanCompleteListener {
        void onScanComplete();
    }

    public interface SortCallback {
        void onSortFinished();
    }

    public interface OnCustomMusicListUpdateListener {
        void onCustomMusicListUpdate(String name);
    }
}
