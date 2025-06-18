package com.fongmi.android.tv.bean;

import android.util.Log;

import com.fongmi.android.tv.App;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
import java.util.List;


public class FtpManager {

    private static final String TAG = "HistorySyncManager";
    //private static final String GIST_TOKEN = "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";
    //private static final String GIST_URL = "https://api.github.com/gists/1de074cade4ca85c981c801a45eaac5e";

    private String server;
    private String path;
    private int port;
    private String username;
    private String password;
    private boolean useFTPS;
    private boolean useGist;
    private String gisttoken;
    private String gisturl;
    public boolean isServerReachable;

    public void initGist(String gurl, String gtoken) {
        this.gisturl = "https://api.github.com/gists/" + gurl;
        this.gisttoken = gtoken;
        this.useGist = true;
    }
    public FtpManager(String server, String path, int port, String username, String password, boolean useFTPS) {
        this.server = server;
        this.path = path;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useFTPS = useFTPS;
        this.isServerReachable = !this.server.trim().isEmpty() && !this.server.isEmpty() && !this.path.trim().equalsIgnoreCase("");
    }

    public FtpManager(String ftpUrl, String username, String password) {
        try {
            this.username = username;
            this.password = password;
            parseUrl(ftpUrl);
            this.isServerReachable = this.server != null && !this.server.trim().isEmpty() && this.path != null &&  !this.path.trim().equalsIgnoreCase("");

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseUrl(String ftpUrl) throws URISyntaxException, NullPointerException  {
        URI uri = new URI(ftpUrl);
        this.server = uri.getHost();
        this.path= uri.getPath();
        this.port = (uri.getPort() == -1) ? useFTPS ? 990 : 21 : uri.getPort();

        if (this.username.trim().isEmpty() || this.password.trim().isEmpty() || this.username.trim().equalsIgnoreCase("") || this.password.trim().equalsIgnoreCase(""))
        {
            if (uri.getUserInfo() != null) {
                String[] userInfo = uri.getUserInfo().split(":");
                this.username = userInfo[0];
                this.password = (userInfo.length > 1) ? userInfo[1] : "";
            } else {
                this.username = "anonymous";
                this.password = "";
            }
        }
    }

    private FTPClient connectToFTP() throws IOException {
        if (!isServerReachable) {
            throw new IOException("Server is not reachable.");
        }

        FTPClient ftpClient = useFTPS ? new FTPSClient() : new FTPClient();
        ftpClient.connect(server, port);

        if (username != null && !username.isEmpty()) {
            ftpClient.login(username, password);
        } else {
            ftpClient.login("anonymous", "");
        }

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.enterLocalPassiveMode();
        return ftpClient;
    }

    public String downloadJsonFileAsString(String remoteFilePath) throws IOException {
        remoteFilePath = remoteFilePath==null? this.path : remoteFilePath;

        FTPClient ftpClient =  connectToFTP();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean success = ftpClient.retrieveFile(remoteFilePath, outputStream);

            if (!success) {
                return null;
                //throw new IOException("Failed to download the file: " + remoteFilePath);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    // Log the exception
                }
            }
        }
    }

    public void uploadJsonString(String jsonString, String remoteFilePath) throws IOException {
        remoteFilePath = remoteFilePath==null? this.path : remoteFilePath;

        FTPClient ftpClient = connectToFTP();
        try {
            createRemoteDirectories(ftpClient, remoteFilePath);

            // Convert the JSON string to an InputStream
            InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));

            boolean done = ftpClient.storeFile(remoteFilePath, inputStream);
            inputStream.close();
            if (!done) {
                throw new IOException("Failed to upload the file.");
            }
        } finally {
            ftpClient.disconnect();
        }
    }

    private void createRemoteDirectories(FTPClient ftpClient, String remoteFilePath) throws IOException {
        String[] pathElements = remoteFilePath.split("/");
        String currentPath = "";

        for (int i = 0; i < pathElements.length - 1; i++) {
            if (!pathElements[i].isEmpty()) {
                currentPath += "/" + pathElements[i];
                boolean dirExists = ftpClient.changeWorkingDirectory(currentPath);
                if (!dirExists) {
                    boolean created = ftpClient.makeDirectory(currentPath);
                    if (!created) {
                        throw new IOException("Unable to create remote directory: " + currentPath);
                    }
                }
            }
        }
    }

    public void uploadGistJsonString(String jsonString, String remoteFilePath) throws IOException {
        if (!useGist || remoteFilePath == null) return;
        try {
            JSONObject gistContent = new JSONObject().put("content", jsonString);
            JSONObject gistfiles = new JSONObject().put(remoteFilePath, gistContent);
            JSONObject requestBody = new JSONObject().put("files", gistfiles);

            URL url = new URL(gisturl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Authorization", "Bearer " + gisttoken);
            //conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error updating Gist", e);
        }
    }

    public String downloadGistJsonFileAsString(String remoteFilePath) {

        String content = null;
        if (!useGist || remoteFilePath == null) return null;
        //List<History> items = new ArrayList<>();

        try {
            URL url = new URL(gisturl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String authString = new String();
            authString = (!gisttoken.startsWith("github_pat_") ? "token ": "") + gisttoken;

            conn.setRequestProperty("Authorization",  authString);
            //conn.setRequestProperty("Authorization", "Bearer " + gisttoken);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            //conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

            int responseCode = conn.getResponseCode();
            if ( responseCode != 200) {
                StringBuffer response = new StringBuffer();

                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                } catch (Exception e) {
                    response.append(e.toString());
                }

                Log.d ("Gist", "Response: (" + responseCode + ") ->" + response.toString());
                //throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            StringBuilder response = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                response.append(output);
            }

            JSONObject jsonObject = new JSONObject(response.toString());
            JSONObject files = jsonObject.getJSONObject("files");
            JSONObject tvJson = files.getJSONObject(remoteFilePath);
             content = tvJson.getString("content");

            //items = History.arrayFrom(content);

            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data from Gist", e);
        }
        //return items;
        return content;
    }



    private void updateGist(List<History> items) {
        try {
            JSONObject contentJson = new JSONObject();
            JSONArray historyArray = new JSONArray(App.gson().toJson(items));
            contentJson.put("History", historyArray);

            JSONObject gistContent = new JSONObject();
            gistContent.put("content", contentJson.toString());

            JSONObject files = new JSONObject();
            files.put("tv.json", gistContent);

            JSONObject requestBody = new JSONObject();
            requestBody.put("files", files);

            URL url = new URL(gisturl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Authorization", "token " + gisttoken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error updating Gist", e);
        }
    }


}

