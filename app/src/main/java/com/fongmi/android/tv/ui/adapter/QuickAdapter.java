package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterQuickBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class QuickAdapter extends RecyclerView.Adapter<QuickAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;
    private int width;

    public QuickAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
        if (!ResUtil.isPad()) {
            int space = ResUtil.dp2px(24) + ResUtil.dp2px(32);
            this.width = (ResUtil.getScreenWidth() - space) / 4;
        }
    }

    public interface OnClickListener {

        void onItemClick(Vod item);

        void onItemLongClick(Vod item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Vod> items) {
        int start = mItems.size();
        mItems.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    public Vod get(int position) {
        return mItems.get(position);
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public void removeBySiteKey(String siteKey) {
        for (int i = mItems.size() - 1; i >= 0; i--) {
            if (mItems.get(i).getSiteKey().equals(siteKey)) {
                mItems.remove(i);
                notifyItemRemoved(i);
            }
        }
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterQuickBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        if (width > 0) holder.binding.getRoot().getLayoutParams().width = width;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.remark.setText(item.getRemarks());
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            mListener.onItemLongClick(item);
            return true;
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterQuickBinding binding;

        public ViewHolder(@NonNull AdapterQuickBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
