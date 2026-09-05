package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.Ad;

import java.util.List;

@Dao
public abstract class AdDao extends BaseDao<Ad> {

    @Query("SELECT * FROM Ad")
    public abstract List<Ad> findAll();

    @Query("SELECT * FROM Ad WHERE sourceId = :sourceId")
    public abstract List<Ad> findBySourceId(String sourceId);

    @Query("DELETE FROM Ad WHERE id = :id")
    public abstract void delete(Long id);

    @Query("DELETE FROM Ad")
    public abstract void delete();
}
