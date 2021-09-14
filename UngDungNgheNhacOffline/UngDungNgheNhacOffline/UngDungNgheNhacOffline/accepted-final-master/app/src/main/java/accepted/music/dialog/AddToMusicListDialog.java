package accepted.music.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import pinyin.util.PinyinComparator;
import recyclerview.helper.ItemClickHelper;
import recyclerview.helper.SelectableHelper;
import accepted.music.R;
import accepted.music.store.Music;
import accepted.music.store.MusicList;
import accepted.music.store.MusicStore;
import accepted.music.util.InputValidator;

public class AddToMusicListDialog extends BottomDialog {
    private Music mMusic;
    private List<Music> mAllMusic;
    private boolean mManyMusicMode;

    private String mExcludeMusicList;
    private List<String> mAllContainsMusicListName;
    private Disposable mLoadNameDisposable;

    @Nullable
    private OnFinishedListener mOnFinishedListener;
    private boolean mFinished;


    public static AddToMusicListDialog newInstance(@NonNull Music music) {
        Preconditions.checkNotNull(music);

        AddToMusicListDialog dialog = new AddToMusicListDialog();
        dialog.setMusic(music);

        return dialog;
    }

    public static AddToMusicListDialog newInstance(@NonNull List<Music> allMusic, @NonNull String excludeMusicList) {
        Preconditions.checkNotNull(allMusic);
        Preconditions.checkNotNull(excludeMusicList);

        AddToMusicListDialog dialog = new AddToMusicListDialog();
        dialog.setAllMusic(allMusic);
        dialog.setExcludeMusicList(excludeMusicList);

        return dialog;
    }

    private void setMusic(Music music) {
        mManyMusicMode = false;
        mMusic = music;
    }

    private void setAllMusic(List<Music> allMusic) {
        mManyMusicMode = true;
        mAllMusic = new ArrayList<>(allMusic);
    }

    private void setExcludeMusicList(String name) {
        mExcludeMusicList = name;
    }

    public void setOnFinishedListener(@Nullable OnFinishedListener listener) {
        mOnFinishedListener = listener;
    }

    @Override
    protected void onInitDialog(AppCompatDialog dialog) {
        dialog.setContentView(R.layout.dialog_add_to_music_list);

        RecyclerView rvItems = dialog.findViewById(R.id.rvItems);
        Button btnNewMusicList = dialog.findViewById(R.id.btnNewMusicList);
        Button btnOK = dialog.findViewById(R.id.btnOK);

        assert rvItems != null;
        assert btnNewMusicList != null;
        assert btnOK != null;

        rvItems.setLayoutManager(new LinearLayoutManager(getContext()));

        List<String> allMusicListName = getAllCustomMusicListName();

        AllMusicListAdapter adapter = new AllMusicListAdapter(allMusicListName, mAllContainsMusicListName);
        rvItems.setAdapter(adapter);

        btnNewMusicList.setOnClickListener(v -> showCreateMusicListDialog());

        btnOK.setOnClickListener(view -> {
            mFinished = true;
            List<String> selectedNames = adapter.getAllSelectedMusicList();
            if (selectedNames.size() < 1) {
                dismiss();
                return;
            }

            if (mManyMusicMode) {
                addManyMusic(selectedNames);
            } else {
                addSingleMusic(selectedNames);
            }

            dismiss();
        });
    }

    @Override
    public void dismiss() {
        if (mFinished) {
            notifyFinished();
        }
        super.dismiss();
    }

    private void notifyFinished() {
        if (mOnFinishedListener != null) {
            mOnFinishedListener.onFinished();
        }
    }

    private void addManyMusic(List<String> selectedNames) {
        Single.create(emitter ->
                MusicStore.getInstance().addToAllMusicList(mAllMusic, selectedNames)
        ).subscribeOn(Schedulers.io())
                .subscribe();

        Toast.makeText(getContext(), R.string.toast_added_successfully, Toast.LENGTH_SHORT).show();
    }

    private void addSingleMusic(List<String> selectedNames) {
        Single.create(emitter ->
                MusicStore.getInstance().addToAllMusicList(mMusic, selectedNames)
        ).subscribeOn(Schedulers.io())
                .subscribe();

        Toast.makeText(getContext(), R.string.toast_added_successfully, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoadNameDisposable != null && !mLoadNameDisposable.isDisposed()) {
            mLoadNameDisposable.dispose();
        }
    }

    @Override
    protected boolean keepOnRestarted() {
        return false;
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        if (mManyMusicMode) {
            mAllContainsMusicListName = Collections.emptyList();
            super.show(manager, tag);
            return;
        }

        mLoadNameDisposable = loadAllContainsMusicListName()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(names -> {
                    mAllContainsMusicListName = names;
                    super.show(manager, tag);
                });
    }

    private List<String> getAllCustomMusicListName() {
        List<String> allMusicListName = new ArrayList<>(MusicStore.getInstance().getAllCustomMusicListName());
        if (mExcludeMusicList != null) {
            allMusicListName.remove(mExcludeMusicList);
        }
        Collections.sort(allMusicListName, new PinyinComparator());
        return allMusicListName;
    }

