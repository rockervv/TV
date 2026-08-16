package com.fongmi.android.tv.ui.dialog;

import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Favorite;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.RemoteSyncManager;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.DialogFavoriteSwitchBinding;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.FavoriteAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.List;

public class FavoriteSwitchDialog extends BaseAlertDialog implements FavoriteAdapter.OnClickListener {

    private DialogFavoriteSwitchBinding binding;
    private FavoriteAdapter adapter;
    private History history;

    public static FavoriteSwitchDialog create() {
        return new FavoriteSwitchDialog();
    }

    public FavoriteSwitchDialog history(History history) {
        this.history = history;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        if (binding != null) return binding;
        return binding = DialogFavoriteSwitchBinding.inflate(LayoutInflater.from(requireContext()));
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter = new FavoriteAdapter(this));
    }

    private void updateData() {
        Task.execute(() -> {
            List<Favorite> favorites = Favorite.get();
            List<History> histories = adapter.getLevel() == FavoriteAdapter.LEVEL_HISTORY ? History.getLatestAll() : History.get();
            List<Keep> keeps = Keep.getVod();
            App.post(() -> {
                if (adapter == null) return;
                Log.d("FavoriteSwitch", "updateData: level=" + adapter.getLevel() + ", favs=" + favorites.size() + ", history=" + histories.size());
                adapter.build(history, favorites, histories, keeps);
            });
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        RemoteSyncManager.SyncFavorite();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            params.width = (int) (ResUtil.getScreenWidth() * (ResUtil.isPad() || ResUtil.isLand(requireContext()) ? 0.35f : 0.7f));
            window.setAttributes(params);
        }
        updateData();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        RemoteSyncManager.SyncFavorite();
    }

    @Override
    public void onItemClick(FavoriteAdapter.Item item) {
        Log.d("FavoriteSwitch", "onItemClick: type=" + item.type + ", title=" + item.title + ", level=" + adapter.getLevel());
        if (item.type == FavoriteAdapter.TYPE_BACK) {
            adapter.setLevel(FavoriteAdapter.LEVEL_MAIN);
            updateData();
        } else if (item.type == FavoriteAdapter.TYPE_ADD) {
            addFavorite(item.history);
        } else if (item.type == FavoriteAdapter.TYPE_ACTION) {
            adapter.checkUpdateAll();
        } else if (item.type == FavoriteAdapter.TYPE_HEADER) {
            if (ResUtil.getString(R.string.favorite_history).equals(item.title)) {
                adapter.setLevel(FavoriteAdapter.LEVEL_HISTORY);
                updateData();
            } else if (ResUtil.getString(R.string.favorite_keep).equals(item.title)) {
                adapter.setLevel(FavoriteAdapter.LEVEL_KEEP);
                updateData();
            }
        } else {
            if (adapter.getLevel() == FavoriteAdapter.LEVEL_MAIN) {
                jump(item);
            } else {
                showSubMenu(item);
            }
        }
    }

    private void jump(FavoriteAdapter.Item item) {
        VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getName(), item.getPic(), item.getRemarks(), false, true, true);
        dismiss();
    }

    private void showSubMenu(FavoriteAdapter.Item item) {
        String[] items = {ResUtil.getString(R.string.favorite_add_to), ResUtil.getString(R.string.play)};
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(item.getName())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        if (item.history != null) addFavorite(item.history);
                        else if (item.keep != null) addFavorite(item.keep);
                        adapter.setLevel(FavoriteAdapter.LEVEL_MAIN);
                        updateData();
                    } else {
                        jump(item);
                    }
                }).show();
    }

    @Override
    public void onItemLongClick(FavoriteAdapter.Item item) {
        if (item.type == FavoriteAdapter.TYPE_ITEM) {
            if (item.favorite != null) {
                showFavoriteMenu(item.favorite, item);
            } else if (item.history != null) {
                showAddMenu(item.history);
            } else if (item.keep != null) {
                showAddMenu(item.keep);
            }
        }
    }

    @Override
    public boolean onItemKey(FavoriteAdapter.Item item, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (item.favorite != null) {
                showFavoriteMenu(item.favorite, item);
                return true;
            } else if (item.history != null || item.keep != null) {
                showSubMenu(item);
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (adapter.getLevel() != FavoriteAdapter.LEVEL_MAIN) {
                adapter.setLevel(FavoriteAdapter.LEVEL_MAIN);
                updateData();
                return true;
            }
        }
        return false;
    }

    private void showFavoriteMenu(Favorite favorite, FavoriteAdapter.Item item) {
        String[] items = {ResUtil.getString(R.string.favorite_check), ResUtil.getString(R.string.favorite_move_up), ResUtil.getString(R.string.favorite_move_down), ResUtil.getString(R.string.favorite_delete)};
        new MaterialAlertDialogBuilder(requireActivity())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) adapter.checkUpdate(item);
                    else if (which == 1) moveFavorite(favorite, true);
                    else if (which == 2) moveFavorite(favorite, false);
                    else if (which == 3) deleteFavorite(favorite);
                }).show();
    }

    private void showAddMenu(History history) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(history.getVodName())
                .setItems(new String[]{ResUtil.getString(R.string.favorite_add_to)}, (dialog, which) -> addFavorite(history))
                .show();
    }

    private void showAddMenu(Keep keep) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(keep.getVodName())
                .setItems(new String[]{ResUtil.getString(R.string.favorite_add_to)}, (dialog, which) -> addFavorite(keep))
                .show();
    }

    private void addFavorite(History history) {
        List<Favorite> favorites = Favorite.get();
        if (favorites.size() >= 10) {
            Notify.show(R.string.favorite_full);
            return;
        }
        for (Favorite f : favorites) if (f.getKey().equals(history.getKey())) return;
        Favorite favorite = Favorite.create(history);
        favorite.setOrder(favorites.size());
        favorite.save();
        updateData();
    }

    private void addFavorite(Keep keep) {
        List<Favorite> favorites = Favorite.get();
        if (favorites.size() >= 10) {
            Notify.show(R.string.favorite_full);
            return;
        }
        for (Favorite f : favorites) if (f.getKey().equals(keep.getKey())) return;
        Favorite favorite = Favorite.create(keep);
        favorite.setOrder(favorites.size());
        favorite.save();
        updateData();
    }

    private void deleteFavorite(Favorite favorite) {
        favorite.delete();
        updateData();
    }

    private void moveFavorite(Favorite favorite, boolean up) {
        List<Favorite> favorites = Favorite.get();
        int index = favorites.indexOf(favorite);
        if (up && index > 0) {
            Collections.swap(favorites, index, index - 1);
        } else if (!up && index < favorites.size() - 1) {
            Collections.swap(favorites, index, index + 1);
        } else {
            return;
        }
        for (int i = 0; i < favorites.size(); i++) {
            favorites.get(i).setOrder(i);
            favorites.get(i).save();
        }
        updateData();
    }
}
