package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.Favorite;

import java.util.List;

@Dao
public abstract class FavoriteDao extends BaseDao<Favorite> {

    @Query("SELECT * FROM Favorite ORDER BY `order` ASC")
    public abstract List<Favorite> findAll();

    @Query("SELECT * FROM Favorite WHERE `key` = :key")
    public abstract Favorite findByKey(String key);

    @Query("DELETE FROM Favorite WHERE `key` = :key")
    public abstract void delete(String key);

    @Query("DELETE FROM Favorite")
    public abstract void delete();
}
