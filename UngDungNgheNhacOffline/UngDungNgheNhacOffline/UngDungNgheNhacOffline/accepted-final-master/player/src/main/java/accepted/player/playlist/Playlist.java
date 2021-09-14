package accepted.player.playlist;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import accepted.player.audio.MusicItem;
import accepted.player.util.MusicItemUtil;

public final class Playlist implements Iterable<MusicItem>, Parcelable {
    private static final String TAG = "Playlist";
    public static final int MAX_SIZE = 1000;

    private final String mName;
    private final String mToken;
    private final ArrayList<MusicItem> mMusicItems;
    private final boolean mEditable;
    @Nullable
    private final Bundle mExtra;

    public Playlist(@NonNull String name, @NonNull List<MusicItem> items, boolean editable, @Nullable Bundle extra) {
        this(name, items, 0, editable, extra);
    }

    public Playlist(@NonNull String name, @NonNull List<MusicItem> items, int position, boolean editable, @Nullable Bundle extra) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(items);

        mName = name;
        mMusicItems = trim(excludeRepeatItem(items), position);
        mEditable = editable;
        mExtra = extra;

        mToken = generateToken();
    }

    private ArrayList<MusicItem> excludeRepeatItem(List<MusicItem> items) {
        ArrayList<MusicItem> musicItems = new ArrayList<>();

        for (MusicItem item : items) {
            if (musicItems.contains(item)) {
                continue;
            }

            musicItems.add(item);
        }

        return musicItems;
    }

    private ArrayList<MusicItem> trim(ArrayList<MusicItem> musicItems, int position) {
        int size = musicItems.size();

        int start = 0;
        int end = musicItems.size();

        if (size > Playlist.MAX_SIZE) {
            start = position - Math.max(0, Playlist.MAX_SIZE - (size - position));
            end = position + Math.min(Playlist.MAX_SIZE, size - position);
        }

        return new ArrayList<>(musicItems.subList(start, end));
    }

    private String generateToken() {
        return MusicItemUtil.generateToken(mMusicItems, new MusicItemUtil.GetUriFunction<MusicItem>() {
            @NonNull
            @Override
            public String getUri(MusicItem item) {
                return item.getUri();
            }
        });
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public String getToken() {
        return mToken;
    }

    public boolean isEditable() {
        return mEditable;
    }

    public boolean contains(MusicItem musicItem) {
        return mMusicItems.contains(musicItem);
    }

    public MusicItem get(int index) throws IndexOutOfBoundsException {
        return mMusicItems.get(index);
    }

    public int indexOf(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        return mMusicItems.indexOf(musicItem);
    }

    public boolean isEmpty() {
        return mMusicItems.isEmpty();
    }

    @NonNull
    @Override
    public Iterator<MusicItem> iterator() {
        return new Iterator<MusicItem>() {
            private final Iterator<MusicItem> iterator = mMusicItems.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public MusicItem next() {
                return iterator.next();
            }

            @Override
            public void remove() {
                Log.e(TAG, "unsupported operation");
            }
        };
    }

    public int size() {
        return mMusicItems.size();
    }

    public List<MusicItem> getAllMusicItem() {
        return new ArrayList<>(mMusicItems);
    }

    @Nullable
    public Bundle getExtra() {
        return mExtra;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Playlist)) {
            return false;
        }

        Playlist other = (Playlist) obj;

        return Objects.equal(mName, other.mName) &&
                Objects.equal(mToken, other.mToken) &&
                Objects.equal(mMusicItems, other.mMusicItems) &&
                Objects.equal(mEditable, other.mEditable);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mName,
                mToken,
                mMusicItems,
                mEditable);
    }

    // Parcelable
    protected Playlist(Parcel in) {
        mName = in.readString();
        mToken = in.readString();
        mMusicItems = in.createTypedArrayList(MusicItem.CREATOR);
        mEditable = in.readByte() != 0;
        mExtra = in.readBundle(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mToken);
        dest.writeTypedList(mMusicItems);
        dest.writeByte((byte) (mEditable ? 1 : 0));
        dest.writeBundle(mExtra);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Playlist> CREATOR = new Creator<Playlist>() {
        @Override
        public Playlist createFromParcel(Parcel in) {
            return new Playlist(in);
        }

        @Override
        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };

    public static final class Builder {
        private String mName;
        private final List<MusicItem> mMusicItems;
        private boolean mEditable;
        private Bundle mExtra;
        private int mPosition;

        public Builder() {
            mName = "";
            mMusicItems = new ArrayList<>();
            mPosition = 0;
            mEditable = true;
        }

        public Builder setName(@NonNull String name) {
            Preconditions.checkNotNull(name);
            mName = name;
            return this;
        }

        public Builder setEditable(boolean editable) {
            mEditable = editable;
            return this;
        }

        public Builder append(@NonNull MusicItem musicItem) {
            Preconditions.checkNotNull(musicItem);
            mMusicItems.add(musicItem);
            return this;
        }

        public Builder appendAll(@NonNull List<MusicItem> musicItems) {
            Preconditions.checkNotNull(musicItems);
            mMusicItems.addAll(musicItems);
            return this;
        }

        public Builder remove(@NonNull MusicItem musicItem) {
            Preconditions.checkNotNull(musicItem);
            mMusicItems.remove(musicItem);
            return this;
        }

        public Builder removeAll(@NonNull List<MusicItem> musicItems) {
            Preconditions.checkNotNull(musicItems);
            mMusicItems.removeAll(musicItems);
            return this;
        }

        public Builder setPosition(int position) {
            mPosition = position;
            return this;
        }

        public Builder setExtra(@Nullable Bundle extra) {
            mExtra = extra;
            return this;
        }

        public Playlist build() {
            return new Playlist(mName, mMusicItems, mPosition, mEditable, mExtra);
        }
    }
}
