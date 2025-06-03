package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import java.util.Objects;

@Entity
public class Keep {

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("siteName")
    private String siteName;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("type")
    private int type;
    @SerializedName("cid")
    private int cid;

    private static long Uptime;

    public static List<Keep> arrayFrom(String str) {
        Type listType = new TypeToken<List<Keep>>() {}.getType();
        List<Keep> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        return getKey().split(AppDatabase.SYMBOL)[1];
    }

    public static Keep find(String key) {
        return find(VodConfig.getCid(), key);
    }

    public static Keep find(int cid, String key) {
        return AppDatabase.get().getKeepDao().find(cid, key);
    }

    public static boolean exist(String key) {
        return AppDatabase.get().getKeepDao().find(key) != null;
    }

    public static void deleteAll() {
        AppDatabase.get().getKeepDao().delete();
    }

    public static void delete(int cid) {
        AppDatabase.get().getKeepDao().delete(cid);
    }

    public static void delete(String key) {
        AppDatabase.get().getKeepDao().delete(key);
    }

    public static List<Keep> getAll() {
        return AppDatabase.get().getKeepDao().getAll();
    }

    public static List<Keep> getVod() {
        return AppDatabase.get().getKeepDao().getVod();
    }

    public static List<Keep> getLive() {
        return AppDatabase.get().getKeepDao().getLive();
    }

    public void save(int cid) {
        setCid(cid);
        AppDatabase.get().getKeepDao().insertOrUpdate(this);
    }

    public void save() {
        AppDatabase.get().getKeepDao().insert(this);
    }

    public Keep delete() {
        AppDatabase.get().getKeepDao().delete(getCid(), getKey());
        return this;
    }

    private static void startSync(List<Config> configs, List<Keep> targets) {
        for (Keep target : targets) {
            for (Config config : configs) {
                if (target.getCid() == config.getId()) {
                    target.save(Config.find(config).getId());
                }
            }
        }
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        App.execute(() -> {
            startSync(configs, targets);
            RefreshEvent.keep();
        });
    }

    private static void startSync(List<Keep> targets) {
        //int cid = VodConfig.getCid();
        //int cid = 1;
        //deleteAll();
        for (Keep target : targets) {
                target.save(target.getCid());
                //target.save();
        }
    }
    public static void sync(List<Keep> targets) {
        App.execute(() -> {
            startSync(targets);
            RefreshEvent.keep();
        });
    }

    public static long GetUptime() {return Uptime;}
    public static List<Keep> syncLists(List<Keep> list1, List<Keep> list2) {
        Map<String, Keep> mergedMap = new HashMap<>();
        // Process items from both lists
        for (List<Keep> list : Arrays.asList(list1, list2)) {
            for (Keep item : list) {
                String key = item.getKey();
                //if (!item.isDeleted() && item.lastUpdate.days - Datetime.days > xxx) { //TODO or noTodo: lastupdated is more xx days then remove from the mergedMap for hard delete
                if (item.getCreateTime() > Uptime) Uptime = item.getCreateTime();

                Keep existingItem = mergedMap.get(key);
                if (existingItem != null) {
                    if (item.getCreateTime() > existingItem.getCreateTime()) {
                        updateAllColumns(existingItem, item);
                    }
                } else {
                    mergedMap.put(key, item);
                }
            }
        }
        //insertOrUpdate(result);
        return new ArrayList<>(mergedMap.values());
    }
    public static void insertOrUpdate(List<Keep> items) {
        AppDatabase.get().getKeepDao().insertOrUpdate(items);
    }
    private static void updateAllColumns(Keep existingItem, Keep newItem) {
        existingItem.setVodPic(newItem.getVodPic());
        existingItem.setVodName(newItem.getVodName());
        existingItem.setSiteName(newItem.getSiteName());
        existingItem.setType(newItem.getType());
        existingItem.setCreateTime(newItem.getCreateTime());
        existingItem.setCid(newItem.getCid());
    }

}
