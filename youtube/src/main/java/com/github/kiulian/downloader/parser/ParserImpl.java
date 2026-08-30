// FINAL_FIX_VERSION_V30
package com.github.kiulian.downloader.parser;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.kiulian.downloader.Config;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.cipher.Cipher;
import com.github.kiulian.downloader.cipher.CipherFactory;
import com.github.kiulian.downloader.cipher.CipherFunction;
import com.github.kiulian.downloader.downloader.Downloader;
import com.github.kiulian.downloader.downloader.YoutubeCallback;
import com.github.kiulian.downloader.downloader.client.ClientType;
import com.github.kiulian.downloader.downloader.request.*;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.downloader.response.ResponseImpl;
import com.github.kiulian.downloader.extractor.Extractor;
import com.github.kiulian.downloader.model.playlist.PlaylistInfo;
import com.github.kiulian.downloader.model.subtitles.SubtitlesInfo;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.*;
import com.github.kiulian.downloader.model.search.SearchResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ParserImpl implements Parser {
    private static final String ANDROID_APIKEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String BASE_API_URL = "https://www.youtube.com/youtubei/v1";

    private static class DelegatedCipherFactory implements CipherFactory {
        Cipher lastCipher;
        final CipherFactory factory;
        DelegatedCipherFactory(CipherFactory f) { this.factory = f; }
        @Override public Cipher createCipher(String url) throws YoutubeException { return lastCipher = factory.createCipher(url); }
        @Override public void addInitialFunctionPattern(int p, String r) { factory.addInitialFunctionPattern(p, r); }
        @Override public void addFunctionEquivalent(String r, CipherFunction f) { factory.addFunctionEquivalent(r, f); }
    }

    private final Config config;
    private final Downloader downloader;
    private final Extractor extractor;
    private final DelegatedCipherFactory cipherFactory;

    public ParserImpl(Config config, Downloader downloader, Extractor extractor, CipherFactory cipherFactory) {
        this.config = config;
        this.downloader = downloader;
        this.extractor = extractor;
        this.cipherFactory = new DelegatedCipherFactory(cipherFactory);
    }

    @Override
    public Response<VideoInfo> parseVideo(RequestVideoInfo request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<VideoInfo> result = executorService.submit(() -> parseVideo(request.getVideoId(), request.getCallback(), request.getClientType()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            return ResponseImpl.from(parseVideo(request.getVideoId(), request.getCallback(), request.getClientType()));
        } catch (YoutubeException e) { return ResponseImpl.error(e); }
    }

    private VideoInfo parseVideo(String videoId, YoutubeCallback<VideoInfo> callback, ClientType client) throws YoutubeException {
        try {
            VideoInfo videoInfo = parseVideoAndroid(videoId, callback, client);
            if (videoInfo == null || (videoInfo.details().liveUrl() == null && videoInfo.formats().isEmpty())) {
                videoInfo = parseVideoWeb(videoId, callback);
            }
            if (callback != null) callback.onFinished(videoInfo);
            return videoInfo;
        } catch (Exception e) {
            android.util.Log.e("Youtube", "parseVideo error: " + e.getMessage());
            return null;
        }
    }

    private VideoInfo parseVideoAndroid(String videoId, YoutubeCallback<VideoInfo> callback, ClientType client) throws YoutubeException {
        String url = BASE_API_URL + "/player?key=" + ANDROID_APIKEY;
        
        JSONObject body = client.getBody();
        body.fluentPut("videoId", videoId);
        
        // V30: DEEP CLEAN - Resolve duplicate and invalid field issues
        body.remove("visitorData");
        body.remove("playbackContext");

        JSONObject context = body.getJSONObject("context");
        if (context == null) { context = new JSONObject(); body.put("context", context); }
        
        String vd = "Cgs0dWZ6N1pSOHJWNCiB-p6zBg==";
        context.fluentPut("visitorData", vd);
        JSONObject clientObj = context.getJSONObject("client");
        if (clientObj != null) { clientObj.fluentPut("visitorData", vd); }

        // V30: Targeted strategies for different clients
        if (client.getName().contains("TV")) {
            JSONObject playbackContext = new JSONObject();
            JSONObject contentPlaybackContext = new JSONObject();
            contentPlaybackContext.fluentPut("signatureTimestamp", 20265);
            // Fix V29 error: html5Preference is a scalar string field!
            contentPlaybackContext.fluentPut("html5Preference", "HTML5_PREF_WANTS_HTML5");
            playbackContext.fluentPut("contentPlaybackContext", contentPlaybackContext);
            body.fluentPut("playbackContext", playbackContext);
        } else {
            // Android Embedded and IOS must NOT have playbackContext to bypass 400 errors
        }

        String jsonBody = body.toJSONString();
        android.util.Log.d("Youtube", "Requesting " + videoId + " with " + client.getName());
        android.util.Log.d("Youtube", "Body: " + jsonBody);

        RequestWebpage request = new RequestWebpage(url, "POST", jsonBody)
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .header("X-YouTube-Client-Name", getClientNameId(client.getName()))
                .header("X-YouTube-Client-Version", client.getVersion())
                .header("X-Goog-Api-Format-Version", "2")
                .header("User-Agent", getUserAgent(client.getName()));

        Response<String> response = downloader.downloadWebpage(request);
        if (!response.ok()) return null;

        JSONObject playerResponse;
        try { playerResponse = JSONObject.parseObject(response.data()); } catch (Exception e) { return null; }
        if (playerResponse == null) return null;

        if (playerResponse.containsKey("playabilityStatus")) {
            android.util.Log.d("Youtube", "Status: " + playerResponse.getJSONObject("playabilityStatus").getString("status"));
        }

        VideoDetails videoDetails = parseVideoDetails(videoId, playerResponse);
        if (videoDetails != null && (videoDetails.isDownloadable() || videoDetails.liveUrl() != null)) {
            String clientVersion = extractor.extractClientVersionFromContext(playerResponse.getJSONObject("responseContext"));
            List<Format> formats = parseFormats(playerResponse, null, clientVersion);
            return new VideoInfo(videoDetails, formats, parseCaptions(playerResponse));
        }
        return videoInfoFromDetails(videoDetails);
    }

    private VideoInfo videoInfoFromDetails(VideoDetails details) {
        return new VideoInfo(details != null ? details : new VideoDetails(""), Collections.emptyList(), Collections.emptyList());
    }

    private VideoInfo parseVideoWeb(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        String htmlUrl = "https://www.youtube.com/watch?v=" + videoId;
        Response<String> response = downloader.downloadWebpage(new RequestWebpage(htmlUrl).header("User-Agent", getUserAgent("WEB")));
        if (!response.ok()) return null;
        try {
            JSONObject playerConfig = extractor.extractPlayerConfigFromHtml(response.data());
            JSONObject playerResponse = playerConfig.getJSONObject("args").getJSONObject("player_response");
            VideoDetails videoDetails = parseVideoDetails(videoId, playerResponse);
            String jsUrl = extractor.extractJsUrlFromConfig(playerConfig, videoId);
            String clientVersion = extractor.extractClientVersionFromContext(playerResponse.getJSONObject("responseContext"));
            return new VideoInfo(videoDetails, parseFormats(playerResponse, jsUrl, clientVersion), parseCaptions(playerResponse));
        } catch (Exception e) { return null; }
    }

    private String getClientNameId(String name) {
        if (name.equals("WEB")) return "1";
        if (name.equals("MWEB")) return "2";
        if (name.equals("ANDROID")) return "17"; // Android Embedded
        if (name.equals("IOS")) return "5";
        if (name.equals("TVHTML5")) return "7";
        if (name.equals("ANDROID_TV")) return "30";
        return "3";
    }

    private String getUserAgent(String name) {
        if (name.contains("TVHTML5")) return "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.202 Safari/537.36";
        if (name.contains("IOS")) return "com.google.ios.youtube/19.29.1 (iPhone15,3; U; CPU iOS 17_6_1 like Mac OS X; en_US)";
        if (name.contains("ANDROID")) return "com.google.android.youtube/19.45.42 (Linux; U; Android 14) ExoPlayerLib/2.11.4";
        return "com.google.android.youtube/19.45.42 (Linux; U; Android 14)";
    }

    private VideoDetails parseVideoDetails(String videoId, JSONObject playerResponse) {
        if (playerResponse == null || !playerResponse.containsKey("videoDetails")) return new VideoDetails(videoId);
        JSONObject details = playerResponse.getJSONObject("videoDetails");
        if (details == null) return new VideoDetails(videoId);
        String hls = playerResponse.containsKey("streamingData") ? playerResponse.getJSONObject("streamingData").getString("hlsManifestUrl") : null;
        return new VideoDetails(details, hls);
    }

    private List<Format> parseFormats(JSONObject playerResponse, String jsUrl, String clientVersion) throws YoutubeException {
        if (playerResponse == null || !playerResponse.containsKey("streamingData")) return Collections.emptyList();
        JSONObject streamingData = playerResponse.getJSONObject("streamingData");
        List<Format> formats = new ArrayList<>();
        addFormats(formats, streamingData.getJSONArray("formats"), jsUrl, false, clientVersion);
        addFormats(formats, streamingData.getJSONArray("adaptiveFormats"), jsUrl, true, clientVersion);
        return formats;
    }

    private void addFormats(List<Format> formats, JSONArray array, String js, boolean adaptive, String ver) {
        if (array == null) return;
        for (int i = 0; i < array.size(); i++) {
            JSONObject json = array.getJSONObject(i);
            Itag itag = Itag.unknown;
            try { itag = Itag.valueOf("i" + json.getIntValue("itag")); } catch (Exception ignored) {}
            if (json.containsKey("signatureCipher")) {
                Map<String, String> params = new HashMap<>();
                for (String s : json.getString("signatureCipher").replace("\\u0026", "&").split("&")) {
                    String[] kv = s.split("="); if (kv.length == 2) params.put(kv[0], kv[1]);
                }
                try {
                    String url = URLDecoder.decode(params.get("url"), "UTF-8");
                    if (params.containsKey("s") && js != null) url += "&sig=" + cipherFactory.createCipher(js).getSignature(URLDecoder.decode(params.get("s"), "UTF-8"));
                    json.put("url", url);
                } catch (Exception ignored) {}
            }
            if (itag.isVideo() && itag.isAudio()) formats.add(new VideoWithAudioFormat(json, adaptive, ver));
            else if (itag.isVideo()) formats.add(new VideoFormat(json, adaptive, ver));
            else if (itag.isAudio()) formats.add(new AudioFormat(json, adaptive, ver));
        }
    }

    private List<SubtitlesInfo> parseCaptions(JSONObject playerResponse) {
        try {
            JSONArray tracks = playerResponse.getJSONObject("captions").getJSONObject("playerCaptionsTracklistRenderer").getJSONArray("captionTracks");
            List<SubtitlesInfo> list = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                JSONObject obj = tracks.getJSONObject(i);
                list.add(new SubtitlesInfo(obj.getString("baseUrl"), obj.getString("languageCode"), false));
            }
            return list;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    @Override public Response<PlaylistInfo> parsePlaylist(RequestPlaylistInfo r) { return ResponseImpl.from(null); }
    @Override public Response<PlaylistInfo> parseChannelsUploads(RequestChannelUploads r) { return ResponseImpl.from(null); }
    @Override public Response<List<SubtitlesInfo>> parseSubtitlesInfo(RequestSubtitlesInfo r) { return ResponseImpl.from(null); }
    @Override public Response<SearchResult> parseSearchResult(RequestSearchResult r) { return ResponseImpl.from(null); }
    @Override public Response<SearchResult> parseSearchContinuation(RequestSearchContinuation r) { return ResponseImpl.from(null); }
    @Override public Response<SearchResult> parseSearcheable(RequestSearchable r) { return ResponseImpl.from(null); }
}
