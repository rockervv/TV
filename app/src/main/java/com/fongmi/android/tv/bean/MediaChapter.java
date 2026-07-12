package com.fongmi.android.tv.bean;

import com.google.gson.annotations.SerializedName;

public class MediaChapter {

    @SerializedName("title")
    private String title;

    @SerializedName("start")
    private long start;

    @SerializedName("end")
    private long end;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }
}
