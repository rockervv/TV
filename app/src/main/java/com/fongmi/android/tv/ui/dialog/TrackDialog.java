package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.DialogTrackBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.track.TrackNameProvider;
import com.fongmi.android.tv.ui.adapter.TrackAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TrackDialog extends BaseDialog implements TrackAdapter.OnClickListener {

    private final TrackNameProvider provider;
    private final TrackAdapter adapter;
    private DialogTrackBinding binding;
    private Listener listener;
    private ChooserListener cListener;
    private PlayerManager player;
    private boolean vod;
    private int type;

    public static TrackDialog create() {
        return new TrackDialog();
    }

    public TrackDialog() {
        this.adapter = new TrackAdapter(this);
        this.provider = new TrackNameProvider();
    }

    public TrackDialog chooser(ChooserListener listener) {
        this.cListener = listener;
        return this;
    }

    public TrackDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public TrackDialog vod(boolean vod) {
        this.vod = vod;
        return this;
    }

    public TrackDialog type(int type) {
        this.type = type;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogTrackBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.addAll(getTrack()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.choose.setVisibility(type == C.TRACK_TYPE_TEXT && player.isExo() && vod ? View.VISIBLE : View.GONE);
        binding.subtitle.setVisibility(type == C.TRACK_TYPE_TEXT ? View.VISIBLE : View.GONE);
        binding.title.setText(ResUtil.getStringArray(R.array.select_track)[type - 1]);
    }

    @Override
    protected void initEvent() {
        binding.choose.setOnClickListener(this::showChooser);
        binding.subtitle.setOnClickListener(this::onSubtitle);
    }

    private void onSubtitle(View view) {
        listener.onSubtitleClick();
        dismiss();
    }

    private void showChooser(View view) {
        if (cListener != null) cListener.showChooser(this);
        else FileChooser.from(this).show(new String[]{MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_TTML, "text/*", "application/octet-stream"});
        player.pause();
    }

    private List<Track> getTrack() {
        List<Track> items = new ArrayList<>();
        if (player.isExo()) addExoTrack(items);
        return items;
    }

    private void addExoTrack(List<Track> items) {
        List<Tracks.Group> groups = player.getCurrentTracks().getGroups();
        for (int i = 0; i < groups.size(); i++) {
            Tracks.Group trackGroup = groups.get(i);
            if (trackGroup.getType() != type) continue;
            for (int j = 0; j < trackGroup.length; j++) {
                Track item = new Track(type, provider.getTrackName(trackGroup.getTrackFormat(j)));
                item.setAdaptive(trackGroup.isAdaptiveSupported());
                item.setSelected(trackGroup.isTrackSelected(j));
                item.setPlayer(player.getEngine());
                item.setGroup(i);
                item.setTrack(j);
                items.add(item);
            }
        }
    }

    @Override
    public void onItemClick(Track item) {
        if (listener != null) listener.onTrackClick(item);
        player.setTrack(Arrays.asList(item));
        if (item.isAdaptive()) return;
        dismiss();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE) return;
        player.setSub(Sub.from(FileChooser.getPathFromUri(getContext(), data.getData())));
        dismiss();
    }

    public interface Listener {

        void onTrackClick(Track item);

        void onSubtitleClick();
    }

    public interface ChooserListener {

        void showChooser(TrackDialog dialog);
    }
}