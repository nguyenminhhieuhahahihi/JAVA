package accepted.music.activity.favorite;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.view.View;

import accepted.music.R;
import accepted.music.activity.ListActivity;
import accepted.music.activity.search.SearchActivity;
import accepted.music.service.AppPlayerService;
import accepted.music.store.MusicStore;
import accepted.music.util.PlayerUtil;
import accepted.player.lifecycle.PlayerViewModel;

public class FavoriteActivity extends ListActivity {
    private FavoriteMusicListFragment mFavoriteMusicListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);

        initPlayerClient();

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.musicListContainer);
        if (fragment instanceof FavoriteMusicListFragment) {
            mFavoriteMusicListFragment = (FavoriteMusicListFragment) fragment;
        } else {
            mFavoriteMusicListFragment = FavoriteMusicListFragment.newInstance();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.musicListContainer, mFavoriteMusicListFragment, "favoriteMusicList")
                    .commit();
        }
    }

    private void initPlayerClient() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        PlayerViewModel playerViewModel = viewModelProvider.get(PlayerViewModel.class);
        PlayerUtil.initPlayerViewModel(this, playerViewModel, AppPlayerService.class);
        setPlayerClient(playerViewModel.getPlayerClient());
    }

    public void finishSelf(View view) {
        finish();
    }

    public void onOptionMenuClicked(View view) {
        int id = view.getId();
        if (id == R.id.btnSearch) {
            SearchActivity.start(this, SearchActivity.Type.MUSIC_LIST, MusicStore.MUSIC_LIST_FAVORITE);
        } else if (id == R.id.btnSort) {
            mFavoriteMusicListFragment.showSortDialog();
        }
    }
}