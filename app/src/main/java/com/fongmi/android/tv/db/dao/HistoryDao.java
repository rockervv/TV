package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.fongmi.android.tv.bean.History;

import java.util.List;

@Dao
public abstract class HistoryDao extends BaseDao<History> {

    @Query("SELECT * FROM History")
    public abstract List<History> findAll();

    //@Query("SELECT * FROM History WHERE cid = :cid ORDER BY createTime DESC")
    //@Query("SELECT * FROM History WHERE cid = :cid ORDER BY lastUpdated DESC")
    //@Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid ORDER BY createTime DESC")
    @Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid AND context = :context ORDER BY lastUpdated DESC")
    public abstract List<History> find(int cid, String context);

    @Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid AND context = :context AND createTime >= :createTime ORDER BY lastUpdated DESC")
    public abstract List<History> find(int cid, String context, long createTime);

    @Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid AND context = :context AND `key` = :key ORDER BY lastUpdated DESC")
    public abstract History find(int cid, String context, String key);

    @Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid AND context = :context AND vodName = :vodName ORDER BY lastUpdated DESC")
    public abstract List<History> findByName(int cid, String context, String vodName);

    @Query("UPDATE History SET deleted = 1, lastUpdated = :lastUpdated WHERE cid = :cid AND context = :context AND `key` = :key")
    public abstract void delete(int cid, String context, String key, long lastUpdated);

    @Query("DELETE FROM History WHERE cid = :cid AND context = :context AND `key` = :key")
    public abstract void delete(int cid, String context, String key);

    @Query("DELETE FROM History WHERE cid = :cid AND context = :context")
    public abstract void delete(int cid, String context);

    @Query("DELETE FROM History")
    public abstract void delete();

    @Query("SELECT * FROM History ORDER BY lastUpdated DESC")
    public abstract List<History> getAllForSync();

    @Query("SELECT * FROM History WHERE deleted = 0 ORDER BY lastUpdated DESC")
    public abstract List<History> getAll();
    @Transaction
    public void insertOrUpdateAll(List<History> histories) {
        for (History history : histories) {
            History existing = find(history.getCid(), history.getContext(), history.getKey());
            if (existing != null) {
                update(history);
            } else {
                insert(history);
            }
        }
    }
}
