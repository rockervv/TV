package com.fongmi.android.tv.player.exo;

import static androidx.media3.datasource.DataSource.*;

import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceInputStream;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.ADFilter;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;

public class MediaSourceFactory implements MediaSource.Factory {

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private HttpDataSource.Factory httpDataSourceFactory;
    private Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;

    private static M3U8ParseListener parseListener;

    public MediaSourceFactory() {
        parseListener = null;
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory());
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        MediaItem item = setHeader(mediaItem);
        String url = item.localConfiguration.uri.toString();

        if (url.endsWith(".m3u8") && true == false) {
            return createCustomHlsMediaSource(item);
        } else if (mediaItem.mediaId.contains("***") && mediaItem.mediaId.contains("|||")) {
            return createConcatenatingMediaSource(item);
        } else {
            return defaultMediaSourceFactory.createMediaSource(item);
        }
    }


    private MediaSource createCustomHlsMediaSource(MediaItem mediaItem) {
        Uri uri = mediaItem.localConfiguration.uri;
        DataSpec dataSpec = new DataSpec(uri);

        try {

            DataSource dataSource = getDataSourceFactory().createDataSource();
            DataSourceInputStream input = new DataSourceInputStream(dataSource, dataSpec);
            input.open();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            //M3U8AdFilterResult result = parseAndFilterM3U8(reader);
            String filtered = ADFilter.Process(reader);

            //Log.d("MediaSourceFactory", "Filtered out " + result.adSegmentCount + " ad sections, total " + result.adDurationSeconds + " seconds");
            //notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds);


            // 將過濾後的 m3u8 字串寫入記憶體 Uri (或暫存檔)，這邊我們用 data URI 播放
            Uri newUri = Uri.parse("data:application/vnd.apple.mpegurl;base64," + Base64.encodeToString(filtered.getBytes(), Base64.NO_WRAP));
            MediaItem newItem = mediaItem.buildUpon().setUri(newUri).build();

            return defaultMediaSourceFactory.createMediaSource(newItem);
            // 如果只是單一路徑，就用單一 media source
            //if (playlistUrls.size() == 1) {
            //    return defaultMediaSourceFactory.createMediaSource(
            //            mediaItem.buildUpon().setUri(playlistUrls.get(0)).build()
            //    );
            //}

            // 否則用 ConcatenatingMediaSource 播放多段
            //ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
            //for (Uri streamUri : playlistUrls) {
            //    MediaItem item = mediaItem.buildUpon().setUri(streamUri).build();
            //    builder.add(defaultMediaSourceFactory.createMediaSource(item));
            //}
            //return builder.build();

        } catch (Exception e) {
            e.printStackTrace();
            // fallback to default
            return defaultMediaSourceFactory.createMediaSource(mediaItem);
        }
    }

    private String resolveUri(String baseUri, String path) {
        try {
            URL base = new URL(baseUri);
            URL resolved = new URL(base, path);
            return resolved.toString();
        } catch (Exception e) {
            return path; // fallback
        }
    }

    private MediaItem setHeader(MediaItem mediaItem) {
        Map<String, String> headers = new HashMap<>();
        for (String key : mediaItem.requestMetadata.extras.keySet())
            headers.put(key, mediaItem.requestMetadata.extras.get(key).toString());
        getHttpDataSourceFactory().setDefaultRequestProperties(headers);
        return mediaItem;
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : mediaItem.mediaId.split("\\*\\*\\*")) {
            String[] info = split.split("\\|\\|\\|");
            if (info.length >= 2)
                builder.add(defaultMediaSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(Uri.parse(info[0])).build()), Long.parseLong(info[1]));
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null)
            extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3);
        return extractorsFactory;
    }

    private Factory getDataSourceFactory() {
        if (dataSourceFactory == null)
            dataSourceFactory = buildReadOnlyCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()));
        return dataSourceFactory;
    }

    private CacheDataSource.Factory buildReadOnlyCacheDataSource(Factory upstreamFactory) {
        return new CacheDataSource.Factory().setCache(CacheManager.get().getCache()).setUpstreamDataSourceFactory(upstreamFactory).setCacheWriteDataSinkFactory(null).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null)
            httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.client());
        return httpDataSourceFactory;
    }


    private static class M3U8AdFilterResult {
        public final String filteredContent;
        public final int adSegmentCount;
        public final double adDurationSeconds;

        public M3U8AdFilterResult(String filteredContent, int adSegmentCount, double adDurationSeconds) {
            this.filteredContent = filteredContent;
            this.adSegmentCount = adSegmentCount;
            this.adDurationSeconds = adDurationSeconds;
        }
    }

    private M3U8AdFilterResult parseAndFilterM3U8(BufferedReader reader) {

        StringBuilder output = new StringBuilder();
        String line;
        boolean inAd = false;
        boolean seenFirstDiscontinuity = false;
        double adDuration = 0.0;
        int adCount = 0;

        double currentDuration = 0.0;
        try {

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("#EXTINF:")) {
                    // Parse duration from EXTINF
                    try {
                        currentDuration = Double.parseDouble(line.substring(8).split(",")[0]);
                    } catch (NumberFormatException e) {
                        currentDuration = 0.0;
                    }

                    if (!inAd) {
                        output.append(line).append("\n");
                    }

                } else if (line.equals("#EXT-X-DISCONTINUITY")) {
                    if (!seenFirstDiscontinuity) {
                        seenFirstDiscontinuity = true;
                        output.append(line).append("\n");
                    } else {
                        // Start or end of ad
                        if (!inAd) {
                            inAd = true;
                            adCount++;
                        } else {
                            inAd = false;
                        }
                    }

                } else if (line.isEmpty() || line.startsWith("#")) {
                    if (!inAd) output.append(line).append("\n");
                } else {
                    // URI of a segment
                    if (!inAd) {
                        output.append(line).append("\n");
                    } else {
                        adDuration += currentDuration;
                        currentDuration = 0.0;
                    }
                }
            }
        } catch (IOException e){
            Log.e("M3U8Parser", "IOException while reading playlist: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e("M3U8Parser", "IOException while closing reader: " + e.getMessage(), e);
                }
            }
        }



        return new M3U8AdFilterResult(output.toString(), adCount, adDuration);
    }

    //private static M3U8ParseListener parseListener;
    public interface M3U8ParseListener {
        void onAdSegmentsFiltered(int adCount, double adSeconds);
    }

    public static void setM3U8ParseListener(M3U8ParseListener listener) {
        parseListener = listener;
    }
    private void notifyAdSegmentsFiltered(int adCount, double adSeconds) {
        if (parseListener != null) {
            parseListener.onAdSegmentsFiltered(adCount, adSeconds);
        }
    }

}