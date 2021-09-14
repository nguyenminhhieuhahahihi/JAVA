package accepted.music.activity.favorite;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import accepted.music.R;
import accepted.music.dialog.BottomDialog;
import accepted.music.dialog.BottomMenuDialog;
import accepted.music.fragment.musiclist.BaseMusicListFragment;
import accepted.music.fragment.musiclist.BaseMusicListViewModel;
import accepted.music.store.Music;
import accepted.music.store.MusicStore;

public class FavoriteMusicListFragment extends BaseMusicListFragment {

    public static FavoriteMusicListFragment newInstance() {
        return new FavoriteMusicListFragment();
    }

    @Override
    protected BaseMusicListViewModel onCreateMusicListViewModel(ViewModelProvider viewModelProvider) {
        FavoriteMusicListViewModel viewModel = viewModelProvider.get(FavoriteMusicListViewModel.class);
        viewModel.init(MusicStore.MUSIC_LIST_FAVORITE);
        return viewModel;
    }

    @Override
    protected void showItemOptionMenu(@NonNull Music music) {
        BottomDialog bottomDialog = new BottomMenuDialog.Builder(Objects.requireNonNull(getContext()))
                .setTitle(music.getTitle())
                .addMenuItem(R.drawable.ic_menu_item_next_play, R.string.menu_item_next_play)
                .addMenuItem(R.drawable.ic_menu_item_add, R.string.menu_item_add_to_music_list)
                .addMenuItem(R.drawable.ic_menu_item_rington, R.string.menu_item_set_as_ringtone)
                .addMenuItem(R.drawable.ic_menu_item_remove, R.string.menu_item_remove)
                .setOnMenuItemClickListener((dialog, position) -> {
                    dialog.dismiss();
                    switch (position) {
                        case 0:
                            setNextPlay(music);
                            break;
                        case 1:
                            addToMusicList(music);
                            break;
                        case 2:
                            setAsRingtone(music);
                            break;
                        case 3:
                            removeMusic(music);
                            break;
                    }
                })
                .build();

        bottomDialog.show(getParentFragmentManager(), "showItemOptionMenu");
    }
}
