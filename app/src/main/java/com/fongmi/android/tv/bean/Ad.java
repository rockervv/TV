package com.fongmi.android.tv.bean;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Ad {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private Long id;
    @SerializedName("fingerprint")
    private String fingerprint;
    @SerializedName("sourceId")
    private String sourceId;
    @SerializedName("seriesName")
    private String seriesName;
    @SerializedName("adName")
    private String adName;
    @SerializedName("urlPatterns")
    private String urlPatterns;
    @SerializedName("segmentDurations")
    private String segmentDurations;
    @SerializedName("timeOffsets")
    private String timeOffsets;
    @SerializedName("tsCount")
    private int tsCount;
    @SerializedName("duration")
    private long duration;
    @SerializedName("hitCount")
    private int hitCount;
    @SerializedName("lastHitTime")
    private long lastHitTime;

    public Ad() {
    }

    public static List<Ad> get(String sourceId) {
        return AppDatabase.get().getAdDao().findBySourceId(sourceId);
    }

    public static List<Ad> getAll() {
        return AppDatabase.get().getAdDao().findAll();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getAdName() {
        return adName;
    }

    public void setAdName(String adName) {
        this.adName = adName;
    }

    public String getUrlPatterns() {
        return urlPatterns;
    }

    public void setUrlPatterns(String urlPatterns) {
        this.urlPatterns = urlPatterns;
    }

    public String getSegmentDurations() {
        return segmentDurations;
    }

    public void setSegmentDurations(String segmentDurations) {
        this.segmentDurations = segmentDurations;
    }

    public String getTimeOffsets() {
        return timeOffsets;
    }

    public void setTimeOffsets(String timeOffsets) {
        this.timeOffsets = timeOffsets;
    }

    public int getTsCount() {
        return tsCount;
    }

    public void setTsCount(int tsCount) {
        this.tsCount = tsCount;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public List<Long> getTimeOffsetList() {
        List<Long> items = new ArrayList<>();
        if (timeOffsets == null || timeOffsets.isEmpty()) return items;
        for (String s : timeOffsets.split(",")) try { items.add(Long.parseLong(s)); } catch (Exception ignored) {}
        return items;
    }

    public void addTimeOffset(long offset) {
        List<Long> list = getTimeOffsetList();
        if (!list.contains(offset)) {
            list.add(offset);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) sb.append(list.get(i)).append(i == list.size() - 1 ? "" : ",");
            this.timeOffsets = sb.toString();
        }
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    public long getLastHitTime() {
        return lastHitTime;
    }

    public void setLastHitTime(long lastHitTime) {
        this.lastHitTime = lastHitTime;
    }

    public void save() {
        AppDatabase.get().getAdDao().insertOrUpdate(this);
    }

    public void delete() {
        AppDatabase.get().getAdDao().delete(getId());
    }
}
