package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterNavBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Integer> mItems;

    public ActionAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {
        void onItemClick(int resId);
    }

    public void addAll(List<Integer> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterNavBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int resId = mItems.get(position);
        holder.binding.name.setText(ResUtil.getString(resId));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(resId));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterNavBinding binding;

        public ViewHolder(@NonNull AdapterNavBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
