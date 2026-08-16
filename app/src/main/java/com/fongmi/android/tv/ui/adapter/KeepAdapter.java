package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Favorite;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeepAdapter extends RecyclerView.Adapter<KeepAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Keep> mItems;
    private final Set<String> mChecking;
    private int width, height;
    private boolean delete;

    public KeepAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.mChecking = new HashSet<>();
        this.mListener = listener;
        setLayoutSize();
    }

    private void setLayoutSize() {
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (Product.getColumn() - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = base / Product.getColumn();
        height = (int) (width / 0.75f);
    }

    public interface OnClickListener {

        void onItemClick(Keep item);

        void onItemDelete(Keep item);

        boolean onLongClick();
    }

    public void addAll(List<Keep> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
        checkUpdateAll();
    }

    public void checkUpdateAll() {
        for (Keep item : new ArrayList<>(mItems)) {
            checkUpdate(item);
        }
    }

    private void checkUpdate(Keep item) {
        if (mChecking.contains(item.getKey())) return;
        mChecking.add(item.getKey());
        Task.execute(() -> {
            Result result = SiteApi.detailContent(item.getSiteKey(), item.getVodId(), false);
            App.post(() -> {
                mChecking.remove(item.getKey());
                if (result.getVod() != null) {
                    Vod vod = result.getVod();
                    item.setVodPic(vod.getPic());
                    item.save();
                    Favorite favorite = Favorite.find(item.getKey());
                    if (favorite != null) {
                        favorite.setVodPic(vod.getPic());
                        favorite.save();
                    }
                    notifyItemChanged(mItems.indexOf(item));
                }
            });
        });
    }

    public void delete(Keep item) {
        int index = mItems.indexOf(item);
        if (index == -1) return;
        mItems.remove(index);
        notifyItemRemoved(index);
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, mItems.size());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.getRoot().getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Keep item = mItems.get(position);
        setFocusListener(holder.binding);
        setClickListener(holder.itemView, item);
        holder.binding.name.setText(item.getVodName());
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.site.setVisibility(View.VISIBLE);
        holder.binding.site.setText(item.getSiteName());
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.loadVod(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    private void setFocusListener(AdapterVodBinding binding) {
        binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> binding.name.setSelected(hasFocus));
    }

    private void setClickListener(View root, Keep item) {
        root.setOnLongClickListener(view -> mListener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) mListener.onItemDelete(item);
            else mListener.onItemClick(item);
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        public ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
