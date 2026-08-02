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
import com.github.catvod.utils.Trans;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

public class SiteViewModel extends ViewModel {

    protected final MutableLiveData<Result> result;
    protected final MutableLiveData<Result> player;
    protected final MutableLiveData<Result> search;
    protected final MutableLiveData<Result> action;
    protected final MutableLiveData<Boolean> filter;
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

    public LiveData<Boolean> getFilter() {
        return filter;
    }

    public void setFilter(boolean open) {
        filter.setValue(open);
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
        execute(TaskType.RESULT, result, () -> SiteApi.homeContent(VodConfig.get().getHome()));
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        if (!VodConfig.get().isLoaded()) {
            android.util.Log.w("SiteViewModel", "categoryContent [ABORTED]: VodConfig not loaded yet!");
            return;
        }
        android.util.Log.d("SiteViewModel", "categoryContent: tid=" + tid + " page=" + page);
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
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
        searches.start(sites, site -> SearchTask.create(site, keyword, quick), result -> App.post(() -> search.setValue(result)));
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
