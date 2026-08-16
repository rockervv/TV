package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Diffable;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

@Entity
public class Favorite implements Diffable<Favorite> {

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("order")
    private int order;

    public Favorite() {
    }

    public static Favorite create(History item) {
        Favorite favorite = new Favorite();
        favorite.setKey(item.getKey());
        favorite.setVodName(item.getVodName());
        favorite.setVodPic(item.getVodPic());
        favorite.setVodRemarks(item.getVodRemarks());
        favorite.setCreateTime(System.currentTimeMillis());
        return favorite;
    }

    public static Favorite create(Keep item) {
        Favorite favorite = new Favorite();
        favorite.setKey(item.getKey());
        favorite.setVodName(item.getVodName());
        favorite.setVodPic(item.getVodPic());
        History history = History.find(item.getKey());
        favorite.setVodRemarks(history != null ? history.getVodRemarks() : "");
        favorite.setCreateTime(System.currentTimeMillis());
        return favorite;
    }

    public static List<Favorite> get() {
        return AppDatabase.get().getFavoriteDao().findAll();
    }

    public static Favorite find(String key) {
        return AppDatabase.get().getFavoriteDao().findByKey(key);
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
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

    public String getVodRemarks() {
        return vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        return getKey().split(AppDatabase.SYMBOL)[1];
    }

    public void save() {
        AppDatabase.get().getFavoriteDao().insertOrUpdate(this);
    }

    public void delete() {
        AppDatabase.get().getFavoriteDao().delete(getKey());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Favorite it)) return false;
        return Objects.equals(getKey(), it.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

    @Override
    public boolean isSameItem(Favorite other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Favorite other) {
        return Objects.equals(getVodName(), other.getVodName()) && Objects.equals(getVodPic(), other.getVodPic()) && Objects.equals(getVodRemarks(), other.getVodRemarks());
    }
}
