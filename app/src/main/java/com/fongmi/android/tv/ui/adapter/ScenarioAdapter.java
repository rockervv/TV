package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Scenario;
import com.fongmi.android.tv.databinding.AdapterScenarioBinding;

import java.util.List;

public class ScenarioAdapter extends RecyclerView.Adapter<ScenarioAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Scenario> mItems;

    public ScenarioAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = VodConfig.get().getScenarios();
    }

    public interface OnClickListener {
        void onItemClick(Scenario item);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterScenarioBinding binding;

        public ViewHolder(@NonNull AdapterScenarioBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterScenarioBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Scenario item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setActivated(item.getId().equals(VodConfig.get().getContext()));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }
}
