
package com.fongmi.android.tv.bean;

//import com.fongmi.android.tv.bean.History;

import android.util.Log;

import com.fongmi.android.tv.App;
//import com.fongmi.android.tv.api.config.VodConfig;
//import com.fongmi.android.tv.bean.History;
//import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.db.AppDatabase;
//import com.fongmi.android.tv.utils.Notify;
//import com.fongmi.android.tv.R;
//import com.fongmi.android.tv.Constant;


//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;

//import java.io.BufferedReader;
//import java.io.FileWriter;
import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

//import com.fongmi.android.tv.db.dao.HistoryDao;
//import com.fongmi.android.tv.ui.activity.HistoryActivity;
//import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.event.RefreshEvent;
//import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.TimeZone;
import java.util.Calendar;
import android.os.Handler;
import android.os.Looper;
//import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class HistorySyncManager {


    private static FtpManager ftpManager;
    private static FtpManager ftpManagerk;
    private static boolean useGist = false;
    private static boolean useFTP = false;

    private static long ServerHupdated = 0;
    private static long ServerKupdated = 0;

    private static class Loader {
        static volatile HistorySyncManager INSTANCE = new HistorySyncManager();
    }

    private static HistorySyncManager get() {
        return HistorySyncManager.Loader.INSTANCE;
    }

    //public HistorySyncManager(FtpManager ftpManager) {
    //    this.ftpManager = ftpManager;
    //}

