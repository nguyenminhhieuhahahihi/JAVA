package accepted.music.activity.history;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;

import java.util.Objects;

import accepted.music.R;
import accepted.music.activity.ListActivity;
import accepted.music.dialog.MessageDialog;
import accepted.music.service.AppPlayerService;
import accepted.music.store.HistoryEntity;
import accepted.music.util.MusicListUtil;
import accepted.music.util.PlayerUtil;
import accepted.player.lifecycle.PlayerViewModel;
import accepted.player.playlist.Playlist;

public class HistoryActivity extends ListActivity {
    private PlayerViewModel mPlayerViewModel;
    private HistoryViewModel mHistoryViewModel;
    private HistoryAdapter mHistoryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViewModel();
        initRecyclerView();
    }

    public void finishSelf(View view) {
        finish();
    }

    public void clearHistory(View view) {
        if (view.getId() != R.id.btnClearHistory) {
            return;
        }

        MessageDialog messageDialog = new MessageDialog.Builder(getApplicationContext())
                .setMessage(R.string.message_clear_history)
                .setPositiveTextColor(getResources().getColor(R.color.red_500))
                .setPositiveButtonClickListener((dialog, which) -> mHistoryViewModel.clearHistory())
                .build();

        messageDialog.show(getSupportFragmentManager(), "clearHistory");
    }

    private void initViewModel() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);

        mPlayerViewModel = viewModelProvider.get(PlayerViewModel.class);
        mHistoryViewModel = viewModelProvider.get(HistoryViewModel.class);

        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);
        setPlayerClient(mPlayerViewModel.getPlayerClient());
    }

    private void initRecyclerView() {
        RecyclerView rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        mHistoryAdapter = new HistoryAdapter(Objects.requireNonNull(mHistoryViewModel.getHistory().getValue()));
        rvHistory.setAdapter(mHistoryAdapter);

        mHistoryAdapter.setOnItemClickListener(new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClicked(int position, @NonNull HistoryEntity historyEntity) {
                playMusic(position);
            }

            @Override
            public void onRemoveClicked(int position, @NonNull HistoryEntity historyEntity) {
                removeHistory(historyEntity);
            }
        });

        mHistoryViewModel.getHistory()
                .observe(this, history -> mHistoryAdapter.setHistory(history));
    }

    private void playMusic(int position) {
        MessageDialog messageDialog = new MessageDialog.Builder(getApplicationContext())
                .setMessage(R.string.message_play_all_music)
                .setPositiveButtonClickListener((dialog, which) -> {
                    Playlist playlist = MusicListUtil.asPlaylist("", mHistoryViewModel.getAllHistoryMusic(), position);

                    mPlayerViewModel.setPlaylist(playlist, position, true);
                })
                .build();

        messageDialog.show(getSupportFragmentManager(), "playMusic");
    }

    private void removeHistory(HistoryEntity historyEntity) {
        MessageDialog messageDialog = new MessageDialog.Builder(getApplicationContext())
                .setMessage(R.string.message_remove_history)
                .setPositiveTextColor(getResources().getColor(R.color.red_500))
                .setPositiveButtonClickListener((dialog, which) -> mHistoryViewModel.removeHistory(historyEntity))
                .build();

        messageDialog.show(getSupportFragmentManager(), "removeHistory");
    }
}