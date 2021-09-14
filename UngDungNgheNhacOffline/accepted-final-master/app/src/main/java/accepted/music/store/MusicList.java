package accepted.music.store;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import pinyin.util.PinyinComparator;


public class MusicList {
    final MusicListEntity musicListEntity;
    private ElementList mElementList;

    MusicList(@NonNull MusicListEntity musicListEntity) {
        Preconditions.checkNotNull(musicListEntity);

        this.musicListEntity = musicListEntity;
    }

    synchronized void applyChanges() {
        if (mElementList == null) {
            return;
        }

        mElementList.applyChanges();
    }

    public long getId() {
        return musicListEntity.id;
    }

    @NonNull
    public String getName() {
        return musicListEntity.name;
    }

    @NonNull
    public SortOrder getSortOrder() {
        return musicListEntity.sortOrder;
    }

    public boolean isBuiltIn() {
        return MusicStore.isBuiltInName(musicListEntity.name);
    }

    public int getSize() {
        return musicListEntity.size;
    }

    public void load() {
        if (mElementList == null) {
            mElementList = new ElementList();
        }
    }

    public synchronized List<Music> getMusicElements() {
        if (mElementList == null) {
            mElementList = new ElementList();
        }
        return mElementList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MusicList musicList = (MusicList) o;

        return musicListEntity.id == musicList.musicListEntity.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(musicListEntity.id);
    }

    private class ElementList implements List<Music> {
        private List<Music> mOrderedList;

        ElementList() {
            if (musicListEntity.orderBytes == null || musicListEntity.orderBytes.length <= 0) {
                mOrderedList = new ArrayList<>(musicListEntity.musicElements);
                return;
            }

            try {
                ByteArrayInputStream byteInput = new ByteArrayInputStream(musicListEntity.orderBytes);
                ObjectInputStream input = new ObjectInputStream(byteInput);

                mOrderedList = new ArrayList<>();
                while (input.available() > 0) {
                    long id = input.readLong();
                    if (id <= 0) {
                        mOrderedList = new ArrayList<>(musicListEntity.musicElements);
                        return;
                    }
                    mOrderedList.add(musicListEntity.musicElements.getById(id));
                }

                input.close();
            } catch (IOException e) {
                mOrderedList = new ArrayList<>(musicListEntity.musicElements);
                e.printStackTrace();
            }
        }

        void applyChanges() {
            musicListEntity.orderBytes = getOrderBytes();
            musicListEntity.size = mOrderedList.size();
        }

