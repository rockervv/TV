package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.PlaybackViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String query = sanitize(keyword);
        state.setSearchKeyword(query);
        state.setAutoFallback(autoFallback);
        state.setSelectFirstSource(autoFallback);
        host.onSearchStarted(query);
        host.requestSearch(getSearchableSites(), query);
    }

    private String sanitize(String keyword) {
        if (keyword == null) return "";
        // 🛠️ 優化：如果是超長的 YouTube 標題，嘗試提取括號內的核心劇名
        if (keyword.length() > 15) {
            Matcher m = Pattern.compile("《([^》]+)》").matcher(keyword);
            if (m.find()) return m.group(1);
            // 🛡️ 備案：移除常見的廢話詞彙，並截斷
            keyword = keyword.replaceAll("一口氣看完|身手不凡|偵破失蹤案|討厭暴力|精通8國語言|精通8国语言", "");
            if (keyword.contains("！")) keyword = keyword.split("！")[0];
            if (keyword.length() > 15) keyword = keyword.substring(0, 15);
        }
        return keyword;
    }

    public void onSearchResult(Result result) {
        List<Vod> items = new ArrayList<>(result.getList());
        items.removeIf(this::mismatch);
        items.removeIf(item -> !isPass(VodConfig.get().getSite(item.getSiteKey())));
        state.addSources(items);
        if (viewModel != null) viewModel.setSources(state.getSources());
        if (state.isSelectFirstSource() && !state.getSources().isEmpty()) nextSource();
        host.onSearchResult();
    }

    private boolean fallbackToNextLineOrSource() {
        boolean isYouTube = isYouTube();
        android.util.Log.d("Fallback", "fallbackToNextLineOrSource() - changeable: " + host.isSiteChangeable() + " resume: " + host.isResume() + " isYouTube: " + isYouTube);
        
        // 🛠️ 核心修正：針對 YouTube (SmartTube) 來源，如果站點設定不可變更 (isSiteChangeable = false)，則嚴禁任何形式的切換回退。
        // 這能防止因暫時性的 403 或解析錯誤導致播放器跳轉到完全無關的其他來源，干擾使用者體驗。
        if (isYouTube && !host.isSiteChangeable()) return false;
        
        if (!host.isSiteChangeable() && !host.isResume()) return false;
        if (fallbackToNextLine()) return true;
        return fallbackToNextSource(false);
    }

    private boolean isYouTube() {
        String key = host.getVodKey();
        Site site = VodConfig.get().getSite(key);
        if (site == null) site = Site.find(key);
        return key.toLowerCase().contains("youtube") || key.toLowerCase().contains("smarttube") || (site != null && (site.getApi().contains("SmartTube") || site.getName().toLowerCase().contains("youtube")));
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
        while (state.hasSources()) {
            Vod item = state.removeFirstSource();
            if (isPass(VodConfig.get().getSite(item.getSiteKey()))) {
                if (viewModel != null) viewModel.setSources(state.getSources());
                host.showSwitchSource(item);
                state.addFailedId(host.getVodId());
                state.setSelectFirstSource(false);
                controller.fallbackSource(item);
                return;
            }
        }
    }

    private List<Site> getSearchableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        return sites;
    }

    private boolean isPass(Site item) {
        if (item.isBlacklist()) return false;
        if (!item.isSearchable()) return false;
        if (state.isAutoFallback() && !item.isChangeable()) return false;
        return true;
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
