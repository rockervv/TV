package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
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
import androidx.media3.exoplayer.dash.manifest.RangedUri;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.dash.manifest.SegmentBase;
import androidx.media3.exoplayer.dash.manifest.UrlTemplate;
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

import java.io.File;
import java.io.InputStream;
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

    private static long anchoredAst = -1;
    private static String anchoredVideoId = "";
    private static final Map<String, TreeMap<Long, Long>> timelineT = new HashMap<>();
    private static final Map<String, TreeMap<Long, Long>> timelineD = new HashMap<>();
    private static final Map<String, Long> itagPto = new HashMap<>();

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
            Log.d("ExoUtil", ">>> YT_STABLE_V23: IN-PLACE MUTATION...");
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
                                anchoredAst = manifest.availabilityStartTimeMs;
                                anchoredVideoId = videoId;
                                timelineT.clear(); timelineD.clear(); itagPto.clear();
                                Log.d("ExoUtil", ">>> YT_STABLE_V23: ANCHOR " + videoId + " AST:" + anchoredAst);
                            }
                        }

                        for (int i = 0; i < manifest.getPeriodCount(); i++) {
                            Period period = manifest.getPeriod(i);
                            for (AdaptationSet set : period.adaptationSets) {
                                for (Representation rep : set.representations) {
                                    mutateInPlace(rep, manifest.availabilityStartTimeMs, period.startMs);
                                }
                            }
                        }

                        return manifest;
                    } catch (Exception e) {
                        Log.e("ExoUtil", ">>> YT_STABLE_V23: MUTATE FAIL", e);
                        return manifest;
                    }
                }

                private void mutateInPlace(Representation rep, long currentAst, long periodStartMs) {
                    try {
                        Field baseField = findField(rep.getClass(), "segmentBase");
                        if (baseField == null) return;
                        baseField.setAccessible(true);
                        Object base = baseField.get(rep);
                        if (base == null) return;

                        Field tlField = findField(base.getClass(), "segmentTimeline");
                        if (tlField == null) return;
                        tlField.setAccessible(true);

                        @SuppressWarnings("unchecked")
                        List<SegmentBase.SegmentTimelineElement> update = (List<SegmentBase.SegmentTimelineElement>) tlField.get(base);
                        if (update == null || update.isEmpty()) return;

                        long timescale = getLongSafe(base, "timescale", 1000);
                        long manifestPto = getLongSafe(base, "presentationTimeOffset", 0);
                        long startNumber = getLongSafe(base, "startNumber", 1);
                        String itag = rep.format.id;

                        synchronized (timelineT) {
                            if (!timelineT.containsKey(itag)) timelineT.put(itag, new TreeMap<>());
                            if (!timelineD.containsKey(itag)) timelineD.put(itag, new TreeMap<>());
                            if (!itagPto.containsKey(itag)) itagPto.put(itag, manifestPto);

                            TreeMap<Long, Long> tMap = timelineT.get(itag);
                            TreeMap<Long, Long> dMap = timelineD.get(itag);
                            Long fixedPto = itagPto.get(itag);
                            if (tMap == null || dMap == null || fixedPto == null) return;

                            long driftOffset = ((currentAst - anchoredAst + periodStartMs) * timescale) / 1000;

                            for (int i = 0; i < update.size(); i++) {
                                long sq = startNumber + i;
                                if (!tMap.containsKey(sq)) {
                                    SegmentBase.SegmentTimelineElement el = update.get(i);
                                    long d = getLongSafe(el, "duration", 5000);
                                    long t;
                                    if (!tMap.isEmpty()) {
                                        long lastSq = tMap.lastKey();
                                        t = tMap.get(lastSq) + (dMap.get(lastSq) * (sq - lastSq));
                                    } else {
                                        t = (getLongSafe(el, "startTime", 0) - manifestPto + fixedPto) + driftOffset;
                                    }
                                    tMap.put(sq, t); dMap.put(sq, d);
                                }
                            }

                            while (tMap.size() > 1000) { Long fk = tMap.firstKey(); tMap.remove(fk); dMap.remove(fk); }

                            // 🚀 暴力修改現有對象
                            List<SegmentBase.SegmentTimelineElement> solderedTimeline = new ArrayList<>();
                            for (Long sq : tMap.keySet()) {
                                solderedTimeline.add(new SegmentBase.SegmentTimelineElement(tMap.get(sq), dMap.get(sq)));
                            }

                            // 強行塞回原對象
                            setFinalField(base, "segmentTimeline", solderedTimeline);
                            setFinalField(base, "startNumber", tMap.firstKey());
                            setFinalField(base, "presentationTimeOffset", fixedPto);

                            Log.d("ExoUtil", ">>> YT_STABLE_V23: [" + itag + "] SOLDERED SQ: " + tMap.firstKey() + "-" + tMap.lastKey());
                        }
                    } catch (Exception e) {
                        Log.e("ExoUtil", ">>> YT_STABLE_V23: MUTATE CRASH", e);
                    }
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

                private long getLongSafe(Object obj, String fieldName, long defValue) {
                    try {
                        Field f = findField(obj.getClass(), fieldName);
                        if (f != null) { f.setAccessible(true); return f.getLong(obj); }
                    } catch (Exception ignored) {}
                    return defValue;
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
