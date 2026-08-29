package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.PlaybackViewModel;

import java.util.ArrayList;
import java.util.List;

public class VodFallbackPolicy {

    private final VodPlaybackController controller;
    private final VodPlaybackState state;
    private final VodPlaybackHost host;
    private PlaybackViewModel viewModel;

    public VodFallbackPolicy(VodPlaybackController controller, VodPlaybackState state, VodPlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    public void setViewModel(PlaybackViewModel viewModel) {
        this.viewModel = viewModel;
    }

    public boolean playbackError() {
        return fallbackToNextLineOrSource();
    }

    public boolean emptyFlag() {
        return fallbackToNextLineOrSource();
    }

    public boolean emptyDetail() {
        return fallbackToNextSource(false);
    }

    public void manualSwitchSource() {
        fallbackToNextSource(true);
    }

    public void search(String keyword, boolean autoFallback) {
        state.setSearchKeyword(keyword);
        state.setAutoFallback(autoFallback);
        state.setSelectFirstSource(autoFallback);
        host.onSearchStarted(keyword);
        host.requestSearch(getSearchableSites(), keyword);
    }

    public void onSearchResult(Result result) {
        List<Vod> items = new ArrayList<>(result.getList());
        items.removeIf(this::mismatch);
        state.setSources(items);
        if (viewModel != null) viewModel.setSources(state.getSources());
        if (state.isSelectFirstSource() && !items.isEmpty()) nextSource();
        if (items.isEmpty()) {
            host.onSearchResult();
            // 💡 搜尋無效時，也不要彈出「解析失敗」的提示
            if (state.isAutoFallback()) {
                host.resetPlaybackForError("");
                if (!host.isSiteChangeable() || host.isResume()) host.finishVod();
            }
            return;
        }
        host.onSearchResult();
    }

    private boolean fallbackToNextLineOrSource() {
        android.util.Log.d("Fallback", "fallbackToNextLineOrSource() - changeable: " + host.isSiteChangeable() + " resume: " + host.isResume());
        if (!host.isSiteChangeable() && !host.isResume()) return false;
        if (fallbackToNextLine()) return true;
        return fallbackToNextSource(false);
    }

    private boolean fallbackToNextLine() {
        int position = state.getFlagPosition() + 1;
        android.util.Log.d("Fallback", "fallbackToNextLine() - next pos: " + position + " total flags: " + state.getFlags().size());
        if (position >= state.getFlags().size()) return false;
        Flag flag = state.getFlags().get(position);
        host.showSwitchLine(flag);
        controller.selectFlag(flag);
        return true;
    }

    private boolean fallbackToNextSource(boolean force) {
        android.util.Log.d("Fallback", "fallbackToNextSource() - hasSources: " + state.hasSources() + " auto: " + state.isAutoFallback() + " force: " + force);
        if (!state.hasSources()) {
            Site site = Site.find(host.getVodKey());
            if (site != null) site.decrementScore();
            search(host.getVodName(), true);
            return true;
        } else if (state.isAutoFallback() || force) {
            nextSource();
            return true;
        }
        return false;
    }

    private void nextSource() {
        if (!state.hasSources()) return;
        Vod item = state.removeFirstSource();
        if (viewModel != null) viewModel.setSources(state.getSources());
        host.showSwitchSource(item);
        state.addFailedId(host.getVodId());
        state.setSelectFirstSource(false);
        controller.fallbackSource(item);
    }

    private List<Site> getSearchableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        return sites;
    }

    private boolean isPass(Site item) {
        if (state.isAutoFallback() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private boolean mismatch(Vod item) {
        if (host.getVodId().equals(item.getId())) return true;
        if (state.hasFailedId(item.getId())) return true;
        String name1 = item.getName().replace(" ", "").toLowerCase();
        String name2 = state.getSearchKeyword().replace(" ", "").toLowerCase();
        if (state.isAutoFallback()) return !name1.equals(name2);
        return !name1.contains(name2);
    }
}
