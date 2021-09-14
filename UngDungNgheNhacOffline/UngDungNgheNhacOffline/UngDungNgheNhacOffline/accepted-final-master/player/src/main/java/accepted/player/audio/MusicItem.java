package accepted.player.audio;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import accepted.player.R;

public final class MusicItem implements Parcelable {
    private String musicId;
    private String title;
    private String artist;
    private String album;
    private String uri;
    private String iconUri;
    private int duration;
    private boolean forbidSeek;
    @Nullable
    private Bundle extra;

    public MusicItem() {
        this.musicId = "";
        this.title = "";
        this.artist = "";
        this.album = "";
        this.uri = "";
        this.iconUri = "";
        this.duration = 0;
        this.extra = null;
        forbidSeek = false;
    }

    public MusicItem(MusicItem source) {
        musicId = source.musicId;
        title = source.title;
        artist = source.artist;
        album = source.album;
        uri = source.uri;
        iconUri = source.iconUri;
        duration = source.duration;
        forbidSeek = source.forbidSeek;
        if (source.extra != null) {
            extra = new Bundle(source.extra);
        }
    }

    @NonNull
    public String getMusicId() {
        return musicId;
    }

    public void setMusicId(@NonNull String musicId) {
        Preconditions.checkNotNull(musicId);
        this.musicId = musicId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        Preconditions.checkNotNull(title);
        this.title = title;
    }

    @NonNull
    public String getArtist() {
        return artist;
    }

    public void setArtist(@NonNull String artist) {
        Preconditions.checkNotNull(artist);
        this.artist = artist;
    }

    @NonNull
    public String getAlbum() {
        return album;
    }

    public void setAlbum(@NonNull String album) {
        Preconditions.checkNotNull(album);
        this.album = album;
    }

    @NonNull
    public String getUri() {
        return uri;
    }

    public void setUri(@NonNull String uri) {
        Preconditions.checkNotNull(uri);
        this.uri = uri;
    }

    @NonNull
    public String getIconUri() {
        return iconUri;
    }

    public void setIconUri(@NonNull String iconUri) {
        Preconditions.checkNotNull(iconUri);
        this.iconUri = iconUri;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        if (duration < 0) {
            this.duration = 0;
            return;
        }

        this.duration = duration;
    }

    public boolean isForbidSeek() {
        return forbidSeek;
    }

    public void setForbidSeek(boolean forbidSeek) {
        this.forbidSeek = forbidSeek;
    }

    @Nullable
    public Bundle getExtra() {
        return extra;
    }

    public void setExtra(@Nullable Bundle extra) {
        this.extra = extra;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MusicItem)) return false;
        MusicItem other = (MusicItem) o;
        return Objects.equal(musicId, other.musicId) &&
                Objects.equal(title, other.title) &&
                Objects.equal(artist, other.artist) &&
                Objects.equal(album, other.album) &&
                Objects.equal(uri, other.uri) &&
                Objects.equal(iconUri, other.iconUri) &&
                Objects.equal(duration, other.duration) &&
                Objects.equal(forbidSeek, other.forbidSeek);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(musicId,
                title,
                artist,
                album,
                uri,
                iconUri,
                duration,
                forbidSeek);
    }

    @Override
    public String toString() {
        return "MusicItem{" +
                "musicId='" + musicId + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", uri='" + uri + '\'' +
                ", iconUri='" + iconUri + '\'' +
                ", duration=" + duration +
                ", forbidSeek=" + forbidSeek +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.musicId);
        dest.writeString(this.title);
        dest.writeString(this.artist);
        dest.writeString(this.album);
        dest.writeString(this.uri);
        dest.writeString(this.iconUri);
        dest.writeInt(this.duration);
        dest.writeByte((byte) (this.forbidSeek ? 1 : 0));
        dest.writeParcelable(extra, 0);
    }

    protected MusicItem(Parcel in) {
        this.musicId = in.readString();
        this.title = in.readString();
        this.artist = in.readString();
        this.album = in.readString();
        this.uri = in.readString();
        this.iconUri = in.readString();
        this.duration = in.readInt();
        this.forbidSeek = in.readByte() == 1;
        this.extra = in.readParcelable(Thread.currentThread().getContextClassLoader());
    }

    public static final Creator<MusicItem> CREATOR = new Creator<MusicItem>() {
        @Override
        public MusicItem createFromParcel(Parcel source) {
            return new MusicItem(source);
        }

        @Override
        public MusicItem[] newArray(int size) {
            return new MusicItem[size];
        }
    };

    public static class Builder {
        private String musicId = "";
        private String title = "";
        private String artist = "";
        private String album = "";
        private String uri;
        private String iconUri = "";
        private int duration;
        private boolean forbidSeek = false;
        private Bundle extra;

        public Builder() {
        }

        public Builder(@NonNull Context context) {
            Preconditions.checkNotNull(context);
            this.title = context.getString(R.string.accepted_music_item_unknown_title);
            this.artist = context.getString(R.string.accepted_music_item_unknown_artist);
            this.album = context.getString(R.string.accepted_music_item_unknown_album);
        }

        public Builder setMusicId(@NonNull String musicId) {
            Preconditions.checkNotNull(musicId);
            this.musicId = musicId;
            return this;
        }

        public Builder setTitle(@NonNull String title) {
            Preconditions.checkNotNull(title);
            this.title = title;
            return this;
        }

        public Builder setArtist(@NonNull String artist) {
            Preconditions.checkNotNull(artist);
            this.artist = artist;
            return this;
        }

        public Builder setAlbum(@NonNull String album) {
            Preconditions.checkNotNull(album);
            this.album = album;
            return this;
        }

        public Builder setUri(@NonNull String uri) {
            Preconditions.checkNotNull(uri);
            this.uri = uri;
            return this;
        }

        public Builder setIconUri(@NonNull String iconUri) {
            Preconditions.checkNotNull(iconUri);
            this.iconUri = iconUri;
            return this;
        }

        public Builder setDuration(int duration) throws IllegalArgumentException {
            if (duration < 0) {
                this.duration = 0;
                return this;
            }

            this.duration = duration;
            return this;
        }

        public Builder setForbidSeek(boolean forbidSeek) {
            this.forbidSeek = forbidSeek;
            return this;
        }

        public Builder setExtra(@Nullable Bundle extra) {
            this.extra = extra;
            return this;
        }

        public MusicItem build() {
            MusicItem musicItem = new MusicItem();

            musicItem.setMusicId(musicId);
            musicItem.setTitle(title);
            musicItem.setArtist(artist);
            musicItem.setAlbum(album);
            musicItem.setUri(uri);
            musicItem.setIconUri(iconUri);
            musicItem.setDuration(duration);
            musicItem.setForbidSeek(forbidSeek);
            musicItem.setExtra(extra);

            return musicItem;
        }
    }
}
