package accepted.music.activity.browser.artist;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import accepted.music.R;
import accepted.music.activity.ListActivity;
import accepted.music.activity.detail.artist.ArtistDetailActivity;
import accepted.music.service.AppPlayerService;
import accepted.music.util.PlayerUtil;
import accepted.player.lifecycle.PlayerViewModel;

public class ArtistBrowserActivity extends ListActivity {
    private RecyclerView rvArtistBrowser;
    private ArtistBrowserViewModel mViewModel;
    private PlayerViewModel mPlayerViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist_browser);

        ViewModelProvider provider = new ViewModelProvider(this);
        mViewModel = provider.get(ArtistBrowserViewModel.class);
        mPlayerViewModel = provider.get(PlayerViewModel.class);

        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);

        rvArtistBrowser = findViewById(R.id.rvArtistBrowser);
        initRecyclerView();
    }

    private void initRecyclerView() {
        rvArtistBrowser.setLayoutManager(new LinearLayoutManager(this));

        List<String> allArtist = mViewModel.getAllArtist().getValue();
        assert allArtist != null;

        ArtistBrowserAdapter adapter = new ArtistBrowserAdapter(allArtist);
        rvArtistBrowser.setAdapter(adapter);

        mViewModel.getAllArtist()
                .observe(this, adapter::setAllArtist);

        mPlayerViewModel.getPlayingMusicItem()
                .observe(this, musicItem -> {
                    if (musicItem == null) {
                        adapter.clearMark();
                        return;
                    }

                    List<String> artistList = mViewModel.getAllArtist().getValue();
                    adapter.setMarkPosition(artistList.indexOf(musicItem.getArtist()));
                });

        adapter.setOnItemClickListener((position, viewId, view, holder) ->
                navigateToArtistDetail(mViewModel.getArtist(position))
        );
    }

    public void finishSelf(View view) {
        finish();
    }

    public void navigateToArtistDetail(String artistName) {
        ArtistDetailActivity.start(this, artistName);
    }
}
