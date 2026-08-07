package com.fongmi.android.tv.setting;

import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.utils.LanguageUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

public class Setting {

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 4;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getProxy() {
        return Prefers.getString("proxy");
    }

    public static void putProxy(String proxy) {
        Prefers.put("proxy", proxy);
    }

    public static String getKeep() {
        return Prefers.getString("keep");
    }

    public static void putKeep(String keep) {
        Prefers.put("keep", keep);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        return Math.min(Math.max(Prefers.getInt("wall", 1), MIN_WALL), MAX_WALL);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", Math.min(Math.max(wall, MIN_WALL), MAX_WALL));
    }

    public static int getWallType() {
        return Math.min(Math.max(Prefers.getInt("wall_type", 0), MIN_WALL_TYPE), MAX_WALL_TYPE);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", Math.min(Math.max(type, MIN_WALL_TYPE), MAX_WALL_TYPE));
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static int getReset() {
        return Prefers.getInt("reset", 1);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getPlayer() {
        return Prefers.getInt("player", PlayerSetting.ENGINE_EXO);
    }

    public static void putPlayer(int player) {
        Prefers.put("player", player);
    }

    public static int getLivePlayer() {
        return Prefers.getInt("player_live", getPlayer());
    }

    public static void putLivePlayer(int player) {
        Prefers.put("player_live", player);
    }

    public static int getDecode(int player) {
        return Prefers.getInt("decode_" + player, PlayerEngine.HARD);
    }

    public static void putDecode(int player, int decode) {
        Prefers.put("decode_" + player, decode);
    }

    public static int getRender() {
        return Prefers.getInt("render", 0);
    }

    public static void putRender(int render) {
        Prefers.put("render", render);
    }

    public static int getQuality() {
        return Prefers.getInt("quality", 2);
    }

    public static void putQuality(int quality) {
        Prefers.put("quality", quality);
    }

    public static int getSize() {
        return Prefers.getInt("size", 2);
    }

    public static void putSize(int size) {
        Prefers.put("size", size);
    }

    public static int getViewType(int viewType) {
        return Prefers.getInt("viewType", viewType);
    }

    public static void putViewType(int viewType) {
        Prefers.put("viewType", viewType);
    }

    public static int getScale() {
        return Prefers.getInt("scale");
    }

    public static void putScale(int scale) {
        Prefers.put("scale", scale);
    }

    public static int getLiveScale() {
        return Prefers.getInt("scale_live", getScale());
    }

    public static void putLiveScale(int scale) {
        Prefers.put("scale_live", scale);
    }

    public static int getHttp() {
        return Prefers.getInt("exo_http", 1);
    }

    public static void putHttp(int http) {
        Prefers.put("exo_http", http);
    }

    public static int getBuffer() {
        return Math.min(Math.max(Prefers.getInt("exo_buffer"), 1), 15);
    }

    public static void putBuffer(int buffer) {
        Prefers.put("exo_buffer", buffer);
    }

    public static int getFlag() {
        return Prefers.getInt("flag");
    }

    public static void putFlag(int flag) {
        Prefers.put("flag", flag);
    }

    public static int getEpisode() {
        return Prefers.getInt("episode");
    }

    public static void putEpisode(int episode) {
        Prefers.put("episode", episode);
    }

    public static int getBackground() {
        return Prefers.getInt("background", 0);
    }

    public static void putBackground(int background) {
        Prefers.put("background", background);
    }

    public static int getRtsp() {
        return Prefers.getInt("rtsp");
    }

    public static void putRtsp(int rtsp) {
        Prefers.put("rtsp", rtsp);
    }

    public static int getSiteMode() {
        return Math.min(Math.max(Prefers.getInt("site_mode", 1), MIN_SITE_MODE), MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.min(Math.max(mode, MIN_SITE_MODE), MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.min(Math.max(Prefers.getInt("sync_mode"), MIN_SYNC_MODE), MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.min(Math.max(mode, MIN_SYNC_MODE), MAX_SYNC_MODE));
    }

    public static boolean isBootLive() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBootLive(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isPlayWithOthers() {
        return Prefers.getBoolean("play_with_others", false);
    }

    public static void putPlayWithOthers(boolean play) {
        Prefers.put("play_with_others", play);
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("exo_tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("exo_tunnel", tunnel);
    }

    public static int getBackupMode() {
        return Prefers.getInt("backup_mode", 1);
    }

    public static void putBackupMode(int auto) {
        Prefers.put("backup_mode", auto);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleFractionalTextSize(float value) {
        Prefers.put("subtitle_text_size", Prefers.getFloat("subtitle_text_size") + value);
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitleBottomPadding() {
        return Prefers.getFloat("subtitle_bottom_padding");
    }

    public static void putSubtitleBottomPadding(float value) {
        Prefers.put("subtitle_bottom_padding", value);
    }

    public static float getThumbnail() {
        return 0.3f * getQuality() + 0.4f;
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isDisplayTime() {
        return Prefers.getBoolean("display_time", false);
    }

    public static void putDisplayTime(boolean display) {
        Prefers.put("display_time", display);
    }

    public static boolean isDisplaySpeed() {
        return Prefers.getBoolean("display_speed", false);
    }

    public static void putDisplaySpeed(boolean display) {
        Prefers.put("display_speed", display);
    }

    public static boolean isDisplayDuration() {
        return Prefers.getBoolean("display_duration", false);
    }

    public static void putDisplayDuration(boolean display) {
        Prefers.put("display_duration", display);
    }

    public static boolean isDisplayMiniProgress() {
        return Prefers.getBoolean("display_mini_progress", false);
    }

    public static void putDisplayMiniProgress(boolean display) {
        Prefers.put("display_mini_progress", display);
    }

    public static boolean isDisplayVideoTitle() {
        return Prefers.getBoolean("display_video_title", false);
    }

    public static void putDisplayVideoTitle(boolean display) {
        Prefers.put("display_video_title", display);
    }

    public static float getPlaySpeed() {
        return Prefers.getFloat("play_speed", 1.0f);
    }

    public static void putPlaySpeed(float speed) {
        Prefers.put("play_speed", speed);
    }

    public static void putFullscreenMenuKey(int key) {
        Prefers.put("fullscreen_menu_key", key);
    }

    public static int getFullscreenMenuKey() {
        return Prefers.getInt("fullscreen_menu_key", 0);
    }

    public static void putHomeMenuKey(int key) {
        Prefers.put("home_menu_key", key);
    }

    public static int getHomeMenuKey() {
        return Prefers.getInt("home_menu_key", 0);
    }

    public static boolean isHomeSiteLock() {
        return Prefers.getBoolean("home_site_lock", false);
    }

    public static void putHomeSiteLock(boolean lock) {
        Prefers.put("home_site_lock", lock);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static void putSmallWindowBackKey(int key) {
        Prefers.put("small_window_back_key", key);
    }

    public static int getSmallWindowBackKey() {
        return Prefers.getInt("small_window_back_key", 0);
    }

    public static void putHomeDisplayName(boolean change) {
        Prefers.put("home_display_name", change);
    }

    public static boolean isHomeDisplayName() {
        return Prefers.getBoolean("home_display_name", false);
    }

    public static boolean isAggregatedSearch() {
        return Prefers.getBoolean("aggregated_search", false);
    }

    public static void putAggregatedSearch(boolean search) {
        Prefers.put("aggregated_search", search);
    }

    public static void putHomeUI(int key) {
        Prefers.put("home_ui", key);
    }

    public static int getHomeUI() {
        return Prefers.getInt("home_ui", 1);
    }

    public static void putHomeButtons(String buttons) {
        Prefers.put("home_buttons", buttons);
    }

    public static String getHomeButtons(String defaultValue) {
        return Prefers.getString("home_buttons", defaultValue);
    }

    public static void putHomeButtonsSorted(String buttons) {
        Prefers.put("home_buttons_sorted", buttons);
    }

    public static String getHomeButtonsSorted(String defaultValue) {
        return Prefers.getString("home_buttons_sorted", defaultValue);
    }

    public static boolean isHomeHistory() {
        return Prefers.getBoolean("home_history", true);
    }

    public static void putHomeHistory(boolean show) {
        Prefers.put("home_history", show);
    }

    public static void putConfigCache(int key) {
        Prefers.put("config_cache", key);
    }

    public static int getConfigCache() {
        return Math.min(Prefers.getInt("config_cache", 0), 2);
    }

    public static void putLanguage(int key) {
        Prefers.put("language", key);
    }

    public static int getLanguage() {
        return Prefers.getInt("language", LanguageUtil.locale());
    }

    public static void putParseWebView(int key) {
        Prefers.put("parse_webview", key);
    }

    public static int getParseWebView() {
        return Prefers.getInt("parse_webview", 0);
    }

    public static boolean isSiteSearch() {
        return Prefers.getBoolean("site_search", false);
    }

    public static void putSiteSearch(boolean search) {
        Prefers.put("site_search", search);
    }

    public static boolean isRemoveAd() {
        return Prefers.getBoolean("remove_ad", true);
    }

    public static void putRemoveAd(boolean remove) {
        Prefers.put("remove_ad", remove);
    }

    public static String getThunderCacheDir() {
        return Prefers.getString("thunder_cache_dir", "");
    }

    public static void putThunderCacheDir(String dir) {
        Prefers.put("thunder_cache_dir", dir);
    }

    public static void putUseFtp(boolean use) {
        Prefers.put("syncUseFtp", use);
    }

    public static boolean isUseFtp() {
        return Prefers.getBoolean("syncUseFtp");
    }

    public static void putFtpUri(String uri) {
        Prefers.put("ftpUri", uri);
    }

    public static String getFtpUri() {
        return Prefers.getString("ftpUri");
    }

    public static void putFtpUsername(String username) {
        Prefers.put("ftpUsername", username);
    }

    public static String getFtpUsername() {
        return Prefers.getString("ftpUsername");
    }

    public static void putFtpPassword(String password) {
        Prefers.put("ftpPassword", password);
    }

    public static String getFtpPassword() {
        return Prefers.getString("ftpPassword");
    }

    public static void putUseGist(boolean use) {
        Prefers.put("syncUseGist", use);
    }

    public static boolean isUseGist() {
        return Prefers.getBoolean("syncUseGist");
    }

    public static void putGistUrl(String uri) {
        Prefers.put("syncGistUrl", uri);
    }

    public static String getGistUrl() {
        return Prefers.getString("syncGistUrl");
    }

    public static void putGistToken(String token) {
        Prefers.put("syncGistToken", token);
    }

    public static String getGistToken() {
        return Prefers.getString("syncGistToken");
    }

    public static boolean isDlna() {
        return Prefers.getBoolean("dlna", true);
    }

    public static void putDlna(boolean dlna) {
        Prefers.put("dlna", dlna);
    }

    public static boolean isNormalize() {
        return Prefers.getBoolean("normalize", true);
    }

    public static void putNormalize(boolean normalize) {
        Prefers.put("normalize", normalize);
    }

    public static String getLocalSpider() {
        return Prefers.getString("local_spider", "");
    }

    public static void putLocalSpider(String spider) {
        Prefers.put("local_spider", spider);
    }

    public static boolean isCategoryCache() {
        return Prefers.getBoolean("category_cache", true);
    }

    public static void putCategoryCache(boolean cache) {
        Prefers.put("category_cache", cache);
    }
}
