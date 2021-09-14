package accepted.player;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import accepted.player.audio.MusicItem;
import accepted.player.playlist.Playlist;
import accepted.player.playlist.PlaylistManager;

class PlaylistManagerImp implements PlaylistManager {
    private static final String KEY_PLAYLIST = "playlist";
    private static final String KEY_PLAYLIST_SIZE = "playlist_size";
    private static final String KEY_NAME = "name";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EDITABLE = "editable";
    private static final String KEY_LAST_MODIFIED = "last_modified";

    private final MMKV mMMKV;
    private Disposable mSaveDisposable;

    PlaylistManagerImp(@NonNull Context context, @NonNull String playlistId) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playlistId);

        MMKV.initialize(context);
        mMMKV = MMKV.mmkvWithID("PlaylistManager:" + playlistId, MMKV.MULTI_PROCESS_MODE);
    }

    @NonNull
    @Override
    public String getPlaylistName() {
        return mMMKV.decodeString(KEY_NAME, "");
    }

    @Override
    public int getPlaylistSize() {
        return mMMKV.decodeInt(KEY_PLAYLIST_SIZE, 0);
    }

    @NonNull
    @Override
    public String getPlaylistToken() {
        return mMMKV.decodeString(KEY_TOKEN, "");
    }

    @Override
    public boolean isPlaylistEditable() {
        return mMMKV.decodeBool(KEY_EDITABLE, true);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    @Override
    public void getPlaylist(@NonNull final Callback callback) {
        Single.create(new SingleOnSubscribe<Playlist>() {
            @Override
            public void subscribe(SingleEmitter<Playlist> emitter) {
                Playlist playlist = mMMKV.decodeParcelable(KEY_PLAYLIST, Playlist.class);
                if (playlist == null) {
                    playlist = new Playlist.Builder().build();
                }
                emitter.onSuccess(playlist);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Playlist>() {
                    @Override
                    public void accept(Playlist playlist) {
                        callback.onFinished(playlist);
                    }
                });
    }

    @Override
    public long getLastModified() {
        return mMMKV.decodeLong(KEY_LAST_MODIFIED, System.currentTimeMillis());
    }

    public void save(@NonNull final Playlist playlist, @Nullable final Runnable doOnSaved) {
        Preconditions.checkNotNull(playlist);

        disposeLastSave();
        mSaveDisposable = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) {
                if (emitter.isDisposed()) {
                    return;
                }

                mMMKV.encode(KEY_PLAYLIST, playlist);
                mMMKV.encode(KEY_PLAYLIST_SIZE, playlist.size());
                mMMKV.encode(KEY_NAME, playlist.getName());
                mMMKV.encode(KEY_TOKEN, playlist.getToken());
                mMMKV.encode(KEY_EDITABLE, playlist.isEditable());
                mMMKV.encode(KEY_LAST_MODIFIED, System.currentTimeMillis());

                if (emitter.isDisposed()) {
                    return;
                }
                emitter.onSuccess(true);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) {
                        if (doOnSaved != null) {
                            doOnSaved.run();
                        }
                    }
                });
    }

    private void disposeLastSave() {
        if (mSaveDisposable != null && !mSaveDisposable.isDisposed()) {
            mSaveDisposable.dispose();
        }
    }
}