    private Single<List<String>> loadAllContainsMusicListName() {
        return Single.create(emitter -> {
            List<String> names = MusicStore.getInstance().getAllCustomMusicListName(mMusic);
            if (emitter.isDisposed()) {
                return;
            }
            emitter.onSuccess(names);
        });
    }

    private void showCreateMusicListDialog() {
        Context context = Objects.requireNonNull(getContext()).getApplicationContext();

        InputDialog dialog = new InputDialog.Builder(Objects.requireNonNull(getContext()))
                .setTitle(R.string.title_create_music_list)
                .setHint(R.string.hint_music_list_title)
                .setOnInputConfirmListener(new InputValidator(context), input -> {
                    assert input != null;
                    createMusicList(context, input);
                })
                .build();

        FragmentManager fm = getParentFragmentManager();

        mFinished = false;
        dismiss();

        dialog.show(fm, "createMusicList");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void createMusicList(Context context, String name) {
        Single.create(emitter -> {
            MusicList musicList = MusicStore.getInstance().createCustomMusicList(name);
            if (mManyMusicMode) {
                musicList.getMusicElements().addAll(mAllMusic);
            } else {
                musicList.getMusicElements().add(mMusic);
            }
            MusicStore.getInstance().updateMusicList(musicList);

            emitter.onSuccess(true);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    Toast.makeText(context, R.string.toast_added_successfully, Toast.LENGTH_SHORT).show();
                    notifyFinished();
                });
    }

    private static class AllMusicListAdapter extends RecyclerView.Adapter<AllMusicListAdapter.ViewHolder> {
        private static final int TYPE_EMPTY = 1;
        private static final int TYPE_ITEM = 2;

        private final List<String> mAllMusicListName;
        private final List<String> mAllContainsMusicListName;
        private final SelectableHelper mSelectableHelper;
        private final ItemClickHelper mItemClickHelper;

        AllMusicListAdapter(@NonNull List<String> allMusicListName, List<String> allContainsMusicListName) {
            Preconditions.checkNotNull(allMusicListName);

            mAllMusicListName = new ArrayList<>(allMusicListName);
            mAllContainsMusicListName = new ArrayList<>(allContainsMusicListName);

            mSelectableHelper = new SelectableHelper(this);
            mItemClickHelper = new ItemClickHelper();

            mSelectableHelper.setSelectMode(SelectableHelper.SelectMode.MULTIPLE);
            mItemClickHelper.setOnItemClickListener((position, viewId, view, holder) -> {
                if (ignore(position)) {
                    return;
                }

                mSelectableHelper.toggle(position);
            });
        }

        private boolean ignore(int position) {
            return mAllContainsMusicListName.contains(mAllMusicListName.get(position));
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            mSelectableHelper.attachToRecyclerView(recyclerView);
            mItemClickHelper.attachToRecyclerView(recyclerView);
        }

        @Override
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            mSelectableHelper.detach();
            mItemClickHelper.detach();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = R.layout.item_add_to_music_list;
            boolean empty = (viewType == TYPE_EMPTY);

            if (empty) {
                layoutId = R.layout.empty_add_to_music_list;
            }

            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(layoutId, parent, false);

            return new ViewHolder(itemView, empty);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (holder.empty) {
                return;
            }

            holder.tvItemTitle.setText(mAllMusicListName.get(position));

            if (ignore(position)) {
                holder.ivCheckBox.setImageResource(R.drawable.ic_checkbox_disabled);
                return;
            }

            mSelectableHelper.updateSelectState(holder, position);
            mItemClickHelper.bindClickListener(holder.itemView);
        }

        @Override
        public int getItemCount() {
            if (mAllMusicListName.isEmpty()) {
                return 1;
            }

            return mAllMusicListName.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (mAllMusicListName.isEmpty()) {
                return TYPE_EMPTY;
            }

            return TYPE_ITEM;
        }

        public List<String> getAllSelectedMusicList() {
            List<String> allSelectedMusicList = new ArrayList<>();

            for (Integer i : mSelectableHelper.getSelectedPositions()) {
                allSelectedMusicList.add(mAllMusicListName.get(i));
            }

            return allSelectedMusicList;
        }

        private static class ViewHolder extends RecyclerView.ViewHolder
                implements SelectableHelper.Selectable {
            final boolean empty;

            TextView tvItemTitle;
            ImageView ivCheckBox;

            public ViewHolder(@NonNull View itemView, boolean empty) {
                super(itemView);

                this.empty = empty;
                if (empty) {
                    return;
                }

                tvItemTitle = itemView.findViewById(R.id.tvItemTitle);
                ivCheckBox = itemView.findViewById(R.id.ivCheckBox);
            }

            @Override
            public void onSelected() {
                if (empty) {
                    return;
                }

                ivCheckBox.setImageResource(R.drawable.ic_checkbox_checked);
            }

            @Override
            public void onUnselected() {
                if (empty) {
                    return;
                }

                ivCheckBox.setImageResource(R.drawable.ic_checkbox_unchecked);
            }
        }
    }

    public interface OnFinishedListener {
        void onFinished();
    }
}
