package com.github.catvod.utils;

import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    public static JsonElement parse(String json) {
        if (TextUtils.isEmpty(json)) throw new com.google.gson.JsonSyntaxException("JSON 內容為空");
        try {
            return JsonParser.parseString(json);
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new com.google.gson.JsonSyntaxException(getErrorMessage(json, e.getMessage()), e.getCause());
        } catch (Throwable e) {
            throw new com.google.gson.JsonSyntaxException("解析失敗: " + e.getMessage());
        }
    }

    private static String getErrorMessage(String json, String error) {
        try {
            if (error == null || !error.contains("at line ")) return error;
            String[] split = error.split("at line ");
            String infoPart = split[1];
            String[] info = infoPart.split(" ");
            int line = Integer.parseInt(info[0]);
            int col = Integer.parseInt(info[2]);
            String[] lines = json.split("\\n");
            if (line > lines.length) return error;
            
            String rawContent = lines[line - 1];
            String content = rawContent.trim();
            int offset = rawContent.length() - rawContent.replaceAll("^\\s+", "").length();
            int displayCol = col - offset;

            // 💡 限制長度避免 Toast 撐爆
            if (content.length() > 60) {
                int start = Math.max(0, displayCol - 30);
                int end = Math.min(content.length(), displayCol + 30);
                content = (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
                displayCol = start > 0 ? displayCol - start + 3 : displayCol;
            }
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.max(0, displayCol - 1); i++) sb.append(" ");
            sb.append("⬆️");
            
            String cleanError = error.substring(0, error.indexOf("at line")).trim();
            return "JSON 語法錯誤 (第 " + line + " 行):\n" + content + "\n" + sb + "\n" + cleanError;
        } catch (Throwable e) {
            return error;
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
                new JSONArray(text);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public static boolean isObj(String text) {
        try {
            if (TextUtils.isEmpty(text)) return false;
            new JSONObject(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isArray(String text) {
        try {
            if (TextUtils.isEmpty(text)) return false;
            new JSONArray(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEmpty(JsonObject obj, String key) {
        if (!obj.has(key)) return true;
        JsonElement element = obj.get(key);
        if (element.isJsonNull()) return true;
        if (element.isJsonArray()) return element.getAsJsonArray().isEmpty();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return element.getAsString().trim().isEmpty();
        return true;
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
        else for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsJsonObject());
        return result;
    }

    public static JsonObject safeObject(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) element = parse(element.getAsJsonPrimitive().getAsString());
            return element.getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static JSONObject safeJSONObject(String text) {
        try {
            return valid(text) ? new JSONObject(text) : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONArray safeJSONArray(String text) {
        try {
            return valid(text) ? new JSONArray(text) : new JSONArray();
        } catch (Exception e) {
            return new JSONArray();
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
}
