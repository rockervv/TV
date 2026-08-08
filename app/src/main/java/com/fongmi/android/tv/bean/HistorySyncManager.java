package com.fongmi.android.tv.bean;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HistorySyncManager {

    private static FtpManager ftpManager;
    private static FtpManager ftpManagerk;
    private static boolean useGist = false;
    private static boolean useFTP = false;

    private static final AtomicBoolean isHistorySyncing = new AtomicBoolean(false);
    private static final AtomicBoolean isKeepSyncing = new AtomicBoolean(false);

    public static void init(String uri, String username, String password, boolean isFTP) {
        String urikeep = uri + ".k.txt";
        ftpManager = new FtpManager(uri, username, password);
        ftpManagerk = new FtpManager(urikeep, username, password);
        useFTP = isFTP;
    }

    public static void initGist(String gurl, String gtoken, boolean isGist) {
        if (ftpManager != null) ftpManager.initGist(gurl, gtoken);
        if (ftpManagerk != null) ftpManagerk.initGist(gurl, gtoken);
        useGist = isGist;
    }

    public static void setup() {
        init(Setting.getFtpUri(), Setting.getFtpUsername(), Setting.getFtpPassword(), Setting.isUseFtp());
        initGist(Setting.getGistUrl(), Setting.getGistToken(), Setting.isUseGist());
        SyncAll();
    }

    private static void syncKeep() {
        if (!useFTP && !useGist) return;
        if (!isKeepSyncing.compareAndSet(false, true)) return;

        try {
            String jsonData = null;
            if (useFTP) {
                try {
                    jsonData = ftpManagerk.downloadJsonFileAsString(null);
                } catch (IOException e) {
                    Log.e("KeepSync", "FTP download failed", e);
                }
            }
            if (useGist && jsonData == null) {
                try {
                    jsonData = ftpManagerk.downloadGistJsonFileAsString("keep.json");
                } catch (Exception e) {
                    Log.e("KeepSync", "Gist download failed", e);
                }
            }

            List<Keep> sqliteItems = Keep.getAll();
            List<Keep> remoteItems = parseKeepList(jsonData);
            List<Keep> newMergedItems = Keep.syncLists(sqliteItems, remoteItems);

            Keep.sync(newMergedItems);
            RefreshEvent.keep();

            JSONObject newJson = new JSONObject();
            newJson.put("Uptime", System.currentTimeMillis());
            newJson.put("Keep", new JSONArray(App.gson().toJson(newMergedItems)));
            String updatedJsonString = newJson.toString();

            if (useFTP) {
                try {
                    ftpManagerk.uploadJsonString(updatedJsonString, null);
                } catch (IOException e) {
                    Log.e("KeepSync", "FTP upload failed", e);
                }
            }

            if (useGist) {
                try {
                    ftpManagerk.uploadGistJsonString(updatedJsonString, "keep.json");
                } catch (IOException e) {
                    Log.e("KeepSync", "Gist upload failed", e);
                }
            }
        } catch (Exception e) {
            Log.e("KeepSync", "Sync error", e);
        } finally {
            isKeepSyncing.set(false);
        }
    }

    private static void syncHistory() {
        if (!useFTP && !useGist) return;
        if (!isHistorySyncing.compareAndSet(false, true)) return;

        try {
            String jsonData = null;
            if (useFTP) {
                try {
                    jsonData = ftpManager.downloadJsonFileAsString(null);
                } catch (IOException e) {
                    Log.e("HistorySync", "FTP download failed", e);
                }
            }
            if (useGist && jsonData == null) {
                try {
                    jsonData = ftpManager.downloadGistJsonFileAsString("tv.json");
                } catch (Exception e) {
                    Log.e("HistorySync", "Gist download failed", e);
                }
            }

            List<History> remoteItems = parseHistoryList(jsonData);
            List<History> sqliteItems = AppDatabase.get().getHistoryDao().getAllForSync();
            List<History> newMergedItems = History.syncLists(sqliteItems, remoteItems);

            boolean enable_debug = false;
            if (enable_debug) {
                for (History item : newMergedItems) {
                    Log.d("HistorySync", "Synced item: " + item.getVodName() + ", Pic: " + item.getVodPic());
                }
            }

            History.sync(newMergedItems);
            RefreshEvent.history();

            JSONObject newJson = new JSONObject();
            newJson.put("Uptime", System.currentTimeMillis());
            newJson.put("History", new JSONArray(App.gson().toJson(newMergedItems)));
            String updatedJsonString = newJson.toString();

            if (useFTP) {
                try {
                    ftpManager.uploadJsonString(updatedJsonString, null);
                } catch (IOException e) {
                    Log.e("HistorySync", "FTP upload failed", e);
                }
            }
            if (useGist) {
                try {
                    ftpManager.uploadGistJsonString(updatedJsonString, "tv.json");
                } catch (IOException e) {
                    Log.e("HistorySync", "Gist upload failed", e);
                }
            }
        } catch (Exception e) {
            Log.e("HistorySync", "Sync error", e);
        } finally {
            isHistorySyncing.set(false);
        }
    }

    public static void SyncAll() {
        SyncHistory();
        SyncKeep();
    }

    public static void SyncHistory() {
        if (useFTP || useGist) {
            Task.execute(HistorySyncManager::syncHistory);
        }
    }

    public static void SyncKeep() {
        if (useFTP || useGist) {
            Task.execute(HistorySyncManager::syncKeep);
        }
    }

    public static String downloadCache(String name) {
        if (useFTP && ftpManager != null) {
            try {
                String path = ftpManager.getPath();
                if (path.contains("/")) {
                    path = path.substring(0, path.lastIndexOf("/"));
                }
                String remotePath = (path.isEmpty() ? "" : path + "/") + "category/" + name;
                return ftpManager.downloadJsonFileAsString(remotePath);
            } catch (IOException ignored) {
            }
        }
        if (useGist && ftpManager != null) {
            try {
                return ftpManager.downloadGistJsonFileAsString(name);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void uploadCache(String name, String content) {
        if (useFTP && ftpManager != null) {
            Task.execute(() -> {
                try {
                    String path = ftpManager.getPath();
                    if (path.contains("/")) {
                        path = path.substring(0, path.lastIndexOf("/"));
                    }
                    String remotePath = (path.isEmpty() ? "" : path + "/") + "category/" + name;
                    ftpManager.uploadJsonString(content, remotePath);
                } catch (IOException ignored) {
                }
            });
        }
        if (useGist) {
            Task.execute(() -> {
                try {
                    ftpManager.uploadGistJsonString(content, name);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static List<Keep> parseKeepList(String jsonString) {
        if (jsonString == null) return new ArrayList<>();
        try {
            JsonObject jsonObject = App.gson().fromJson(jsonString, JsonObject.class);
            if (jsonObject.has("Keep")) {
                Type listType = new TypeToken<List<Keep>>() {}.getType();
                return App.gson().fromJson(jsonObject.getAsJsonArray("Keep"), listType);
            }
        } catch (Exception e) {
            Log.e("Sync", "Parse Keep failed", e);
        }
        return new ArrayList<>();
    }

    private static List<History> parseHistoryList(String jsonString) {
        if (jsonString == null) return new ArrayList<>();
        try {
            JsonObject jsonObject = App.gson().fromJson(jsonString, JsonObject.class);
            if (jsonObject.has("History")) {
                Type listType = new TypeToken<List<History>>() {}.getType();
                return App.gson().fromJson(jsonObject.getAsJsonArray("History"), listType);
            }
        } catch (Exception e) {
            Log.e("Sync", "Parse History failed", e);
        }
        return new ArrayList<>();
    }
}
