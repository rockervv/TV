package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.DialogButtonsBinding;
import com.fongmi.android.tv.ui.adapter.ActionAdapter;
import com.fongmi.android.tv.utils.LiveUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ChannelActionDialog extends BaseAlertDialog implements ActionAdapter.OnClickListener {

    private DialogButtonsBinding binding;
    private ActionAdapter adapter;
    private Channel channel;
    private boolean virtual;

    public static ChannelActionDialog create() {
        return new ChannelActionDialog();
    }

    public ChannelActionDialog channel(Channel channel) {
        this.channel = channel;
        this.virtual = LiveConfig.get().getHome().getType().equals("virtual");
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogButtonsBinding.inflate(LayoutInflater.from(requireContext()));
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(binding.getRoot());
    }

    @Override
    protected void initView() {
        binding.recycler.setAdapter(adapter = new ActionAdapter(this));
        List<Integer> items = new ArrayList<>();
        if (virtual) {
            items.add(R.string.channel_edit);
            items.add(R.string.channel_move);
            items.add(R.string.channel_group);
            items.add(R.string.channel_epg);
            items.add(R.string.channel_delete);
        } else {
            items.add(R.string.keep);
            items.add(R.string.favorite_add_to);
        }
        adapter.addAll(items);
    }

    @Override
    public void onItemClick(int resId) {
        if (resId == R.string.favorite_add_to) {
            showGroupDialog();
        } else if (resId == R.string.keep) {
            toggleKeep();
        }
        dismiss();
    }

    private void toggleKeep() {
        boolean exist = Keep.exist(channel.getName());
        if (exist) delKeep();
        else addKeep();
    }

    private void addKeep() {
        Keep keep = new Keep();
        keep.setKey(channel.getName());
        keep.setType(1);
        keep.save();
        Notify.show(R.string.keep_add);
    }

    private void delKeep() {
        Keep.delete(channel.getName());
        Notify.show(R.string.keep_del);
    }

    private void showGroupDialog() {
        List<Group> groups = LiveUtil.getMyLive().getGroups();
        List<String> items = new ArrayList<>();
        for (Group g : groups) items.add(g.getName());
        items.add(ResUtil.getString(R.string.favorite_add)); // 借用作為 "新增分類"

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(channel.getName())
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == items.size() - 1) {
                        showNewGroupDialog();
                    } else {
                        LiveUtil.addChannel(items.get(which), channel);
                        Notify.show(R.string.keep_add);
                    }
                }).show();
    }

    private void showNewGroupDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorite_add)
                .setView(input)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    LiveUtil.addChannel(name, channel);
                    Notify.show(R.string.keep_add);
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }
}
