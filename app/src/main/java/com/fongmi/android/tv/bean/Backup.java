package com.fongmi.android.tv.bean;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.annotations.SerializedName;
import com.orhanobut.logger.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Backup {

    @SerializedName("site")
    private List<Site> site;
    @SerializedName("live")
    private List<Live> live;
    @SerializedName("keep")
    private List<Keep> keep;
    @SerializedName("config")
    private List<Config> config;
    @SerializedName("history")
    private List<History> history;
    @SerializedName("track")
    private List<Track> track;
    @SerializedName("device")
    private List<Device> device;
    @SerializedName("download")
    private List<Download> download;
    @SerializedName("flagScore")
    private List<FlagScore> flagScore;
    @SerializedName("favorite")
    private List<Favorite> favorite;
    @SerializedName("prefers")
    private Map<String, ?> prefers;

    public static Backup create() {
        Backup backup = new Backup();
        backup.setPrefers(Prefers.getPrefers().getAll());
        backup.setSite(AppDatabase.get().getSiteDao().findAll());
        backup.setLive(AppDatabase.get().getLiveDao().findAll());
        backup.setKeep(AppDatabase.get().getKeepDao().findAll());
        backup.setConfig(AppDatabase.get().getConfigDao().findAll());
        backup.setHistory(AppDatabase.get().getHistoryDao().findAll());
        backup.setTrack(AppDatabase.get().getTrackDao().findAll());
        backup.setDevice(AppDatabase.get().getDeviceDao().findAll());
        backup.setDownload(AppDatabase.get().getDownloadDao().find());
        backup.setFlagScore(AppDatabase.get().getFlagScoreDao().findAll());
        backup.setFavorite(AppDatabase.get().getFavoriteDao().findAll());
        return backup;
    }

    public static Backup objectFrom(String json) {
        try {
            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();
            Backup backup = gson.fromJson(json, Backup.class);
            return backup == null ? new Backup() : backup;
        } catch (Exception e) {
            e.printStackTrace();
            return new Backup();
        }
    }

    public boolean isEmpty() {
        return getConfig().isEmpty() && getSite().isEmpty() && getLive().isEmpty() && getKeep().isEmpty() && getHistory().isEmpty() && getPrefers().isEmpty();
    }

    public void restore() {
        android.util.Log.d("Backup", "database restore START");
        AppDatabase.get().runInTransaction(() -> {
            AppDatabase.get().clearAllTables();
            AppDatabase.get().getSiteDao().insertOrUpdate(getSite());
            AppDatabase.get().getLiveDao().insertOrUpdate(getLive());
            AppDatabase.get().getKeepDao().insertOrUpdate(getKeep());
            AppDatabase.get().getConfigDao().insertOrUpdate(getConfig());
            AppDatabase.get().getHistoryDao().insertOrUpdate(getHistory());
            AppDatabase.get().getTrackDao().insertOrUpdate(getTrack());
            AppDatabase.get().getDeviceDao().insertOrUpdate(getDevice());
            AppDatabase.get().getDownloadDao().insertOrUpdate(getDownload());
            AppDatabase.get().getFlagScoreDao().insertOrUpdate(getFlagScore());
            AppDatabase.get().getFavoriteDao().insertOrUpdate(getFavorite());
        });
        android.util.Log.d("Backup", "database restore SUCCESS, updating prefers");
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        for (Map.Entry<String, ?> entry : getPrefers().entrySet()) {
            Object obj = entry.getValue();
            if (obj instanceof String) editor.putString(entry.getKey(), (String) obj);
            else if (obj instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) obj);
            else if (obj instanceof Float) editor.putFloat(entry.getKey(), (Float) obj);
            else if (obj instanceof Integer) editor.putInt(entry.getKey(), (Integer) obj);
            else if (obj instanceof Long) editor.putLong(entry.getKey(), (Long) obj);
            else if (obj instanceof Number) {
                Number num = (Number) obj;
                if (num.toString().contains(".")) editor.putFloat(entry.getKey(), num.floatValue());
                else {
                    long val = num.longValue();
                    if (val <= Integer.MAX_VALUE && val >= Integer.MIN_VALUE) editor.putInt(entry.getKey(), (int) val);
                    else editor.putLong(entry.getKey(), val);
                }
            }
        }
        editor.apply();
        android.util.Log.d("Backup", "prefers update SUCCESS");
    }

    public List<Site> getSite() {
        return site == null ? Collections.emptyList() : site;
    }

    public void setSite(List<Site> site) {
        this.site = site;
    }

    public List<Live> getLive() {
        return live == null ? Collections.emptyList() : live;
    }

    public void setLive(List<Live> live) {
        this.live = live;
    }

    public List<Keep> getKeep() {
        return keep == null ? Collections.emptyList() : keep;
    }

    public void setKeep(List<Keep> keep) {
        this.keep = keep;
    }

    public List<Config> getConfig() {
        return config == null ? Collections.emptyList() : config;
    }

    public void setConfig(List<Config> config) {
        this.config = config;
    }

    public List<History> getHistory() {
        return history == null ? Collections.emptyList() : history;
    }

    public void setHistory(List<History> history) {
        this.history = history;
    }

    public List<Track> getTrack() {
        return track == null ? Collections.emptyList() : track;
    }

    public void setTrack(List<Track> track) {
        this.track = track;
    }

    public List<Device> getDevice() {
        return device == null ? Collections.emptyList() : device;
    }

    public void setDevice(List<Device> device) {
        this.device = device;
    }

    public List<Download> getDownload() {
        return download == null ? Collections.emptyList() : download;
    }

    public void setDownload(List<Download> download) {
        this.download = download;
    }

    public List<FlagScore> getFlagScore() {
        return flagScore == null ? Collections.emptyList() : flagScore;
    }

    public void setFlagScore(List<FlagScore> flagScore) {
        this.flagScore = flagScore;
    }

    public List<Favorite> getFavorite() {
        return favorite == null ? Collections.emptyList() : favorite;
    }

    public void setFavorite(List<Favorite> favorite) {
        this.favorite = favorite;
    }

    public Map<String, ?> getPrefers() {
        return prefers == null ? new HashMap<>() : prefers;
    }

    public void setPrefers(Map<String, ?> prefers) {
        this.prefers = prefers;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
