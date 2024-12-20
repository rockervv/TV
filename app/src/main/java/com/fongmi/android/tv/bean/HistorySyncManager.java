
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

import java.lang.reflect.Type;
import java.util.TimeZone;
import java.util.Calendar;

public class HistorySyncManager {


    private static FtpManager ftpManager;
    private static FtpManager ftpManagerk;
    private static boolean useGist = false;
    private static boolean useFTP = false;
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
        String jsonData;
        Gson gson;
        if (!useFTP && !useGist) {
            return;
        } else {
            jsonData = null;
            gson = new Gson();
            if (useFTP) {
                try {
                    jsonData = ftpManagerk.downloadJsonFileAsString(null);///USB2T/(Documents)/(TVBox)/TV2/TV.json");
                } catch (IOException e) {
                    Log.e("Keep", "FTP download", e);
                }
            }
            if (useGist) {
                String jsontmp = ftpManagerk.downloadGistJsonFileAsString("keep.json");///USB2T/(Documents)/(TVBox)/TV2/TV.json");
                if (jsonData == null) jsonData = jsontmp;
            }
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
        JsonObject jsonObject = jsonData==null? new JsonObject(): gson.fromJson(jsonData, JsonObject.class);
        List<Keep> ftpItems = jsonData==null? new ArrayList<>(): get().parseKeepList(jsonData);
            //List<Keep> ftpItems = Keep.arrayFrom(jsonData);

            //JsonObject jsonObject = jsonData==null? new JsonObject(): App.gson().fromJson(jsonData, JsonObject.class);
            //List<Keep> ftpItems = jsonData==null? new ArrayList<>(): parseKeepList(jsonData);
            //List<Keep> sqliteItems =AppDatabase.get().getHistoryDao().getAll();
            //List<Keep> vod = Keep.getVod();
            //List<Keep> live = Keep.getLive();
        if (ftpItems == null) ftpItems = new ArrayList<>();
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
        Keep.sync(newMergedItems);
        jsonObject.add("Keep", App.gson().toJsonTree(newMergedItems));
        String updatedJsonString = App.gson().toJson(jsonObject);

        if (useFTP) {
            try {
                ftpManagerk.uploadJsonString(updatedJsonString, null);
            } catch (IOException e) {
                Log.e("Keep", "FTP upload", e);

            }
        }

        if (useGist && newMergedItems.size() != ftpItems.size()) {
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
        String jsonData;
        Gson gson;

        if (!useFTP && !useGist) {
            return;
        } else {
            jsonData = null;
            gson = new Gson();
            if (useFTP) {
                try {
                    jsonData = ftpManager.downloadJsonFileAsString(null);///USB2T/(Documents)/(TVBox)/TV2/TV.json");
                } catch (IOException e) {
                    Log.e("History", "FTP download", e);
                }
            }
            if (useGist) {
                String jsontmp = ftpManager.downloadGistJsonFileAsString("tv.json");///USB2T/(Documents)/(TVBox)/TV2/TV.json");
                if (jsonData == null) jsonData = jsontmp;
            }
        }
        JsonObject jsonObject = jsonData==null? new JsonObject(): gson.fromJson(jsonData, JsonObject.class);
        List<History> ftpItems = jsonData==null? new ArrayList<>(): get().parseHistoryList (jsonData);
        if (ftpItems == null) ftpItems = new ArrayList<>();

        List<History> sqliteItems =AppDatabase.get().getHistoryDao().getAll();
        List<History> newMergedItems = History.syncLists(sqliteItems, ftpItems);


            //AppDatabase.get().runInTransaction(new Runnable() {
            //    @Override
            //    public void run() {
            //        AppDatabase.get().getHistoryDao().insertOrUpdate(newMergedItems);
            //        //TODO or NotToDo: delete hard, if lastupdated is more than xx days old, delete it from the database
            //        //AppDatabase.get().getHistoryDao().deleteHard();
            //    }
            //});
        History.sync(newMergedItems);
        jsonObject.add("History", gson.toJsonTree(newMergedItems));
        String updatedJsonString = gson.toJson(jsonObject);
            //ftpManager.uploadJsonString(updatedJsonString, "/USB2T/(Documents)/(TVBox)/TV2/TV.json");
        if (useFTP) {
            try {
                ftpManager.uploadJsonString(updatedJsonString, null);
            } catch (IOException e) {
                //e.printStackTrace();
                Log.e("History", "FTP upload", e);
                //Notify.show(R.string.sync_fail);
            }
        }
        if (useGist && newMergedItems.size() != ftpItems.size()) {
            try {
                ftpManager.uploadGistJsonString(updatedJsonString, "tv.json");
                //Notify.show(R.string.sync_success);

            } catch (IOException e) {
                //e.printStackTrace();
                Log.e("History", "Gist upload", e);
                //Notify.show(R.string.sync_fail);
            }
            //RefreshEvent.history();

        }
    }

    public static void SyncAll() {
        SyncHistory();
        SyncKeep();
    }

    public static void SyncHistory() {
        if (useFTP || useGist)
            new SyncHistoryTask().execute();
    }

    public static void SyncKeep() {
        if (useFTP || useGist)
            new SyncKeepTask().execute();
    }

    private static class SyncHistoryTask extends android.os.AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                syncHistory();
            } catch (Exception e) {
                Log.e("Sync", "Error during History sync operation", e);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            Log.d("Sync", "Sync History operation completed");
        }
    }

    private static class SyncKeepTask extends android.os.AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                syncKeep();
            } catch (Exception e) {
                Log.e("Sync", "Error during Keep sync operation", e);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            Log.d("Sync", "Sync Keep operation completed");
        }
    }

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
        if (jsonObject.has("keep")) {
            String keepArrayString = jsonObject.getAsJsonArray("keep").toString();

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
