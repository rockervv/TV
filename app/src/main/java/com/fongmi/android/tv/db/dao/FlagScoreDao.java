package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.FlagScore;

import java.util.List;

@Dao
public abstract class FlagScoreDao extends BaseDao<FlagScore> {

    @Query("SELECT * FROM FlagScore WHERE siteKey = :siteKey AND flagName = :flagName")
    public abstract FlagScore find(String siteKey, String flagName);

    @Query("SELECT * FROM FlagScore WHERE siteKey = :siteKey")
    public abstract List<FlagScore> findBySite(String siteKey);
}
