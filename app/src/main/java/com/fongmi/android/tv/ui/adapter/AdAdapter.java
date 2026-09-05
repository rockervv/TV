package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Ad;
import com.fongmi.android.tv.databinding.AdapterAdBinding;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

public class AdAdapter extends RecyclerView.Adapter<AdAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Ad> mItems;

    public AdAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {
        void onDelete(Ad item);
    }

    public void addAll(List<Ad> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Ad item) {
        int index = mItems.indexOf(item);
        if (index != -1) {
            mItems.remove(index);
            notifyItemRemoved(index);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterAdBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Ad item = mItems.get(position);
        List<Long> offsets = item.getTimeOffsetList();
        String timeText = offsets.isEmpty() ? "未知" : Util.timeMs(offsets.get(0));
        if (offsets.size() > 1) timeText += " (+" + (offsets.size() - 1) + ")";
        holder.binding.name.setText(item.getSeriesName() + " - " + item.getAdName());
        holder.binding.time.setText("發生時間: " + timeText + " | 時長: " + (item.getDuration() / 1000) + "s");
        holder.binding.hits.setText("跳過次數: " + item.getHitCount());
        holder.binding.delete.setOnClickListener(v -> mListener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        AdapterAdBinding binding;

        ViewHolder(@NonNull AdapterAdBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
