package com.fongmi.android.tv.model;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.List;
import java.util.concurrent.Callable;

public class SiteViewModel extends ViewModel {

    protected final MutableLiveData<Result> result;
    protected final MutableLiveData<Result> player;
    protected final MutableLiveData<Result> search;
    protected final MutableLiveData<Result> action;
    protected final MutableLiveData<String> filter;
    private final ViewModelTaskRunner<TaskType> tasks;
    private final ViewModelSearchRunner searches;

    public SiteViewModel() {
        result = new MutableLiveData<>();
        player = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        filter = new MutableLiveData<>();
        tasks = new ViewModelTaskRunner<>(TaskType.class);
        searches = new ViewModelSearchRunner();
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getPlayer() {
        return player;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public LiveData<String> getFilter() {
        return filter;
    }

    public void setFilter(String typeId) {
        filter.setValue(typeId);
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        player.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        if (!VodConfig.get().isLoaded()) {
            android.util.Log.w("SiteViewModel", "homeContent [ABORTED]: VodConfig not loaded yet!");
            return;
        }
        Site site = VodConfig.get().getHome();
        Result cache = com.fongmi.android.tv.api.CacheManager.get(site);
        if (cache != null) {
            android.util.Log.d("SiteViewModel", "homeContent [CACHE]: Found file cached data for site: " + site.getName());
            result.postValue(cache.setTid(""));
        }
        execute(TaskType.RESULT, result, () -> SiteApi.homeContent(site, true));
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend, boolean refresh) {
        if (!VodConfig.get().isLoaded()) {
            android.util.Log.w("SiteViewModel", "categoryContent [ABORTED]: VodConfig not loaded yet!");
            return;
        }
        Site site = VodConfig.get().getSite(key);
        Result cache = com.fongmi.android.tv.api.CacheManager.get(site, tid, page);
        if (cache != null) {
            result.postValue(cache.setTid(tid));
        }
        
        // 如果是本地 Spider 且已有快取，則不執行背景更新，避免併發任務過多導致中斷
        if (cache != null && key.startsWith("loc_") && !refresh) return;

        android.util.Log.d("SiteViewModel", "categoryContent: tid=" + tid + " page=" + page);
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend, refresh));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id));
    }

    public void playerContent(String key, String flag, String id) {
        execute(TaskType.PLAYER, player, () -> SiteApi.playerContent(key, flag, id));
    }

    public void searchContent(Site site, String keyword, boolean quick) {
        searchContent(site, keyword, quick, "1");
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(TaskType.RESULT, result, SearchTask.create(site, keyword, quick, page));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        List<Site> sorted = new ArrayList<>(sites);
        Collections.sort(sorted, (o1, o2) -> Integer.compare(o2.getScore(), o1.getScore()));
        searches.start(sorted, site -> SearchTask.create(site, keyword, quick), result -> {
            if (result.getList().size() > 0) App.post(() -> search.setValue(result));
        });
    }

    protected void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        String monitorKey = "Task_" + type.name();
        Monitor.start(monitorKey);
        tasks.execute(type, Constant.TIMEOUT_VOD, callable, result -> {
            Monitor.end(monitorKey);
            liveData.postValue(result);
        }, error -> {
            Monitor.end(monitorKey);
            if (error instanceof ExtractException) liveData.postValue(Result.error(error.getMessage()));
            else liveData.postValue(Result.empty());
            Log.e ("SiteViewModel", "Error:", error);
        });
    }

    public void stopSearch() {
        searches.stop();
    }

    @Override
    protected void onCleared() {
        stopSearch();
        tasks.cancelAll();
    }

    protected record SearchTask(Site site, String keyword, boolean quick, String page) implements Callable<Result> {

        private static final String FIRST_PAGE = "1";

        protected static SearchTask create(Site site, String keyword, boolean quick) {
            return create(site, keyword, quick, FIRST_PAGE);
        }

        protected static SearchTask create(Site site, String keyword, boolean quick, String page) {
            return new SearchTask(site, Trans.z2p(keyword), quick, page);
        }

        @Override
        public Result call() throws Exception {
            try {
                if (quick && !site.isQuickSearch()) return Result.empty();
                return SiteApi.searchContent(site, keyword, quick, page);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected enum TaskType {RESULT, PLAYER, ACTION}
}
