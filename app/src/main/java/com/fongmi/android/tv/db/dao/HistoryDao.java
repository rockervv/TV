package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.fongmi.android.tv.bean.History;

import java.util.List;

@Dao
public abstract class HistoryDao extends BaseDao<History> {

    //@Query("SELECT * FROM History WHERE cid = :cid ORDER BY createTime DESC")
    //@Query("SELECT * FROM History WHERE cid = :cid ORDER BY lastUpdated DESC")
    //@Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid ORDER BY createTime DESC")
    @Query("SELECT * FROM History WHERE deleted = 0 AND cid = :cid ORDER BY lastUpdated DESC")

    public abstract List<History> find(int cid);

    //@Query("SELECT * FROM History WHERE cid = :cid AND `key` = :key")
    @Query("SELECT * FROM History WHERE cid = :cid AND `key` = :key ORDER BY lastUpdated")

    public abstract History find(int cid, String key);

    //@Query("SELECT * FROM History WHERE cid = :cid AND vodName = :vodName")
    @Query("SELECT * FROM History WHERE cid = :cid AND vodName = :vodName ORDER BY lastUpdated")
    public abstract List<History> findByName(int cid, String vodName);

    //@Query("DELETE FROM History WHERE cid = :cid AND `key` = :key")
    //Soft Deleted to sync with other devices to set as delete
    @Query("UPDATE History SET deleted=1 WHERE cid = :cid AND `key` = :key")
    public abstract void delete(int cid, String key);

    //hard Deleted from database, TODO or NotTodo: delete rows which has deleted flag on for more than xx days
    @Query("DELETE FROM History WHERE cid = :cid AND `key` = :key")
    public abstract void deleteHard(int cid, String key);

    @Query("DELETE FROM History WHERE cid = :cid")
    public abstract void delete(int cid);

    @Query("DELETE FROM History")
    public abstract void delete();

    //@Query("SELECT * FROM History ORDER BY createTime DESC")
    @Query("SELECT * FROM History ORDER BY lastUpdated DESC")
    public abstract List<History> getAll();
    @Transaction
    public void insertOrUpdateAll(List<History> histories) {
        for (History history : histories) {
            History existing = find(history.getCid(), history.getKey());
            if (existing != null) {
                update(history);
            } else {
                insert(history);
            }
        }
    }
}
