package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.dash.manifest.SegmentBase;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.InputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaSourceFactory implements MediaSource.Factory {

    private static final int CACHE_SPACE_PERCENT = 80;
    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private static String anchoredVideoId = "";
    private static long anchoredAST = -1;
    private static long anchoredPTO = -1;
    private static final Map<String, TreeMap<Long, Long>> timelineT = new HashMap<>();
    private static final Map<String, TreeMap<Long, Long>> timelineD = new HashMap<>();

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private HttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;

    public MediaSourceFactory() {
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory());
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers) {
        HttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.client());
        factory.setDefaultRequestProperties(headers);
        return new DefaultDataSource.Factory(App.get(), factory);
    }

    static synchronized Cache getCache() {
        if (cache != null) return cache;
        File dir = Path.exo();
        return cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(getMaxCacheSize(dir)), getDatabaseProvider());
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static long getMaxCacheSize(File dir) {
        long usedBytes = getFolderSize(dir);
        long availableBytes = Math.max(0, dir.getUsableSpace());
        long storageBudget = (usedBytes + availableBytes) * CACHE_SPACE_PERCENT / 100;
        return Math.min(PreloadSetting.getPreloadSizeBytes(), storageBudget);
    }

    private static long getFolderSize(File file) {
        long size = 0;
        if (file == null) return 0;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) size += getFolderSize(f);
        } else {
            size = file.length();
        }
        return size;
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        getHttpDataSourceFactory().setDefaultRequestProperties(ExoUtil.extractHeaders(mediaItem));
        String url = (mediaItem.localConfiguration != null) ? mediaItem.localConfiguration.uri.toString() : "";

        if (url.contains("googlevideo.com/api/manifest/dash")) {
            Log.d("ExoUtil", ">>> YT_STABLE_V37: DUAL-ANCHOR SYNC...");
            DefaultDashChunkSource.Factory chunkSourceFactory = new DefaultDashChunkSource.Factory(getDataSourceFactory(), 4);
            DashMediaSource.Factory factory = new DashMediaSource.Factory(chunkSourceFactory, getDataSourceFactory());
            
            factory.setManifestParser(new DashManifestParser() {
                private final Pattern idPattern = Pattern.compile("/id/([a-zA-Z0-9_-]{11})");

                private String getYouTubeId(Uri uri) {
                    Matcher matcher = idPattern.matcher(uri.toString());
                    return matcher.find() ? matcher.group(1) : uri.toString();
                }

                @Override
                @NonNull
                public DashManifest parse(@NonNull Uri uri, @NonNull InputStream inputStream) throws java.io.IOException {
                    DashManifest manifest = super.parse(uri, inputStream);
                    try {
                        if (manifest.getPeriodCount() < 1) return manifest;
                        String videoId = getYouTubeId(uri);
                        
                        synchronized (timelineT) {
                            if (!anchoredVideoId.equals(videoId)) {
                                anchoredVideoId = videoId;
                                anchoredAST = System.currentTimeMillis() - 60000;
                                anchoredPTO = -1; // 會在第一個 Rep 裡初始化
                                timelineT.clear(); timelineD.clear();
                                Log.d("ExoUtil", ">>> YT_STABLE_V37: NEW VIDEO ANCHOR AST: " + anchoredAST);
                            }
                        }

                        List<AdaptationSet> sets = new ArrayList<>();
                        Period firstPeriod = manifest.getPeriod(0);
                        for (AdaptationSet set : firstPeriod.adaptationSets) {
                            List<Representation> reps = new ArrayList<>();
                            for (Representation rep : set.representations) reps.add(solder(rep));
                            sets.add(new AdaptationSet(set.id, set.type, reps, set.accessibilityDescriptors, set.essentialProperties, set.supplementalProperties));
                        }

                        Period infinitePeriod = new Period("dual_anchor_track", 0, sets, firstPeriod.eventStreams);
                        return new DashManifest(anchoredAST, -1, 5000L, true, 5000L, 86400000L, 15000L, manifest.publishTimeMs, manifest.programInformation, manifest.utcTiming, manifest.serviceDescription, manifest.location, Collections.singletonList(infinitePeriod));
                    } catch (Exception e) {
                        return manifest;
                    }
                }

                private Representation solder(Representation rep) {
                    try {
                        Field baseField = findField(rep.getClass(), "segmentBase");
                        if (baseField == null) return rep;
                        baseField.setAccessible(true);
                        Object base = baseField.get(rep);
                        if (base == null) return rep;

                        Field tlField = findField(base.getClass(), "segmentTimeline");
                        if (tlField == null) return rep;
                        tlField.setAccessible(true);

                        @SuppressWarnings("unchecked")
                        List<SegmentBase.SegmentTimelineElement> update = (List<SegmentBase.SegmentTimelineElement>) tlField.get(base);
                        if (update == null || update.isEmpty()) return rep;

                        String itag = rep.format.id;
                        long manifestStartNum = getLongSafe(base, "startNumber", 1);

                        synchronized (timelineT) {
                            if (!timelineT.containsKey(itag)) timelineT.put(itag, new TreeMap<>());
                            if (!timelineD.containsKey(itag)) timelineD.put(itag, new TreeMap<>());

                            TreeMap<Long, Long> tMap = timelineT.get(itag);
                            TreeMap<Long, Long> dMap = timelineD.get(itag);
                            if (tMap == null || dMap == null) return rep;

                            // 🚀 初始化全局 PTO 錨點：確保所有 Rep 使用統一的零點
                            if (anchoredPTO == -1) {
                                anchoredPTO = 1000000L; // 設一個大的固定基數
                                Log.d("ExoUtil", ">>> YT_STABLE_V37: ANCHORED PTO SET: " + anchoredPTO);
                            }

                            // 對齊最後一個片段
                            long currentLastSq = manifestStartNum + update.size() - 1;
                            if (tMap.isEmpty()) {
                                // 將最後一個片段對齊到 LiveEdge (60s)
                                tMap.put(currentLastSq, anchoredPTO + 60000L);
                                dMap.put(currentLastSq, getLongSafe(update.get(update.size()-1), "duration", 5000));
                            }

                            for (int i = 0; i < update.size(); i++) {
                                long sq = manifestStartNum + i;
                                if (!tMap.containsKey(sq)) {
                                    Long refSq = tMap.lastKey();
                                    Long refT = tMap.get(refSq);
                                    Long refD = dMap.get(refSq);
                                    if (refT != null && refD != null) {
                                        tMap.put(sq, refT + refD * (sq - refSq));
                                        dMap.put(sq, getLongSafe(update.get(i), "duration", 5000));
                                    }
                                }
                            }

                            while (tMap.size() > 500) { Long fk = tMap.firstKey(); tMap.remove(fk); dMap.remove(fk); }

                            List<SegmentBase.SegmentTimelineElement> soldered = new ArrayList<>();
                            for (Long sq : tMap.keySet()) {
                                Long tv = tMap.get(sq); Long dv = dMap.get(sq);
                                if (tv != null && dv != null) soldered.add(new SegmentBase.SegmentTimelineElement(tv, dv));
                            }

                            // 未來填充 (12段/60s)
                            long lastSq = tMap.lastKey();
                            long lastT = tMap.get(lastSq);
                            long lastD = dMap.get(lastSq);
                            for (int j = 1; j <= 12; j++) {
                                soldered.add(new SegmentBase.SegmentTimelineElement(lastT + (lastD * j), lastD));
                            }

                            setFinalField(base, "segmentTimeline", soldered);
                            setFinalField(base, "startNumber", tMap.firstKey());
                            // 🚀 核心：PTO 絕對固定！不隨刷新改變。
                            setFinalField(base, "presentationTimeOffset", anchoredPTO);
                            setFinalField(base, "timescale", 1000L);
                            
                            Log.d("ExoUtil", ">>> YT_STABLE_V37: [" + itag + "] Window:" + tMap.firstKey() + "-" + tMap.lastKey() + " RelEnd:" + (lastT - anchoredPTO));
                        }
                    } catch (Exception ignored) {}
                    return rep;
                }

                private void setFinalField(Object obj, String fieldName, Object value) {
                    try {
                        Field f = findField(obj.getClass(), fieldName);
                        if (f == null) return;
                        f.setAccessible(true);
                        Field modifiersField = Field.class.getDeclaredField("accessFlags");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                        f.set(obj, value);
                    } catch (Exception ignored) {}
                }

                private long getLongSafe(Object obj, String fieldName, long def) {
                    try {
                        Field f = findField(obj.getClass(), fieldName);
                        if (f != null) { f.setAccessible(true); return f.getLong(obj); }
                    } catch (Exception ignored) {}
                    return def;
                }

                private Field findField(Class<?> startClass, String name) {
                    Class<?> current = startClass;
                    while (current != null) {
                        try { return current.getDeclaredField(name); } catch (Exception e) { current = current.getSuperclass(); }
                    }
                    return null;
                }
            });

            return factory.createMediaSource(mediaItem);
        }
        return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 20);
        return extractorsFactory;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) dataSourceFactory = () -> getCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory())).createDataSource();
        return dataSourceFactory;
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        return new CacheDataSource.Factory().setCache(getCache()).setUpstreamDataSourceFactory(upstreamFactory).setCacheWriteDataSinkFactory(null).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.client());
        return httpDataSourceFactory;
    }
}
