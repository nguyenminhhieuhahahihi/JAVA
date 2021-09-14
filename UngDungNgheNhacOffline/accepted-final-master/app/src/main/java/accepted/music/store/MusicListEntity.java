package accepted.music.store;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Unique;
import io.objectbox.relation.ToMany;
import accepted.music.store.converter.SortOrderConverter;

@Entity
public class MusicListEntity {
    @Id
    long id;
    @Unique
    String name;
    int size;
    @Convert(converter = SortOrderConverter.class, dbType = Integer.class)
    MusicList.SortOrder sortOrder;

    byte[] orderBytes;
    ToMany<Music> musicElements;

    public MusicListEntity() {
        this.name = "";
    }

    public MusicListEntity(long id, String name, int size, MusicList.SortOrder sortOrder, byte[] orderBytes) {
        this.id = id;
        this.name = name;
        this.orderBytes = orderBytes;
        this.size = size;
        this.sortOrder = sortOrder;
    }
}