//    public HistorySyncManager() {
//        ftpManager = new FtpManager("192.168.1.1", 21, "hoanayang", "ilovebob123", false);
//    }

    public static void init (String uri, String username, String password) {
        init (uri, username, password, false);
    }

    public static void init (String uri, String username, String password, boolean isFTP) {
        String urikeep = uri + ".k.txt";
        ftpManager = new FtpManager(uri, username, password);
        ftpManagerk = new FtpManager(urikeep, username, password);
        useFTP = isFTP;
        //useGist = isGist;
    }

    public static void initGist (String gurl, String gtoken) {
        initGist (gurl, gtoken, false);
    }

    public static void initGist (String gurl, String gtoken, boolean isGist) {
        ftpManager.initGist(gurl, gtoken);
        ftpManagerk.initGist(gurl, gtoken);
        useGist = isGist;
    }

    private static void syncKeep() {
        //String jsonData;
        //Gson gson;
        if (!useFTP && !useGist) {
            return;
        }

        String jsonData = null;
        if (useFTP) {
            try {
                jsonData = ftpManagerk.downloadJsonFileAsString(null);///USB2T/(Documents)/(TVBox)/TV2/TV.json");
            } catch (IOException e) {
                Log.e("Keep", "FTP download", e);
            }
        }
        if (useGist) {
            //String jsontmp = ftpManagerk.downloadGistJsonFileAsString("keep.json");///USB2T/(Documents)/(TVBox)/TV2/TV.json");
            //if (jsonData == null) jsonData = jsontmp;
            jsonData = ftpManagerk.downloadGistJsonFileAsString("keep.json");
        }

        //JsonObject jsonObject;
        //if (jsonData != null) {
        //    jsonObject = App.gson().fromJson(jsonData, JsonObject.class);
        //}
        //if (jsonObject != null) {
        //    jsonObject = jsonObject.getAsJsonObject("keep")
        //}

        //if (jsonObject == null){
        //    jsonObject = new JsonObject()();
        //}
       //JsonObject jsonObject = jsonData==null? new JsonObject(): App.gson().fromJson(jsonData, JsonObject.class).getAsJsonObject("Keep");
        //List<Keep> sqliteItems = AppDatabase.get().getKeepDao().getAll();
        List<Keep> sqliteItems = Keep.getAll();
        //JsonObject vodobj = jsonObject.getAsJsonObject("")
        //getJSONObject("videoDetails");


        //JsonObject jsonObject = jsonData==null? new JsonObject(): gson.fromJson(jsonData, JsonObject.class);
        //List<Keep> ftpItems = jsonData==null? new ArrayList<>(): get().parseKeepList(jsonData);
            //List<Keep> ftpItems = Keep.arrayFrom(jsonData);

            //JsonObject jsonObject = jsonData==null? new JsonObject(): App.gson().fromJson(jsonData, JsonObject.class);
            //List<Keep> ftpItems = jsonData==null? new ArrayList<>(): parseKeepList(jsonData);
            //List<Keep> sqliteItems =AppDatabase.get().getHistoryDao().getAll();
            //List<Keep> vod = Keep.getVod();
            //List<Keep> live = Keep.getLive();
        //if (ftpItems == null) ftpItems = new ArrayList<>();
        //JSONObject jsonObject = null;
        List<Keep> ftpItems = null;

        try {
            //jsonObject = new JSONObject(jsonData);
            ftpItems = get().parseKeepList(jsonData);
        } catch (Exception e) {
            Log.e ("GIST", "Error fetch/parse Keep gist json: " + e.toString());
            //jsonObject = new JSONObject ();
            ftpItems = new ArrayList<>();
        }

        if (ftpItems == null) {
            ftpItems = new ArrayList<>();
        }

        List<Keep> newMergedItems = Keep.syncLists(sqliteItems, ftpItems);
            //App.gson().toJson(Keep.getVod()));
            //App.gson().toJson(Config.findUrls()));



            //AppDatabase.get().runInTransaction(new Runnable() {
            //    @Override
            //    public void run() {
            //        AppDatabase.get().getHistoryDao().insertOrUpdate(newMergedItems);
            //        //TODO or NotToDo: delete hard, if lastupdated is more than xx days old, delete it from the database
            //        //AppDatabase.get().getHistoryDao().deleteHard();
            //    }
            //});
        //if (ServerKupdated > Keep.GetUptime()) {
            Keep.sync(newMergedItems);
        //    return;
        //}


        JSONObject newJson = new JSONObject();

        try {
            newJson.put ("Uptime", System.currentTimeMillis());
            newJson.put ("Keep", new JSONArray (App.gson().toJson(newMergedItems)));

        } catch (Exception e) {
            Log.e ("Gist", "Error finalize Json");
        }
        String updatedJsonString = newJson.toString();



        //jsonObject.add("Keep", App.gson().toJsonTree(newMergedItems));
        //String updatedJsonString = App.gson().toJson(jsonObject);

        if (useFTP) {
            try {
                ftpManagerk.uploadJsonString(updatedJsonString, null);
            } catch (IOException e) {
                Log.e("Keep", "FTP upload", e);

            }
        }

        if (useGist) {
            try {
                ftpManagerk.uploadGistJsonString(updatedJsonString, "keep.json");
            } catch (IOException e) {
                Log.e("Keep", "Gist upload", e);
            }
            //RefreshEvent.keep();

            //syncKeep();
        }

    }

    private static void syncHistory() {

        if (!useFTP && !useGist) {
            return;
        }

        String jsonData = null;
        //Gson gson = new Gson();

        if (useFTP) {
            try {
                jsonData = ftpManager.downloadJsonFileAsString(null);///USB2T/(Documents)/(TVBox)/TV2/TV.json");
            } catch (IOException e) {
                Log.e("History", "FTP download", e);
            }
        }
        if (useGist) {
            //String jsontmp = ftpManager.downloadGistJsonFileAsString("tv.json");///USB2T/(Documents)/(TVBox)/TV2/TV.json");
            //if (jsonData == null) jsonData = jsontmp;
            try {
                jsonData = ftpManager.downloadGistJsonFileAsString("tv.json");
            } catch (Exception e) {
                Log.d ("SyncHistGist", "tv.json error:", e);
            }
        }
        //Original version:
        //JsonObject jsonObject = jsonData==null? new JsonObject(): gson.fromJson(jsonData, JsonObject.class);
        //List<History> ftpItems = jsonData==null? new ArrayList<>(): get().parseHistoryList (jsonData);
        //if (ftpItems == null) ftpItems = new ArrayList<>();


        //JSONObject jsonObject = null;
        List<History> remoteItems = null;

        try {
            //jsonObject = new JSONObject(jsonData);
            remoteItems = get().parseHistoryList(jsonData);
        } catch (Exception e) {
            Log.e ("SyncHistory", "Error fetch/parse history gist :" + e);
            //jsonObject = new JSONObject ();
            //remoteItems = new ArrayList<>();
        }
        if (remoteItems == null) {
            remoteItems = new ArrayList<>();
        }
        List<History> sqliteItems =AppDatabase.get().getHistoryDao().getAll();
        List<History> newMergedItems = History.syncLists(sqliteItems, remoteItems);
        Log.d("SyncHistory", "SQL: " + sqliteItems.size() + " merge to remot: " + remoteItems.size());
            //AppDatabase.get().runInTransaction(new Runnable() {
            //    @Override
            //    public void run() {
            //        AppDatabase.get().getHistoryDao().insertOrUpdate(newMergedItems);
            //        //TODO or NotToDo: delete hard, if lastupdated is more than xx days old, delete it from the database
            //        //AppDatabase.get().getHistoryDao().deleteHard();
            //    }
            //});
        //if (ServerHupdated > History.GetUptime()) {
            Log.d ("SyncHistory", "Result : " + newMergedItems.size() + " at " + ServerHupdated +  " vs " + History.GetUptime());
        //}
        History.sync(newMergedItems);
            //return;
        //}

        //original version
        //jsonObject.add("History", gson.toJsonTree(newMergedItems));
        //String updatedJsonString = gson.toJson(jsonObject);

        JSONObject newJson = new JSONObject();

        try {
            //newJson.put ("Uptime", History.GetUptime());
            newJson.put ("Uptime", System.currentTimeMillis());
            newJson.put ("History", new JSONArray (App.gson().toJson(newMergedItems)));

        } catch (Exception e) {
            Log.e ("SyncHistory", "Error finalize Json");
        }
        String updatedJsonString = newJson.toString();

            //ftpManager.uploadJsonString(updatedJsonString, "/USB2T/(Documents)/(TVBox)/TV2/TV.json");
        if (useFTP) {
            try {
                ftpManager.uploadJsonString(updatedJsonString, null);
            } catch (IOException e) {
                //e.printStackTrace();
                Log.e("SyncHistory", "FTP upload", e);
                //Notify.show(R.string.sync_fail);
            }
        }
        if (useGist) {
            try {
                ftpManager.uploadGistJsonString(updatedJsonString, "tv.json");
                //Notify.show(R.string.sync_success);

            } catch (IOException e) {
                //e.printStackTrace();
                Log.e("SyncHistory", "Gist upload", e);
                //Notify.show(R.string.sync_fail);
            }
            //RefreshEvent.history();

        }
    }

    public static void SyncAll() {
        SyncHistory();
        SyncKeep();
    }
    //通用的執行緒池管理類（例如：TaskExecutor），所有背景任務都透過它執行。
    public static class TaskExecutor {
        private static final ExecutorService executor = Executors.newSingleThreadExecutor();
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());

        public static void runOnBackground(Runnable task) {
            executor.execute(task);
        }

        public static void runOnMain(Runnable task) {
            mainHandler.post(task);
        }
    }


    public static void SyncHistory() {
        if (useFTP || useGist)
            //new SyncHistoryTask().execute();
            new SyncHistoryTask().run();
    }

    public static void SyncKeep() {
        if (useFTP || useGist)
            //new SyncKeepTask().execute();
            new SyncKeepTask().run();
    }

    public static class SyncHistoryTask {
        public static void run() {
            TaskExecutor.runOnBackground(() -> {
                try {
                    syncHistory();
                } catch (Exception e) {
                    Log.e("Sync", "Error during History sync operation", e);
                }
                TaskExecutor.runOnMain(() ->
                        Log.d("Sync", "Sync History operation completed")
                );
            });
        }
    }
    //public static class SyncHistoryTask {
    //    public static void run() {
    //        Executors.newSingleThreadExecutor().execute(() -> {
    //            try {
    //                syncHistory();
    //            } catch (Exception e) {
    //                Log.e("Sync", "Error during History sync operation", e);
    //            }
    //            new Handler(Looper.getMainLooper()).post(() ->
    //                    Log.d("Sync", "Sync History operation completed")
    //            );
    //        });
    //    }
    //}

    public static class SyncKeepTask {
        public static void run() {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    syncKeep();
                } catch (Exception e) {
                    Log.e("Sync", "Error during Keep sync operation", e);
                }

                new Handler(Looper.getMainLooper()).post(() ->
                        Log.d("Sync", "Sync Keep operation completed")
                );
            });
        }
    }


    //private static class SyncKeepTask extends android.os.AsyncTask<Void, Void, Void> {
    //    @Override
    //    protected Void doInBackground(Void... voids) {
    //        try {
    //            syncKeep();
    //        } catch (Exception e) {
    //            Log.e("Sync", "Error during Keep sync operation", e);
    //        }
    //        return null;
    //    }
    //    @Override
    //    protected void onPostExecute(Void result) {
    //        Log.d("Sync", "Sync Keep operation completed");
    //    }
    //}

    public List<Keep> parseKeepList(String jsonString){
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Keep.class, new JsonDeserializer<Keep>() {
                    @Override
                    public Keep deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                        JsonObject jsonObject = json.getAsJsonObject();
                        Keep keep = new Keep();

                        keep.setKey(jsonObject.get("key").getAsString());
                        keep.setVodPic(jsonObject.has("vodPic") ? jsonObject.get("vodPic").getAsString() : null);
                        keep.setVodName(jsonObject.has("vodName") ? jsonObject.get("vodName").getAsString() : null);
                        keep.setSiteName(jsonObject.has("siteName") ? jsonObject.get("siteName").getAsString() : null);
                        keep.setType(jsonObject.has("type") ? jsonObject.get("type").getAsInt() : 0);
                        keep.setCreateTime(jsonObject.has("createTime") ? jsonObject.get("createTime").getAsLong() : 0L);
                        keep.setCid(jsonObject.has("cid") ? jsonObject.get("cid").getAsInt() : -1);


                        return keep;
                    }
                })
                .create();

        JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
        if (jsonObject.has("Uptime")) {
            //ServerKupdated = jsonObject.getAsJsonObject("Uptime").getAsLong();
            ServerKupdated = jsonObject.getAsJsonPrimitive("Uptime").getAsLong();
        }

        if (jsonObject.has("Keep")) {
            String keepArrayString = jsonObject.getAsJsonArray("Keep").toString();

            Type listType = new TypeToken<List<Keep>>(){}.getType();
            return gson.fromJson(keepArrayString, listType);
        } else return null;
    }

    public List<History> parseHistoryList(String jsonString) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(History.class, new JsonDeserializer<History>() {
                    @Override
                    public History deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                        JsonObject jsonObject = json.getAsJsonObject();
                        History history = new History();

                        history.setKey(jsonObject.get("key").getAsString());
                        history.setVodPic(jsonObject.has("vodPic") ? jsonObject.get("vodPic").getAsString() : null);
                        history.setVodName(jsonObject.has("vodName") ? jsonObject.get("vodName").getAsString() : null);
                        history.setVodFlag(jsonObject.has("vodFlag") ? jsonObject.get("vodFlag").getAsString() : null);
                        history.setVodRemarks(jsonObject.has("vodRemarks") ? jsonObject.get("vodRemarks").getAsString() : null);
                        history.setEpisodeUrl(jsonObject.has("episodeUrl") ? jsonObject.get("episodeUrl").getAsString() : null);
                        history.setRevSort(jsonObject.has("revSort") && jsonObject.get("revSort").getAsBoolean());
                        history.setRevPlay(jsonObject.has("revPlay") && jsonObject.get("revPlay").getAsBoolean());
                        history.setCreateTime(jsonObject.has("createTime") ? jsonObject.get("createTime").getAsLong() : 0L);
                        history.setOpening(jsonObject.has("opening") ? jsonObject.get("opening").getAsLong() : 0L);
                        history.setEnding(jsonObject.has("ending") ? jsonObject.get("ending").getAsLong() : 0L);
                        history.setPosition(jsonObject.has("position") ? jsonObject.get("position").getAsLong() : 0L);
                        history.setDuration(jsonObject.has("duration") ? jsonObject.get("duration").getAsLong() : 0L);
                        history.setSpeed(jsonObject.has("speed") ? jsonObject.get("speed").getAsFloat() : 1.0f);
                        history.setPlayer(jsonObject.has("player") ? jsonObject.get("player").getAsInt() : 0);
                        history.setScale(jsonObject.has("scale") ? jsonObject.get("scale").getAsInt() : 0);
                        history.setCid(jsonObject.has("cid") ? jsonObject.get("cid").getAsInt() : -1);

                        // Set default values for missing fields
                        history.setLastUpdated(jsonObject.has("lastUpdated") ? jsonObject.get("lastUpdated").getAsLong() : getCurrentUTCTime());
                        history.setDeleted(jsonObject.has("deleted") && jsonObject.get("deleted").getAsBoolean());

                        return history;
                    }
                })
                .create();


        JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
        if (jsonObject.has("Uptime")) {
            ServerHupdated = jsonObject.getAsJsonPrimitive("Uptime").getAsLong();
        }
        if (jsonObject.has("History")) {
            String keepArrayString = jsonObject.getAsJsonArray("History").toString();

            Type listType = new TypeToken<List<History>>(){}.getType();
            return gson.fromJson(keepArrayString, listType);
        } else return null;

    }

    private static long getCurrentUTCTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        return calendar.getTimeInMillis();
    }

    private void updateSQLite(List<History> items) {
        AppDatabase.get().getHistoryDao().insertOrUpdate(items);
    }



}
