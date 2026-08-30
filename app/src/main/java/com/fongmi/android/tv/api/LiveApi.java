package com.fongmi.android.tv.api;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        return item.getEpgXml().stream().map(url -> startXml(item, url)).reduce(false, Boolean::logicalOr);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        for (int offset : new int[]{-1, 0, 1}) fetchEpgDay(item, zoneId, offset);
        return item.getDataList().stream().filter(epg -> epg.equal(today)).findFirst().orElseGet(Epg::new).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = getUrl(item);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        try {
            String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
            String url = item.getEpg().replace("{date}", date);
            boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
            if (!need) return;
            try (okhttp3.Response response = OkHttp.newCall(url).execute()) {
                byte[] bytes = response.body().bytes();
                if (bytes.length == 0) return;
                String content = bytesToContent(bytes);
                Epg epg = Epg.objectFrom(content, item.getTvgId(), zoneId);
                if (epg.getList().isEmpty()) epg = EpgParser.getEpg(content, item.getTvgName(), zoneId);
                if (epg.getList().isEmpty()) epg = EpgParser.getEpg(content, item.getName(), zoneId);
                item.setData(epg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String bytesToContent(byte[] bytes) throws Exception {
        if (bytes.length < 2) return new String(bytes);
        if ((bytes[0] & 0xFF | (bytes[1] & 0xFF) << 8) == 0x8B1F) {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
            java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bis);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) bos.write(buffer, 0, len);
            return bos.toString();
        }
        return new String(bytes);
    }
}
