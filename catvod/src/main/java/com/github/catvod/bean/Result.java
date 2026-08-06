package com.github.catvod.bean;

import com.github.catvod.utils.Json;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.*;

public class Result {

    @SerializedName("class")
    private List<Class> classes;
    @SerializedName("list")
    private List<Vod> list;
    @SerializedName("filters")
    private LinkedHashMap<String, List<Filter>> filters;
    @SerializedName("header")
    private String header;
    @SerializedName("format")
    private String format;
    @SerializedName("msg")
    private String msg;
    @SerializedName("danmaku")
    private String danmaku;
    @SerializedName("url")
    private Object url;
    @SerializedName("subs")
    private List<Sub> subs;
    @SerializedName("parse")
    private int parse;
    @SerializedName("jx")
    private int jx;
    @SerializedName("page")
    private Integer page;
    @SerializedName("pagecount")
    private Integer pagecount;
    @SerializedName("limit")
    private Integer limit;
    @SerializedName("total")
    private Integer total;

    public static Result get() {
        return new Result();
    }

    public static String string(List<Class> classes, List<Vod> list, JSONObject filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JsonElement filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Vod> list) {
        return Result.get().vod(list).string();
    }

    public static String error(String msg) {
        return Result.get().msg(msg).string();
    }

    public Result classes(List<Class> classes) {
        this.classes = classes;
        return this;
    }

    public Result vod(List<Vod> list) {
        this.list = list;
        return this;
    }

    public Result filters(JSONObject object) {
        if (object == null) return this;
        Type listType = new TypeToken<LinkedHashMap<String, List<Filter>>>() {
        }.getType();
        this.filters = new Gson().fromJson(object.toString(), listType);
        return this;
    }

    public Result filters(JsonElement element) {
        if (element == null) return this;
        Type listType = new TypeToken<LinkedHashMap<String, List<Filter>>>() {
        }.getType();
        this.filters = new Gson().fromJson(element.toString(), listType);
        return this;
    }

    public Result page(int page, int count, int limit, int total) {
        this.page = page > 0 ? page : Integer.MAX_VALUE;
        this.limit = limit > 0 ? limit : Integer.MAX_VALUE;
        this.total = total > 0 ? total : Integer.MAX_VALUE;
        this.pagecount = count > 0 ? count : Integer.MAX_VALUE;
        return this;
    }

    public Result msg(String msg) {
        this.msg = msg;
        return this;
    }

    public String string() {
        return toString();
    }

    @Override
    public String toString() {
        return new Gson().newBuilder().disableHtmlEscaping().create().toJson(this);
    }
}
