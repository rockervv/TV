package com.fongmi.android.tv.bean;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

@Entity
public class History {

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodFlag")
    private String vodFlag;
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("revSort")
    private boolean revSort;
    @SerializedName("revPlay")
    private boolean revPlay;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("opening")
    private long opening;
    @SerializedName("ending")
    private long ending;
    @SerializedName("position")
    private long position;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private float speed;
    @SerializedName("player")
    private int player;
    @SerializedName("scale")
    private int scale;
    @SerializedName("cid")
    private int cid;
    @SerializedName("lastUpdated")
    private long lastUpdated = getCurrentUTCTime();
    @SerializedName("deleted")
    private boolean deleted = false;
    private static long Uptime = 0;

    public static History objectFrom(String str) {
        return App.gson().fromJson(str, History.class);
    }

    public static List<History> arrayFrom(String str) {
        Type listType = new TypeToken<List<History>>() {}.getType();
        List<History> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public History() {
        this.speed = 1;
        this.scale = -1;
        this.player = -1;
    }

    public static List<History> getAll() {
        return AppDatabase.get().getHistoryDao().getAll();
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodFlag() {
        return vodFlag;
    }

    public void setVodFlag(String vodFlag) {
        this.vodFlag = vodFlag;
    }

    public String getVodRemarks() {
        return vodRemarks == null ? "" : vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public boolean isRevSort() {
        return revSort;
    }

    public void setRevSort(boolean revSort) {
        this.revSort = revSort;
    }

    public boolean isRevPlay() {
        return revPlay;
    }

    public void setRevPlay(boolean revPlay) {
        this.revPlay = revPlay;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getOpening() {
        return opening;
    }

    public void setOpening(long opening) {
        this.opening = opening;
    }

    public long getEnding() {
        return ending;
    }

    public void setEnding(long ending) {
        this.ending = ending;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        this.player = player;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getSiteName() {
        return VodConfig.get().getSite(getSiteKey()).getName();
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        return getKey().split(AppDatabase.SYMBOL)[1];
    }

    public Flag getFlag() {
        return Flag.create(getVodFlag());
    }

    public Episode getEpisode() {
        return Episode.create(getVodRemarks(), getEpisodeUrl());
    }

    public int getSiteVisible() {
        return TextUtils.isEmpty(getSiteName()) ? View.GONE : View.VISIBLE;
    }

    public int getRevPlayText() {
        return isRevPlay() ? R.string.play_backward : R.string.play_forward;
    }

    public int getRevPlayHint() {
        return isRevPlay() ? R.string.play_backward_hint : R.string.play_forward_hint;
    }

    public boolean isNew() {
        return getCreateTime() == 0 && getPosition() == 0;
    }

    public static List<History> get() {
        return get(VodConfig.getCid());
    }

    public static List<History> get(int cid) {
        return AppDatabase.get().getHistoryDao().find(cid);
    }

    public static History find(String key) {
        return AppDatabase.get().getHistoryDao().find(VodConfig.getCid(), key);
    }

    public static void delete(int cid) {
        AppDatabase.get().getHistoryDao().delete(cid);
    }

    private void checkParam(History item) {
        if (getOpening() == 0) setOpening(item.getOpening());
        if (getEnding() == 0) setEnding(item.getEnding());
        if (getSpeed() == 1) setSpeed(item.getSpeed());
    }

    private void merge(List<History> items, boolean force) {
        for (History item : items) {
            if (getDuration() > 0 && item.getDuration() > 0 && Math.abs(getDuration() - item.getDuration()) > 10 * 60 * 1000) continue;
            if (!force && getKey().equals(item.getKey())) continue;
            checkParam(item);
            item.delete();
        }
    }

    public void update() {
        merge(find(), false);
        save();
    }

    public History update(int cid) {
        return update(cid, find());
    }

    public History update(int cid, List<History> items) {
        setCid(cid);
        merge(items, true);
        return save();
    }

    public History save() {
        AppDatabase.get().getHistoryDao().insertOrUpdate(this);
        if (this.getLastUpdated() > Uptime) Uptime = this.getLastUpdated();
        return this;
    }

    public History delete() {

        //hard delete
        if (getLastUpdated() == 0 || isDeleted()) {
        //if (getLastUpdated() == 0 ) {

            setDeleted(true);
            AppDatabase.get().getHistoryDao().insertOrUpdate(this);
            //AppDatabase.get().getHistoryDao().deleteHard(VodConfig.getCid(), getKey());
            //AppDatabase.get().getTrackDao().delete(getKey());

        } else if (getLastUpdated() > 0 &&
                ((System.currentTimeMillis() - getCreateTime()) / 1000 > 86400 * 90) &&
                ((System.currentTimeMillis() - getLastUpdated()) / 1000 > 86400 * 60)) {
            //AppDatabase.get().getHistoryDao().delete(VodConfig.getCid(), getKey());
            AppDatabase.get().getHistoryDao().deleteHard(VodConfig.getCid(), getKey());
            AppDatabase.get().getTrackDao().delete(getKey());

        } else {
            //soft delete
            setDeleted(true);
            //setLastUpdated(System.currentTimeMillis());
            AppDatabase.get().getHistoryDao().insertOrUpdate(this);
        }
        return this;
    }

    public List<History> find() {
        return AppDatabase.get().getHistoryDao().findByName(VodConfig.getCid(), getVodName());
    }

    public void findEpisode(List<Flag> flags) {
        if (flags.size() > 0) {
            setVodFlag(flags.get(0).getFlag());
            if (flags.get(0).getEpisodes().size() > 0) {
                setVodRemarks(flags.get(0).getEpisodes().get(0).getName());
            }
        }
        for (History item : find()) {
            if (getPosition() > 0) break;
            for (Flag flag : flags) {
                Episode episode = flag.find(item.getVodRemarks(), true);
                if (episode == null) continue;
                setVodFlag(flag.getFlag());
                setPosition(item.getPosition());
                setVodRemarks(episode.getName());
                checkParam(item);
                break;
            }
        }
    }

    private static void startSync(List<History> targets) {
        for (History target : targets) {
            List<History> items = target.find();
            if (items.isEmpty()) {
                target.update(VodConfig.getCid(), items);
                continue;
            }
            for (History item : items) {
                if (target.getCreateTime() > item.getCreateTime()) {
                    target.update(VodConfig.getCid(), items);
                    break;
                }
            }
        }
    }

    public static void sync(List<History> targets) {
        App.execute(() -> {
            startSync(targets);
            RefreshEvent.history();
        });
    }

    public static long getCurrentUTCTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        return calendar.getTimeInMillis();
    }

    public static long GetUptime() { return Uptime;}

    public static List<History> syncLists(List<History> localList, List<History> remoteList) {
        Map<String, History> mergedMap = new HashMap<>();
        long now = getCurrentUTCTime();
        long ninetyDaysInMillis = 86400L * 1000 * 90;

        // 第一階段：先處理 localList，保留刪除標記與新資料
        for (History item : localList) {
            String key = item.getKey();
            long lastUpdated = item.getLastUpdated();

            // 標記已過期的資料
            if ((now - lastUpdated) > ninetyDaysInMillis) item.setDeleted (true);

            // 直接放進 mergedMap
            mergedMap.put(key, item); // 不論是否 deleted，因為 local 為主
        }

        // 第二階段：處理 remoteList，僅當 local 沒有此 key 時才放入
        for (History item : remoteList) {
            String key = item.getKey();
            long lastUpdated = item.getLastUpdated();

            // 跳過已過期或已刪除資料
            if (item.isDeleted()) continue;
            if ((now - lastUpdated) > ninetyDaysInMillis) continue;

            History localItem = mergedMap.get(key);
            if (localItem == null) {
                // local 沒有此資料，加入 remote
                mergedMap.put(key, item);
            } else if (!localItem.isDeleted()) {
                if (item.getLastUpdated() > localItem.getLastUpdated()
                        ||item.getPosition() > localItem.getPosition()) {
                    updateAllColumns(localItem, item);
                }
                //mergedMap.put(key, item);
            }

        }
        List<History> result = new ArrayList<>();
        for (History item : mergedMap.values()) {
            long lastUpdated = item.getLastUpdated();
            boolean expired = (now - lastUpdated) > ninetyDaysInMillis;
            if (!(item.isDeleted() && expired)) {
                result.add(item);
            }
        }
        return result;
        //return new ArrayList<>(mergedMap.values());
    }

    public static List<History> syncLists(int old, List<History> localList, List<History> remoteList) {
        Map<String, History> mergedMap = new HashMap<>();
        // Process items from both lists
        for (List<History> list : Arrays.asList(localList, remoteList)) {
            for (History item : list) {
                String key = item.getKey();
                //undelete all.
                //item.deleted = false;
                long lastupdated = item.getLastUpdated();
                if (lastupdated > Uptime) Uptime = lastupdated;
                //if (item.isDeleted() || lastupdated == 0) continue;
                if ((getCurrentUTCTime() - lastupdated)/1000 > 86400 * 30 * 3 ) item.setLastUpdated(0);

                //if (!item.isDeleted() && item.lastUpdate.days - Datetime.days > xxx) { //TODO or noTodo: lastupdated is more xx days then remove from the mergedMap for hard delete

                if (mergedMap.containsKey(key)) {
                    History existingItem = mergedMap.get(key);
                    //暫時先忽略isDeleted.
                    if (item.getLastUpdated() == 0)
                    {
                        item.setDeleted(true);
                        assert existingItem != null;
                        existingItem.setDeleted(true);
                        continue;
                    }
                    if ((item.isDeleted() || Objects.requireNonNull(existingItem).isDeleted())) {
                        item.setDeleted(true);
                        assert existingItem != null;
                        existingItem.setDeleted(true);
                    }
                    if (item.getLastUpdated() > existingItem.getLastUpdated()) {
                        updateAllColumns(existingItem, item);
                    } else if (item.getPosition() > existingItem.getPosition()) {
                        updateAllColumns(existingItem, item);
                    }
                } else {
                    mergedMap.put(key, item);
                }
//                } else {
//                    // If item is marked as deleted, remove it from the merged map
//                    mergedMap.remove(key);
//                }
            }

        }
        List<History> result = new ArrayList<>(mergedMap.values());
        //insertOrUpdate(result);
        return result;
    }
    public static void insertOrUpdate(List<History> items) {
        AppDatabase.get().getHistoryDao().insertOrUpdate(items);
    }
    private static void updateAllColumns(History existingItem, History newItem) {
        existingItem.setVodPic(newItem.getVodPic());
        existingItem.setVodName(newItem.getVodName());
        existingItem.setVodFlag(newItem.getVodFlag());
        existingItem.setVodRemarks(newItem.getVodRemarks());
        existingItem.setEpisodeUrl(newItem.getEpisodeUrl());
        existingItem.setRevSort(newItem.isRevSort());
        existingItem.setRevPlay(newItem.isRevPlay());
        existingItem.setCreateTime(newItem.getCreateTime());
        existingItem.setOpening(newItem.getOpening());
        existingItem.setEnding(newItem.getEnding());
        existingItem.setPosition(newItem.getPosition());
        existingItem.setDuration(newItem.getDuration());
        existingItem.setSpeed(newItem.getSpeed());
        existingItem.setPlayer(newItem.getPlayer());
        existingItem.setScale(newItem.getScale());
        existingItem.setCid(newItem.getCid());
        existingItem.setLastUpdated(newItem.getLastUpdated());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
