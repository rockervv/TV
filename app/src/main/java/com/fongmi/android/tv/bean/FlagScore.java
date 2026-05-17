package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;

import java.util.List;

@Entity(indices = @Index(value = {"siteKey", "flagName"}, unique = true))
public class FlagScore {

    @PrimaryKey(autoGenerate = true)
    private Integer id;
    private String siteKey;
    private String flagName;
    private int score;

    public static FlagScore create(String siteKey, String flagName) {
        FlagScore item = new FlagScore();
        item.setSiteKey(siteKey);
        item.setFlagName(flagName);
        item.setScore(0);
        return item;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public String getFlagName() {
        return flagName;
    }

    public void setFlagName(String flagName) {
        this.flagName = flagName;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public static FlagScore find(String siteKey, String flagName) {
        FlagScore item = AppDatabase.get().getFlagScoreDao().find(siteKey, flagName);
        return item == null ? create(siteKey, flagName) : item;
    }

    public void increment() {
        score++;
        save();
    }

    public void decrement() {
        score--;
        save();
    }

    public void save() {
        AppDatabase.get().getFlagScoreDao().insertOrUpdate(this);
    }
}
