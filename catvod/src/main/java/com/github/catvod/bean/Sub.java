package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;

public class Sub {
    @SerializedName("url")
    private String url;
    @SerializedName("name")
    private String name;
    @SerializedName("lang")
    private String lang;
    @SerializedName("format")
    private String format;
}
