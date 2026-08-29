package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterBackupBinding;
import com.fongmi.android.tv.utils.DescHelper;
import com.github.catvod.utils.Path;

import com.orhanobut.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private DescHelper descHelper;
    private List<String> mItems;

    public BackupAdapter(OnClickListener listener) {
        this.mListener = listener;
    }

    public void setDescHelper(DescHelper descHelper) {
        this.descHelper = descHelper;
    }

    public interface OnClickListener {

        void onTextClick(String item);

        void onDeleteClick(String item);
    }

    public BackupAdapter addAll() {
        mItems = new ArrayList<>();
        File tv = Path.tv();
        List<File> files = Path.list(tv);
        android.util.Log.d("Backup", "addAll path: " + tv.getAbsolutePath() + " size: " + files.size());
        for (File file : files) {
            if (file.getName().endsWith(".bk.gz") || file.getName().endsWith(".tv.backup") || file.getName().endsWith(".bk")) {
                mItems.add(file.getName());
            }
        }
        Collections.sort(mItems, Collections.reverseOrder());
        android.util.Log.d("Backup", "addAll items found: " + mItems.size());
        return this;
    }

    public int remove(String item) {
        File file = new File(Path.tv(), item);
        if (file.exists()) file.delete();
        mItems.remove(item);
        notifyDataSetChanged();
        return getItemCount();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterBackupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mItems.get(position);
        holder.binding.text.setText(item.replace(".bk.gz", "").replace(".tv.backup", "").replace(".bk", ""));
        holder.binding.text.setOnClickListener(v -> mListener.onTextClick(item));
        holder.binding.delete.setOnClickListener(v -> mListener.onDeleteClick(item));
        holder.binding.text.setOnFocusChangeListener((v, hasFocus) -> onFocusChange(hasFocus, R.string.desc_history_text));
        holder.binding.delete.setOnFocusChangeListener((v, hasFocus) -> onFocusChange(hasFocus, R.string.desc_history_delete));
    }

    private void onFocusChange(boolean hasFocus, int resId) {
        if (descHelper == null) return;
        if (hasFocus) descHelper.show(resId);
        else descHelper.hide();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterBackupBinding binding;

        public ViewHolder(@NonNull AdapterBackupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
