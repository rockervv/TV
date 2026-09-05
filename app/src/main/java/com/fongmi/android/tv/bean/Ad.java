package com.fongmi.android.tv.bean;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;
import com.google.gson.annotations.SerializedName;

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
    @SerializedName("startTimeOffset")
    private long startTimeOffset;
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

    public long getStartTimeOffset() {
        return startTimeOffset;
    }

    public void setStartTimeOffset(long startTimeOffset) {
        this.startTimeOffset = startTimeOffset;
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
