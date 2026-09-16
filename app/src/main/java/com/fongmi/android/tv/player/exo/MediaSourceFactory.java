package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;

import com.fongmi.android.tv.App;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UnstableApi
public class MediaSourceFactory implements MediaSource.Factory {

    private final DataSource.Factory dataSourceFactory;
    private static final boolean debug_enabled = false;

    public MediaSourceFactory() {
        this.dataSourceFactory = new androidx.media3.datasource.DefaultDataSource.Factory(App.get());
    }

    public MediaSourceFactory(DataSource.Factory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        String url = mediaItem.localConfiguration != null ? mediaItem.localConfiguration.uri.toString() : "";
        int type = ExoUtil.getType(url, mediaItem.localConfiguration != null ? mediaItem.localConfiguration.mimeType : "");
        DataSource.Factory factory = getDataSourceFactory(mediaItem);
        if (type == C.CONTENT_TYPE_DASH) {
            return new DashMediaSource.Factory(factory).setManifestParser(new YoutubeDashParser()).createMediaSource(mediaItem);
        } else if (type == C.CONTENT_TYPE_HLS) {
            return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
        } else if (type == C.CONTENT_TYPE_SS) {
            return new SsMediaSource.Factory(factory).createMediaSource(mediaItem);
        } else if (type == C.CONTENT_TYPE_RTSP) {
            return new RtspMediaSource.Factory().createMediaSource(mediaItem);
        } else {
            return new ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem);
        }
    }

    private DataSource.Factory getDataSourceFactory(MediaItem mediaItem) {
        Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
        Log.d("MediaSourceFactory", "getDataSourceFactory for: " + (mediaItem.localConfiguration != null ? mediaItem.localConfiguration.uri : "null"));
        if (headers.isEmpty()) return dataSourceFactory;
        androidx.media3.datasource.DefaultHttpDataSource.Factory httpFactory = new androidx.media3.datasource.DefaultHttpDataSource.Factory();
        String headerUa = headers.get(com.google.common.net.HttpHeaders.USER_AGENT);
        String ua = headerUa != null ? headerUa : getHeaderIgnoreCase(headers, com.google.common.net.HttpHeaders.USER_AGENT);
        if (ua != null) {
            if (debug_enabled)Log.d("MediaSourceFactory", "Setting User-Agent: " + ua);
            httpFactory.setUserAgent(ua);
        }
        if (debug_enabled) Log.d("MediaSourceFactory", "Setting Headers: " + headers);
        httpFactory.setDefaultRequestProperties(headers);
        httpFactory.setAllowCrossProtocolRedirects(true);
        return new androidx.media3.datasource.DefaultDataSource.Factory(App.get(), httpFactory);
    }

    private String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) if (key.equalsIgnoreCase(name)) return headers.get(key);
        return null;
    }

    @Override public MediaSource.Factory setDrmSessionManagerProvider(androidx.media3.exoplayer.drm.DrmSessionManagerProvider drm) { return this; }
    @Override public MediaSource.Factory setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy policy) { return this; }
    @Override public int[] getSupportedTypes() { return new int[]{C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_RTSP, C.CONTENT_TYPE_SS}; }

    private static class YoutubeDashParser extends androidx.media3.exoplayer.dash.manifest.DashManifestParser {
        private static final Map<String, Long> sessionAnchorSQ = new HashMap<>();
        private static final Map<String, Long> sessionAnchorT = new HashMap<>();
        private static final Map<String, Long> sessionStep = new HashMap<>(); 
        private static String currentSessionId = "";
        private static long sessionFixedAST_ms = 0;
        private static final String VERSION = "V518";

        @Override
        public DashManifest parse(Uri uri, InputStream inputStream) throws java.io.IOException {
            String vId = getVId(uri);
            boolean isProxy = uri.toString().contains("127.0.0.1:9978") || uri.toString().contains("dash?id=");
            boolean forceLive = uri.getQueryParameter("live") != null || uri.toString().contains("live=true");

            synchronized (sessionAnchorSQ) {
                if (!currentSessionId.equals(vId)) {
                    currentSessionId = vId;
                    sessionAnchorSQ.clear();
                    sessionAnchorT.clear();
                    sessionStep.clear();
                    sessionFixedAST_ms = 0;
                    Log.d("ExoUtil", ">>> YT_SILK_" + VERSION + ": SESSION START - " + vId + " (ForceLive=" + forceLive + ")");
                }
            }

            StringBuilder sb = new StringBuilder();
            try (java.util.Scanner s = new java.util.Scanner(inputStream, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                if (s.hasNext()) sb.append(s.next());
            }
            String rawXml = sb.toString();
            if (rawXml.isEmpty()) return super.parse(uri, inputStream);

            boolean isDynamic = rawXml.contains("type=\"dynamic\"") || rawXml.contains("type='dynamic'");
            boolean isYoutubeLive = (isDynamic || forceLive) && (rawXml.contains("googlevideo.com") || isProxy);

            Log.d("ExoUtil", ">>> YT_SILK_" + VERSION + ": Parsing DASH (Live=" + isYoutubeLive + ") - " + vId);

            // 1. Process Period Tag
            String modXml = rawXml;
            if (isYoutubeLive) {
                Matcher mP = Pattern.compile("(<Period\\b[^>]*>)").matcher(rawXml);
                if (mP.find()) {
                    String pTag = mP.group(1);
                    String newPTag = pTag.replaceAll("\\bstart\\s*=\\s*[\"'][^\"']+[\"']", "start=\"PT0S\"")
                                         .replaceAll("\\bid\\s*=\\s*[\"'][^\"']+[\"']", "id=\"stable_period\"");
                    if (!newPTag.contains("id=")) newPTag = newPTag.replaceFirst("(<Period\\b)", "$1 id=\"stable_period\"");
                    if (!newPTag.contains("start=")) newPTag = newPTag.replaceFirst("(<Period\\b)", "$1 start=\"PT0S\"");
                    modXml = rawXml.substring(0, mP.start()) + newPTag.replaceAll("\\s+", " ") + rawXml.substring(mP.end());
                }
            }

            // 2. Hierarchical Processing
            String finalXml = hierarchicalSanitize(modXml, vId, isYoutubeLive);

            // 3. Global Attributes
            finalXml = adjustGlobalAttrs(finalXml, isYoutubeLive);

            // 🛠️ DEBUG: Dump key XML snapshot
            dumpKeyXml(finalXml);

            try {
                return super.parse(uri, new ByteArrayInputStream(finalXml.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                android.util.Log.e("ExoUtil", ">>> YT_SILK_" + VERSION + ": Parsing FAILED. Sample: " + (finalXml.length() > 500 ? finalXml.substring(0, 500) : finalXml));
                throw e;
            }
        }

        private String getVId(Uri uri) {
            String id = uri.getQueryParameter("id");
            String pathId = id != null ? id : getVIdFromPath(uri);
            String finalId = pathId != null && pathId.contains(".") ? pathId.split("\\.")[0] : pathId;
            return finalId == null ? "default" : finalId;
        }

        private String getVIdFromPath(Uri uri) {
            List<String> segments = uri.getPathSegments();
            for (int i = 0; i < segments.size(); i++) {
                if ("id".equals(segments.get(i)) && i + 1 < segments.size()) return segments.get(i + 1);
            }
            return null;
        }

        private String hierarchicalSanitize(String xml, String sessionId, boolean isLive) {
            String[] adSets = xml.split("(?=<AdaptationSet|<\\w+:AdaptationSet)");
            if (adSets.length <= 0) return xml;

            StringBuilder newXml = new StringBuilder();
            newXml.append(sanitizeFragment(adSets[0], sessionId, 1000L, 1L, isLive));

            for (int i = 1; i < adSets.length; i++) {
                String adSetBlock = adSets[i];
                long adSetTS = extractAttr(adSetBlock, "timescale", 1000L);
                long adSetSN = extractAttr(adSetBlock, "startNumber", extractAttr(adSetBlock, "start_number", 1L));

                String[] reps = adSetBlock.split("(?=<Representation|<\\w+:Representation)");
                newXml.append(sanitizeFragment(reps[0], sessionId, adSetTS, adSetSN, isLive));

                for (int j = 1; j < reps.length; j++) {
                    newXml.append(sanitizeFragment(reps[j], sessionId, adSetTS, adSetSN, isLive));
                }
            }
            return newXml.toString();
        }

        private String sanitizeFragment(String body, String sessionId, long ts, long sn, boolean isYoutubeLive) {
            if (body == null) return "";
            long rawTS = extractAttr(body, "timescale", ts);
            long realTS = rawTS;
            if (rawTS == 1000L) {
                if (body.contains("video/") || body.contains("width=")) realTS = 90000L;
                else if (body.contains("audio/")) realTS = 48000L;
            }

            String trackKey = sessionId + "_" + realTS;

            // VOD: Relative shift (Quote Agnostic)
            if (!isYoutubeLive) {
                long pto = extractAttr(body, "presentationTimeOffset", 0L);
                if (debug_enabled && pto != 0) Log.d("ExoUtil", ">>> YT_VOD: Found non-zero PTO: " + pto + " for " + trackKey);

                String mod = body.replaceAll("\\bpresentationTimeOffset\\s*=\\s*[\"']-?\\d+[\"']", "presentationTimeOffset=\"0\"");
                if ((body.contains("<SegmentTemplate") || body.contains("<SegmentList")) && !mod.contains("presentationTimeOffset=")) {
                    mod = mod.replaceFirst("(<SegmentTemplate|<SegmentList)", "$1 presentationTimeOffset=\"0\"");
                }

                if (mod.contains("<SegmentTimeline")) {
                    long firstT = extractTimelineFirstT(mod);
                    // DANGER: If the URL template uses $Time$, shifting T might break requests.
                    boolean hasTimeTemplate = body.contains("$Time$") || body.contains("%7BTime%7D");
                    if (firstT > 0 && !hasTimeTemplate) {
                        if (debug_enabled) Log.d("ExoUtil", ">>> YT_VOD: Shifting timeline by -" + firstT + " to zero-base " + trackKey);
                        return shiftTimelineT(mod, -firstT);
                    } else if (firstT > 0) {
                        if (debug_enabled) Log.d("ExoUtil", ">>> YT_VOD: SKIP Shifting (Time Template detected) for " + trackKey);
                    }
                }
                return mod;
            }

            // Live: Linearization
            long realSN = extractAttr(body, "startNumber", extractAttr(body, "start_number", sn));
            if (realSN == 1L) {
                Matcher m = Pattern.compile("sq/(\\d+)").matcher(body);
                if (m.find()) {
                    String g = m.group(1);
                    if (g != null) realSN = Long.parseLong(g);
                }
            }

            synchronized (sessionAnchorSQ) {
                if (!sessionAnchorSQ.containsKey(trackKey) && realSN > 1000L) {
                    sessionAnchorSQ.put(trackKey, realSN);
                    sessionAnchorT.put(trackKey, 60L * realTS); 
                    // Lock the step duration at the start to prevent jitter
                    long initialD = extractAttr(body, "d", (5000L * realTS / 1000L));
                    sessionStep.put(trackKey, initialD);
                }

                Long lockedD = sessionStep.get(trackKey);
                long baseStep = (lockedD != null) ? lockedD : (5000L * realTS / 1000L);

                Long anchorSQObj = sessionAnchorSQ.get(trackKey);
                if (anchorSQObj == null || realSN < (anchorSQObj - 100L)) {
                    return body.replaceAll("\\bpresentationTimeOffset\\s*=\\s*[\"']-?\\d+[\"']", "presentationTimeOffset=\"0\"");
                }

                long stableT = sessionAnchorT.get(trackKey) + (realSN - anchorSQObj) * baseStep;
                String mod = body.replaceAll("\\bpresentationTimeOffset\\s*=\\s*[\"']-?\\d+[\"']", "presentationTimeOffset=\"0\"");
                if ((body.contains("<SegmentTemplate") || body.contains("<SegmentList")) && !mod.contains("presentationTimeOffset=")) {
                    mod = mod.replaceFirst("(<SegmentTemplate|<SegmentList)", "$1 presentationTimeOffset=\"0\"");
                }
                
                if (sessionFixedAST_ms == 0) {
                    sessionFixedAST_ms = System.currentTimeMillis() - (stableT * 1000 / realTS) - 45000L;
                }
                
                return injectTimelineTFixed(mod, stableT, trackKey, baseStep);
            }
        }

        private String injectTimelineTFixed(String body, long virtualT, String trackKey, long baseStep) {
            if (!body.contains("<SegmentTimeline")) return injectT(body, virtualT, trackKey);

            Pattern pTimeline = Pattern.compile("(<SegmentTimeline[^>]*>)([\\s\\S]*?)(</SegmentTimeline>)");
            Matcher mTimeline = pTimeline.matcher(body);
            if (!mTimeline.find()) return injectT(body, virtualT, trackKey);

            String timelineContent = mTimeline.group(2);
            if (timelineContent == null) return injectT(body, virtualT, trackKey);

            Pattern pS = Pattern.compile("<S\\s+([^>]*?)\\s*/?>");
            Matcher mS = pS.matcher(timelineContent);
            StringBuffer newTimeline = new StringBuffer();
            long currentVirtualT = virtualT;

            while (mS.find()) {
                String sAttr = mS.group(1);
                if (sAttr == null) continue;
                long r = extractAttr(sAttr, "r", 0L);
                
                // Use LOCKED baseStep to avoid manifest-to-manifest jitter
                String cleanSAttr = sAttr.replaceAll("\\bt\\s*=\\s*[\"']\\d+[\"']", "");
                String newSAttr = "t=\"" + currentVirtualT + "\" " + cleanSAttr.trim();
                
                mS.appendReplacement(newTimeline, Matcher.quoteReplacement("<S " + newSAttr.trim() + "/>"));
                currentVirtualT += baseStep * (r + 1);
            }
            mS.appendTail(newTimeline);

            return body.substring(0, mTimeline.start()) + mTimeline.group(1) + newTimeline + mTimeline.group(3) + body.substring(mTimeline.end());
        }

        private String adjustGlobalAttrs(String xml, boolean isLive) {
            if (!isLive) return xml;
            String res = xml;
            if (sessionFixedAST_ms > 0) {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                String astStr = fmt.format(new Date(sessionFixedAST_ms));
                res = res.replaceAll("\\bavailabilityStartTime\\s*=\\s*[\"'][^\"']+[\"']", "availabilityStartTime=\"" + astStr + "\"")
                         .replaceAll("\\bpublishTime\\s*=\\s*[\"'][^\"']+[\"']", "publishTime=\"" + astStr + "\"");
            }
            return res.replaceAll("\\bsuggestedPresentationDelay\\s*=\\s*[\"'][^\"']+[\"']", "suggestedPresentationDelay=\"PT30S\"")
                      .replaceAll("\\bminBufferTime\\s*=\\s*[\"'][^\"']+[\"']", "minBufferTime=\"PT4S\"");
        }

        private long extractTimelineFirstT(String body) {
            Pattern pS = Pattern.compile("<S\\s+[^>]*\\bt\\s*=\\s*[\"'](\\d+)[\"']");
            Matcher mS = pS.matcher(body);
            if (mS.find()) {
                String group = mS.group(1);
                if (group != null) return Long.parseLong(group);
            }
            return -1;
        }

        private String shiftTimelineT(String body, long delta) {
            Pattern pTimeline = Pattern.compile("(<SegmentTimeline[^>]*>)([\\s\\S]*?)(</SegmentTimeline>)");
            Matcher mTimeline = pTimeline.matcher(body);
            if (!mTimeline.find()) return body;

            String timelineContent = mTimeline.group(2);
            if (timelineContent == null) return body;
            
            Pattern pS = Pattern.compile("<S\\s+([^>]*?)\\s*/?>");
            Matcher mS = pS.matcher(timelineContent);
            StringBuffer newTimeline = new StringBuffer();

            while (mS.find()) {
                String sAttr = mS.group(1);
                if (sAttr == null) continue;
                Pattern pT = Pattern.compile("\\bt\\s*=\\s*[\"'](\\d+)[\"']");
                Matcher mT = pT.matcher(sAttr);
                String newSAttr;
                if (mT.find()) {
                    String tGroup = mT.group(1);
                    if (tGroup != null) {
                        newSAttr = sAttr.replaceFirst("\\bt\\s*=\\s*[\"']\\d+[\"']", "t=\"" + (Long.parseLong(tGroup) + delta) + "\"");
                    } else {
                        newSAttr = sAttr;
                    }
                } else {
                    newSAttr = sAttr;
                }
                mS.appendReplacement(newTimeline, Matcher.quoteReplacement("<S " + newSAttr.trim() + "/>"));
            }
            mS.appendTail(newTimeline);
            return body.substring(0, mTimeline.start()) + mTimeline.group(1) + newTimeline + mTimeline.group(3) + body.substring(mTimeline.end());
        }

        private String injectT(String body, long virtualT, String trackKey) {
            long originalT = extractAttr(body, "t", -1L);
            String mod = body.replaceAll("\\bt\\s*=\\s*[\"']\\d+[\"']", "");
            String newBody;
            if (mod.contains("<S ")) {
                newBody = mod.replaceFirst("<S ", "<S t=\"" + virtualT + "\" ");
            } else {
                newBody = mod;
            }
            Log.d("ExoUtil", ">>> YT_SILK_" + VERSION + " AUDIT: Replacing t attribute. Track: " + trackKey + ", Original t: " + originalT + " -> Virtual t: " + virtualT);
            return newBody;
        }

        private void dumpKeyXml(String xml) {
            if (!debug_enabled) return;
            try {
                String[] lines = xml.split("\n");
                StringBuilder dump = new StringBuilder("\n--- KEY XML SNAPSHOT ---\n");
                boolean inTimeline = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.contains("<MPD") || trimmed.contains("<Period") || trimmed.contains("availabilityStartTime") || trimmed.contains("mediaPresentationDuration")) {
                        dump.append(trimmed).append("\n");
                    }
                    if (trimmed.contains("<AdaptationSet") || trimmed.contains("<Representation")) {
                        dump.append("  ").append(trimmed).append("\n");
                        String ts = extractAttrString(trimmed, "timescale");
                        if (!ts.equals("N/A")) dump.append("    [timescale=").append(ts).append("]\n");
                    }
                    if (trimmed.contains("presentationTimeOffset") || trimmed.contains("startNumber")) {
                        dump.append("    ").append(trimmed).append("\n");
                    }
                    if (trimmed.contains("<SegmentTemplate") || trimmed.contains("<SegmentList")) {
                        dump.append("    ").append(trimmed).append("\n");
                        if (trimmed.contains("media=")) {
                            String media = trimmed.substring(trimmed.indexOf("media="));
                            dump.append("      [URL Sample: ").append(media.length() > 100 ? media.substring(0, 100) : media).append("...]\n");
                        }
                    }
                    if (trimmed.contains("<SegmentTimeline")) {
                        inTimeline = true;
                        dump.append("    ").append(trimmed).append("\n");
                    }
                    if (inTimeline && trimmed.contains("<S ")) {
                        dump.append("      ").append(trimmed).append("\n");
                        inTimeline = false;
                    }
                }
                dump.append("--- END SNAPSHOT ---");
                String finalLog = dump.toString();
                int maxLogSize = 3500;
                for (int i = 0; i <= finalLog.length() / maxLogSize; i++) {
                    int start = i * maxLogSize;
                    int end = Math.min((i + 1) * maxLogSize, finalLog.length());
                    if (start < end) Log.d("ExoUtil", finalLog.substring(start, end));
                }
            } catch (Exception ignored) {
            }
        }

        private String extractAttrString(String text, String attr) {
            Pattern p = Pattern.compile("\\b" + attr + "\\s*=\\s*[\"'](\\d+)[\"']");
            Matcher m = p.matcher(text);
            return m.find() ? m.group(1) : "N/A";
        }

        private long extractAttr(String text, String attr, long defaultVal) {
            String val = extractAttrString(text, attr);
            try {
                return val.equals("N/A") ? defaultVal : Long.parseLong(val);
            } catch (Exception e) {
                return defaultVal;
            }
        }
    }
}
