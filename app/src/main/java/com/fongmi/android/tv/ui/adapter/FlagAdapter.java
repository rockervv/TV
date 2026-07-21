package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.AdapterFlagBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlagAdapter extends RecyclerView.Adapter<FlagAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Flag> mItems;
    private int nextFocusDown;

    public FlagAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
        this.nextFocusDown = R.id.episode;
    }

    public interface OnClickListener {

        void onItemClick(Flag item);

        default void onItemLongClick(Flag item) {
        }

        default boolean onLongClick(Flag item) {
            return false;
        }
    }

    public void addAll(List<Flag> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int getPosition() {
        if (mItems.isEmpty()) return -1;
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected() || mItems.get(i).isActivated()) return i;
        return 0;
    }

    public Flag get(int position) {
        return position < 0 || position >= mItems.size() ? new Flag() : mItems.get(position);
    }

    public int indexOf(Flag item) {
        return mItems.indexOf(item);
    }

    public Flag getActivated() {
        return get(getPosition());
    }

    public void setActivated(Flag flag) {
        if (!mItems.contains(flag)) flag.setFlag(mItems.get(0).getFlag());
        for (Flag item : mItems) item.setActivated(flag);
        notifyItemRangeChanged(0, getItemCount());
    }

    public void toggle(Episode episode) {
        for (Flag item : mItems) item.toggle(item.isActivated(), episode);
    }

    public void reverse() {
        for (Flag item : mItems) Collections.reverse(item.getEpisodes());
    }

    public void setNextFocusDown(int nextFocusDown) {
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
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
        return new ViewHolder(AdapterFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Flag item = mItems.get(position);
        holder.binding.text.setText(item.getShow());
        holder.binding.text.setActivated(item.isActivated() || item.isSelected());
        holder.binding.text.setSelected(item.isSelected());
        if (nextFocusDown != 0) holder.binding.text.setNextFocusDownId(nextFocusDown);
        holder.binding.text.setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.text.setOnLongClickListener(v -> {
            mListener.onItemLongClick(item);
            return mListener.onLongClick(item);
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterFlagBinding binding;

        ViewHolder(@NonNull AdapterFlagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
