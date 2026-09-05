package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Ad;
import com.fongmi.android.tv.databinding.DialogAdManagerBinding;
import com.fongmi.android.tv.ui.adapter.AdAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;

import java.util.List;

public final class AdManagerDialog extends BaseBottomSheetDialog implements AdAdapter.OnClickListener {

    private AdAdapter adapter;
    private DialogAdManagerBinding binding;
    private String sourceId;

    public static AdManagerDialog create() {
        return new AdManagerDialog();
    }

    public AdManagerDialog sourceId(String sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof AdManagerDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogAdManagerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter = new AdAdapter(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        loadAds();
    }

    private void loadAds() {
        App.execute(() -> {
            List<Ad> items = Ad.get(sourceId);
            App.post(() -> {
                adapter.addAll(items);
                binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onDelete(Ad item) {
        App.execute(() -> {
            item.delete();
            App.post(() -> {
                adapter.remove(item);
                if (adapter.getItemCount() == 0) binding.empty.setVisibility(View.VISIBLE);
            });
        });
    }
}