        @NonNull
        private byte[] getOrderBytes() {
            try {
                ByteArrayOutputStream byteOutput = new ByteArrayOutputStream(mOrderedList.size() * 4);
                ObjectOutputStream output = new ObjectOutputStream(byteOutput);

                for (Music music : mOrderedList) {
                    output.writeLong(music.id);
                }

                output.flush();
                output.close();
                return byteOutput.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return new byte[0];
        }

        @Override
        public int size() {
            return mOrderedList.size();
        }

        @Override
        public boolean isEmpty() {
            return mOrderedList.isEmpty();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return mOrderedList.contains(o);
        }

        @NonNull
        @Override
        public Iterator<Music> iterator() {
            return mOrderedList.iterator();
        }

        @NonNull
        @Override
        public Object[] toArray() {
            return mOrderedList.toArray();
        }

        @SuppressWarnings("SuspiciousToArrayCall")
        @NonNull
        @Override
        public <T> T[] toArray(@NonNull T[] a) {
            return mOrderedList.toArray(a);
        }

        @Override
        public boolean add(Music t) {
            if (contains(t)) {
                return false;
            }

            boolean result = mOrderedList.add(t);

            if (result) {
                musicListEntity.musicElements.add(t);
                musicListEntity.size = musicListEntity.musicElements.size();
            }

            return result;
        }

        @Override
        public boolean remove(@Nullable Object o) {
            boolean result = mOrderedList.remove(o);

            if (result) {
                musicListEntity.musicElements.remove(o);
                musicListEntity.size = musicListEntity.musicElements.size();
            }

            return result;
        }

        @Override
        public boolean containsAll(@NonNull Collection<?> c) {
            Preconditions.checkNotNull(c);
            return mOrderedList.containsAll(c);
        }

        @Override
        public boolean addAll(@NonNull Collection<? extends Music> c) {
            Preconditions.checkNotNull(c);

            c = excludeDuplicates(c, true);

            boolean result = mOrderedList.addAll(c);

            if (result) {
                musicListEntity.musicElements.addAll(c);
                musicListEntity.size = musicListEntity.musicElements.size();
            }

            return result;
        }

        @Override
        public boolean addAll(int index, @NonNull Collection<? extends Music> c) {
            Preconditions.checkNotNull(c);

            c = excludeDuplicates(c, true);

            boolean result = mOrderedList.addAll(index, c);

            if (result) {
                musicListEntity.musicElements.addAll(c);
                musicListEntity.size = musicListEntity.musicElements.size();
            }

            return result;
        }

        @Override
        public boolean removeAll(@NonNull Collection<?> c) {
            Preconditions.checkNotNull(c);

            boolean result = mOrderedList.removeAll(c);

            if (result) {
                musicListEntity.musicElements.removeAll(c);
                musicListEntity.size = musicListEntity.musicElements.size();
            }

            return result;
        }

        @Override
        public boolean retainAll(@NonNull Collection<?> c) {
            Preconditions.checkNotNull(c);

            c = excludeDuplicates(c, false);

            boolean result = mOrderedList.retainAll(c);

            if (result) {
                musicListEntity.musicElements.retainAll(c);
                musicListEntity.size = musicListEntity.musicElements.size();
            }

            return result;
        }

        private Collection<Music> excludeDuplicates(Collection<?> c, boolean excludeExists) {
            List<Music> musicList = new ArrayList<>();

            for (Object music : c) {
                if (!(music instanceof Music)) {
                    continue;
                }

                if (musicList.contains(music)) {
                    continue;
                }

                if (excludeExists && contains(music)) {
                    continue;
                }

                musicList.add((Music) music);
            }

            return musicList;
        }

        @Override
        public void clear() {
            musicListEntity.musicElements.clear();
            musicListEntity.size = musicListEntity.musicElements.size();
            mOrderedList.clear();
        }

        @Override
        public Music get(int index) {
            return mOrderedList.get(index);
        }

        @Override
        public Music set(int index, Music element) {
            int elementIndex = indexOf(element);

            Music music = mOrderedList.set(index, element);
            musicListEntity.musicElements.set(musicListEntity.musicElements.indexOf(music), element);

            if (elementIndex > 0 && elementIndex != index) {
                remove(elementIndex);
            }

            return music;
        }

        @Override
        public void add(int index, Music element) {
            int elementIndex = indexOf(element);

            musicListEntity.musicElements.add(index, element);
            musicListEntity.size = musicListEntity.musicElements.size();

            if (elementIndex > -1) {
                remove(element);
            }

            mOrderedList.add(index, element);
        }

        @Override
        public Music remove(int index) {
            Music music = mOrderedList.remove(index);
            musicListEntity.musicElements.remove(music);
            musicListEntity.size = musicListEntity.musicElements.size();
            return music;
        }

        @Override
        public int indexOf(@Nullable Object o) {
            return mOrderedList.indexOf(o);
        }

        @Override
        public int lastIndexOf(@Nullable Object o) {
            return mOrderedList.lastIndexOf(o);
        }

        @NonNull
        @Override
        public ListIterator<Music> listIterator() {
            return mOrderedList.listIterator();
        }

        @NonNull
        @Override
        public ListIterator<Music> listIterator(int index) {
            return mOrderedList.listIterator(index);
        }

        @NonNull
        @Override
        public List<Music> subList(int fromIndex, int toIndex) {
            return mOrderedList.subList(fromIndex, toIndex);
        }
    }

    public enum SortOrder {
        BY_ADD_TIME(0) {
            @NonNull
            @Override
            public Comparator<Music> comparator() {
                return (o1, o2) -> Long.compare(o1.getAddTime(), o2.getAddTime());
            }
        },
        BY_TITLE(1) {
            @NonNull
            @Override
            public Comparator<Music> comparator() {
                return new Comparator<Music>() {
                    private final PinyinComparator mPinyinComparator = new PinyinComparator();

                    @Override
                    public int compare(Music o1, Music o2) {
                        return mPinyinComparator.compare(o1.getTitle(), o2.getTitle());
                    }
                };
            }
        },
        BY_ARTIST(2) {
            @NonNull
            @Override
            public Comparator<Music> comparator() {
                return new Comparator<Music>() {
                    private final PinyinComparator mPinyinComparator = new PinyinComparator();

                    @Override
                    public int compare(Music o1, Music o2) {
                        return mPinyinComparator.compare(o1.getArtist(), o2.getArtist());
                    }
                };
            }
        },
        BY_ALBUM(3) {
            @NonNull
            @Override
            public Comparator<Music> comparator() {
                return new Comparator<Music>() {
                    private final PinyinComparator mPinyinComparator = new PinyinComparator();

                    @Override
                    public int compare(Music o1, Music o2) {
                        return mPinyinComparator.compare(o1.getAlbum(), o2.getAlbum());
                    }
                };
            }
        };

        public final int id;

        @NonNull
        public abstract Comparator<Music> comparator();

        SortOrder(int id) {
            this.id = id;
        }

        public static SortOrder getValueById(int id) {
            switch (id) {
                case 1:
                    return BY_TITLE;
                case 2:
                    return BY_ARTIST;
                case 3:
                    return BY_ALBUM;
                default:
                    return BY_ADD_TIME;
            }
        }
    }
}
