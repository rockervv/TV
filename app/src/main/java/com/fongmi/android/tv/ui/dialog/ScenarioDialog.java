package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Scenario;
import com.fongmi.android.tv.databinding.DialogScenarioBinding;
import com.fongmi.android.tv.ui.adapter.ScenarioAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ScenarioDialog extends BaseAlertDialog implements ScenarioAdapter.OnClickListener {

    private DialogScenarioBinding binding;
    private Callback callback;

    public interface Callback {
        void setScenario(Scenario item);
    }

    public static ScenarioDialog create() {
        return new ScenarioDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogScenarioBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        this.callback = (Callback) getActivity();
        binding.recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recycler.setAdapter(new ScenarioAdapter(this));
    }

    @Override
    public void onItemClick(Scenario item) {
        callback.setScenario(item);
        dismiss();
    }
}
