package com.github.catvod.utils;

import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    public static JsonElement parse(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Throwable e) {
            return new JsonParser().parse(json);
        }
    }

    public static boolean valid(String text) {
        if (text == null) return false;
        text = text.trim();
        if (text.startsWith("<html>") || text.startsWith("<HTML>") || text.startsWith("<!DOCTYPE")) return false;
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                new JSONObject(text);
                return true;
            } catch (Exception e) {
                return false;
            }
        } else if (text.startsWith("[") && text.endsWith("]")) {
            try {
                new org.json.JSONArray(text);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public static boolean invalid(String text) {
        return !valid(text);
    }

    public static String safeString(JsonObject obj, String key) {
        try {
            return obj.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String> safeListString(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (!obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(safeString(obj, key));
        else for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsString());
        return result;
    }

    public static List<JsonElement> safeListElement(JsonObject obj, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (!obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(obj.get(key).getAsJsonObject());
        for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsJsonObject());
        return result;
    }

    public static JsonObject safeObject(String text) {
        try {
            JsonElement element = parse(text);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static com.google.gson.JsonArray safeArray(String text) {
        try {
            JsonElement element = parse(text);
            return element.isJsonArray() ? element.getAsJsonArray() : new com.google.gson.JsonArray();
        } catch (Exception e) {
            return new com.google.gson.JsonArray();
        }
    }

    public static JSONObject safeJSONObject(String text) {
        try {
            return valid(text) ? new JSONObject(text) : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static org.json.JSONArray safeJSONArray(String text) {
        try {
            return valid(text) ? new org.json.JSONArray(text) : new org.json.JSONArray();
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    public static JsonObject safeObject(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) element = parse(element.getAsJsonPrimitive().getAsString());
            return element.getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static Map<String, String> toMap(String json) {
        return TextUtils.isEmpty(json) ? null : toMap(parse(json));
    }

    public static Map<String, String> toMap(JsonElement element) {
        Map<String, String> map = new HashMap<>();
        JsonObject object = safeObject(element);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) map.put(entry.getKey(), safeString(object, entry.getKey()));
        return map;
    }

    public static JsonObject toObject(Map<String, String> map) {
        JsonObject object = new JsonObject();
        for (String key : map.keySet()) object.addProperty(key, map.get(key));
        return object;
    }
}
