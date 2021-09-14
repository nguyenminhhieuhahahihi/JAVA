package accepted.music.activity.localmusic;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import accepted.music.R;
import accepted.music.activity.ListActivity;
import accepted.music.activity.search.SearchActivity;
import accepted.music.dialog.MessageDialog;
import accepted.music.dialog.ScannerDialog;
import accepted.music.fragment.musiclist.MusicListFragment;
import accepted.music.service.AppPlayerService;
import accepted.music.store.MusicStore;
import accepted.music.util.PlayerUtil;
import accepted.player.lifecycle.PlayerViewModel;

public class LocalMusicActivity extends ListActivity {
    private MusicListFragment mMusicListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_music);

        initPlayerClient();

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.musicListContainer);
        if (fragment instanceof MusicListFragment) {
            mMusicListFragment = (MusicListFragment) fragment;
        } else {
            mMusicListFragment = MusicListFragment.newInstance(MusicStore.MUSIC_LIST_LOCAL_MUSIC);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.musicListContainer, mMusicListFragment, "MusicList")
                    .commit();
        }

        if (noPermission() && localMusicIsEmpty() && shouldShowRequestPermissionRationale()) {
            scanMusic();
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
            SearchActivity.start(this, SearchActivity.Type.MUSIC_LIST, MusicStore.MUSIC_LIST_LOCAL_MUSIC);
        } else if (id == R.id.btnSort) {
            mMusicListFragment.showSortDialog();
        } else if (id == R.id.btnScan) {
            scanMusic();
        }
    }

    private boolean noPermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_DENIED;
    }

    private boolean localMusicIsEmpty() {
        return MusicStore.getInstance().getLocalMusicList().getSize() < 1;
    }

    private boolean shouldShowRequestPermissionRationale() {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void scanMusic() {
        MessageDialog messageDialog = new MessageDialog.Builder(this)
                .setMessage(R.string.message_scan_local_music)
                .setPositiveButtonClickListener((dialog, which) -> {
                    ScannerDialog scannerDialog = ScannerDialog.newInstance(localMusicIsEmpty());
                    scannerDialog.show(getSupportFragmentManager(), "scanMusic");
                })
                .build();

        messageDialog.show(getSupportFragmentManager(), "messageScanMusic");
    }
}