package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.App;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Scenario {

    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("type")
    private int type;
    @SerializedName("style")
    private Style style;

    public static List<Scenario> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Scenario.class).getType();
        List<Scenario> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public int getType() {
        return type;
    }

    public Style getStyle() {
        return style == null ? Style.rect() : style;
    }
}
