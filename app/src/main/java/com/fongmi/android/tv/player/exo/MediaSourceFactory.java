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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        if (type == C.CONTENT_TYPE_DASH) {
            return new DashMediaSource.Factory(dataSourceFactory).setManifestParser(new YoutubeDashParser()).createMediaSource(mediaItem);
        } else if (type == C.CONTENT_TYPE_HLS) {
            return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        } else if (type == C.CONTENT_TYPE_SS) {
            return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        } else if (type == C.CONTENT_TYPE_RTSP) {
            return new RtspMediaSource.Factory().createMediaSource(mediaItem);
        } else {
            return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
    }

    @Override public MediaSource.Factory setDrmSessionManagerProvider(androidx.media3.exoplayer.drm.DrmSessionManagerProvider drm) { return this; }
    @Override public MediaSource.Factory setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy policy) { return this; }
    @Override public int[] getSupportedTypes() { return new int[]{C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_RTSP, C.CONTENT_TYPE_SS}; }

    /**
     * V480: The Zero-Offset Linearizer
     * Force PTO=0 and sync AST to linearized raw media timestamps.
     */
    private static class YoutubeDashParser extends androidx.media3.exoplayer.dash.manifest.DashManifestParser {
        private static final Map<String, Long> sessionAnchorSQ = new HashMap<>();
        private static final Map<String, Long> sessionAnchorT = new HashMap<>();
        private static String currentSessionId = "";
        private static long sessionFixedAST_ms = 0;
        private static final Map<String, Long> sessionStep = new HashMap<>();
        private static final String VERSION = "V490";

        @Override
        public DashManifest parse(Uri uri, InputStream inputStream) throws java.io.IOException {
            String vId = uri.getQueryParameter("id");
            if (vId == null) {
                List<String> segments = uri.getPathSegments();
                for (int i = 0; i < segments.size(); i++) {
                    if ("id".equals(segments.get(i)) && i + 1 < segments.size()) {
                        vId = segments.get(i + 1);
                        break;
                    }
                }
            }
            if (vId != null && vId.contains(".")) vId = vId.split("\\.")[0];
            if (vId == null) vId = "default";

            synchronized (sessionAnchorSQ) {
                if (!currentSessionId.equals(vId)) {
                    currentSessionId = vId;
                    sessionAnchorSQ.clear();
                    sessionAnchorT.clear();
                    sessionStep.clear();
                    sessionFixedAST_ms = 0;
                    Log.d("ExoUtil", ">>> YT_SILK_" + VERSION + ": SESSION START - " + vId);
                }
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            }
            String xml = sb.toString();

            boolean isDynamic = xml.contains("type=\"dynamic\"") || xml.contains("type='dynamic'");
            Log.d("ExoUtil", ">>> YT_SILK_" + VERSION + ": Parsing DASH (isDynamic=" + isDynamic + ") - " + vId);

            if (isDynamic) {
                // 1. Robust Global Reconstruction (Live only)
                xml = absoluteSanitize(xml, vId);

                // 2. Final Metadata Polish (AST/Delay/Period)
                if (sessionFixedAST_ms > 0) {
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String astStr = fmt.format(new Date(sessionFixedAST_ms));
                    xml = xml.replaceAll("availabilityStartTime\\s*=\\s*\"[^\"]+\"", "availabilityStartTime=\"" + astStr + "\"");
                    xml = xml.replaceAll("publishTime\\s*=\\s*\"[^\"]+\"", "publishTime=\"" + astStr + "\"");
                }
                xml = xml.replaceAll("suggestedPresentationDelay\\s*=\\s*\"[^\"]+\"", "suggestedPresentationDelay=\"PT30S\"");
                xml = xml.replaceAll("minBufferTime\\s*=\\s*\"[^\"]+\"", "minBufferTime=\"PT4S\"");
                xml = xml.replaceAll("<Period[^>]*>", "<Period id=\"stable_period\" start=\"PT0S\">");
            }

            // 3. Smart XML Dump
            dumpKeyXml(xml);

            return super.parse(uri, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }

        private String absoluteSanitize(String xml, String sessionId) {
            String[] adaptationSets = xml.split("<AdaptationSet");
            if (adaptationSets.length <= 1) return xml;

            // Sanitize Header
            String header = sanitizeFragment(adaptationSets[0], sessionId, 1000L, 1L);
            
            StringBuilder newXml = new StringBuilder(header);
            for (int i = 1; i < adaptationSets.length; i++) {
                String adSetBody = adaptationSets[i];
                long adSetTS = extractAttr(adSetBody, "timescale", 1000L);
                long adSetSN = extractAttr(adSetBody, "startNumber", extractAttr(adSetBody, "start_number", 1L));

                String[] representations = adSetBody.split("<Representation");
                String adSetHeader = sanitizeFragment(representations[0], sessionId, adSetTS, adSetSN);

                for (int j = 1; j < representations.length; j++) {
                    String repBody = sanitizeFragment(representations[j], sessionId, adSetTS, adSetSN);
                    representations[j] = repBody;
                }
                
                newXml.append("<AdaptationSet").append(adSetHeader);
                for (int j = 1; j < representations.length; j++) {
                    newXml.append("<Representation").append(representations[j]);
                }
            }
            return newXml.toString();
        }

        private String sanitizeFragment(String body, String sessionId, long ts, long sn) {
            // SQ Probe
            long realSN = extractAttr(body, "startNumber", extractAttr(body, "start_number", sn));
            if (realSN == 1L) {
                Matcher m = Pattern.compile("sq/(\\d+)").matcher(body);
                if (m.find()) {
                    String g1 = m.group(1);
                    if (g1 != null) realSN = Long.parseLong(g1);
                }
            }
            
            // Timescale Probe
            long realTS = extractAttr(body, "timescale", ts);
            if (realTS == 1000L) {
                if (body.contains("video/") || body.contains("width=")) realTS = 90000L;
                else if (body.contains("audio/")) realTS = 44100L;
            }

            // Linearization Logic
            String trackKey = sessionId + "_" + realTS;
            long anchorSQ, anchorT;
            synchronized (sessionAnchorSQ) {
                if (!sessionAnchorSQ.containsKey(trackKey)) {
                    sessionAnchorSQ.put(trackKey, realSN);
                    long rawT = extractAttr(body, "t", 0L);
                    if (rawT == 0L) rawT = (realTS == 1000L) ? 15000000000L : (realTS * 15000000L / 1000L);
                    sessionAnchorT.put(trackKey, rawT);
                }
                    Long valSQ = sessionAnchorSQ.get(trackKey);
                    Long valT = sessionAnchorT.get(trackKey);
                    anchorSQ = (valSQ != null) ? valSQ : realSN;
                    anchorT = (valT != null) ? valT : 0L;
            }

            long stableT = anchorT + (realSN - anchorSQ) * 5000L * realTS / 1000L;
            
            // Force PTO = 0
            String mod = body.replaceAll("\\bpresentationTimeOffset\\s*=\\s*\"-?\\d+\"", "presentationTimeOffset=\"0\"");
            if ((body.contains("<SegmentTemplate") || body.contains("<SegmentList")) && !mod.contains("presentationTimeOffset=\"0\"")) {
                mod = mod.replaceFirst("(<SegmentTemplate|<SegmentList)", "$1 presentationTimeOffset=\"0\"");
            }
            
            mod = injectT(mod, stableT);

            // Calculate global AST based on the very first segment seen in the session
            synchronized (sessionAnchorSQ) {
                if (sessionFixedAST_ms == 0) {
                    sessionFixedAST_ms = System.currentTimeMillis() - (stableT * 1000 / realTS) - 45000L; // 45s lead
                }
            }
            
            return mod;
        }

        private String injectT(String body, long virtualT) {
            String newBody = body.replaceAll("\\bt\\s*=\\s*\"\\d+\"", "t=\"" + virtualT + "\"");
            if (newBody.equals(body) && body.contains("<S ")) {
                newBody = body.replaceFirst("<S ", "<S t=\"" + virtualT + "\" ");
            }
            return newBody;
        }

        private void dumpKeyXml(String xml) {
            try {
                String[] lines = xml.split("\n");
                StringBuilder dump = new StringBuilder("\n--- KEY XML SNAPSHOT ---\n");
                boolean inTimeline = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.contains("<MPD") || trimmed.contains("<Period") || trimmed.contains("availabilityStartTime")) {
                        dump.append(trimmed).append("\n");
                    }
                    if (trimmed.contains("<AdaptationSet") || trimmed.contains("<Representation")) {
                        dump.append("  ").append(trimmed).append("\n");
                    }
                    if (trimmed.contains("presentationTimeOffset") || trimmed.contains("startNumber")) {
                        dump.append("    ").append(trimmed).append("\n");
                    }
                    if (trimmed.contains("<SegmentTimeline")) {
                        inTimeline = true;
                        dump.append("    ").append(trimmed).append("\n");
                    }
                    if (inTimeline && trimmed.contains("<S ")) {
                        dump.append("      ").append(trimmed).append("\n");
                        inTimeline = false; // Only show first S
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
            } catch (Exception e) {}
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
