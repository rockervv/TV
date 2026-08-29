package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;
import com.fongmi.android.tv.utils.DescHelper;

import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final OnClickListener listener;
    private DescHelper descHelper;
    private List<Config> mItems;
    private boolean readOnly;

    public ConfigAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setDescHelper(DescHelper descHelper) {
        this.descHelper = descHelper;
    }

    public interface OnClickListener {

        void onTextClick(Config item);

        void onDeleteClick(Config item);
    }

    public ConfigAdapter readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public ConfigAdapter addAll(int type) {
        mItems = Config.getAll(type);
        if (!mItems.isEmpty() && !readOnly) mItems.remove(0);
        return this;
    }

    public int remove(Config item) {
        int position = mItems.indexOf(item);
        if (position == -1) return -1;
        item.delete();
        mItems.remove(position);
        notifyItemRemoved(position);
        return getItemCount();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = mItems.get(position);
        holder.binding.text.setText(item.getDesc());
        holder.binding.text.setOnClickListener(v -> listener.onTextClick(item));
        holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item));
        holder.binding.text.setOnFocusChangeListener((v, hasFocus) -> onFocusChange(hasFocus, R.string.desc_history_text));
        holder.binding.delete.setOnFocusChangeListener((v, hasFocus) -> onFocusChange(hasFocus, R.string.desc_history_delete));
    }

    private void onFocusChange(boolean hasFocus, int resId) {
        if (descHelper == null) return;
        if (hasFocus) descHelper.show(resId);
        else descHelper.hide();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterConfigBinding binding;

        public ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
