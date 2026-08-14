package com.fongmi.android.tv.api.config;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Scenario;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();


    private List<Doh> doh;
    private List<Rule> rules;
    private Map<String, List<Site>> siteMap;
    private List<Scenario> scenarios;
    private List<Parse> parses;
    private List<String> flags;
    private List<String> ads;
    private List<String> parseAds;
    private boolean loadLive;
    private Parse parse;
    private String wall;
    private String context;
    private Site home;

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }

    public VodConfig() {
        this.ads = new ArrayList<>();
        this.parseAds = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.siteMap = new HashMap<>();
        this.scenarios = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.parses = new ArrayList<>();
        this.context = "vod";
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }


    public static void load(Config config, Callback callback) {
        get().config(config).load(callback);
    }

    public void cache() {
        try {
            if (config.getJson().isEmpty()) return;
            checkJson(config, Json.parse(config.getJson()).getAsJsonObject());
        } catch (Throwable ignored) {
        }
    }

    public VodConfig init() {
        this.siteMap.clear();
        this.siteMap.put("vod", new ArrayList<>());
        addLocal(this.siteMap.get("vod"));
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.ads.clear();
        this.parseAds.clear();
        this.doh.clear();
        this.rules.clear();
        this.siteMap.clear();
        this.scenarios.clear();
        this.flags.clear();
        this.parses.clear();
        this.loadLive = true;
        this.context = "vod";
        this.siteMap.put("vod", new ArrayList<>());
        addLocal(this.siteMap.get("vod"));
        BaseLoader.get().clear();
        return this;
    }

    private void addLocal(List<Site> items) {
        for (String key : com.github.catvod.spider.SpiderFactory.getKeys()) {
            com.github.catvod.crawler.Spider spider = com.github.catvod.spider.SpiderFactory.get(key);
            if (spider == null) continue;
            Site site = new Site();
            site.setKey(key + "_local");
            site.setName(spider.getName());
            site.setApi(key);
            site.setType(spider.getType());
            site.setSearchable(spider.getSearchable());
            site.setChangeable(spider.getChangeable());
            if (!items.contains(site)) items.add(0, site);
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        String url = UrlUtil.convert(config.getUrl());
        String json = Decoder.getJson(url, TAG);
        if (json.equals(config.getJson()) && isLoaded()) return;
        config.setJson(json);
        try {
            if (TextUtils.isEmpty(json)) throw new Exception("設定檔內容為空 (Empty)");
            JsonElement element = Json.parse(json);
            if (element == null || element.isJsonNull()) throw new Exception("設定檔不是有效的 JSON");
            if (!element.isJsonObject()) throw new Exception("設定檔頂層必須是物件 {} 而非數組 []");
            checkJson(config, element.getAsJsonObject());
        } catch (Exception e) {
            throw new Exception(ResUtil.getString(R.string.error_config_parse) + "\n" + e.getMessage());
        }
    }


    @Override
    public boolean isLoaded() {
        return getSites().stream().anyMatch(s -> !s.getKey().endsWith("_local"));
    }


    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initScenario(object);
        initSite(config, object);
        initParse(config, object);
        config.setLogo(Json.safeString(object, "logo"));
        config.setNotice(Json.safeString(object, "notice"));
    }

    private void initScenario(JsonObject object) {
        scenarios.clear();
        if (object.has("contexts")) {
            scenarios.addAll(Scenario.arrayFrom(object.getAsJsonArray("contexts").toString()));
        }
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        siteMap.clear();
        
        // 1. 解析預設場景 (sites)
        List<Site> vodSites = new ArrayList<>();
        if (object.has("sites")) {
            vodSites.addAll(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().toList());
        }
        addLocal(vodSites);
        siteMap.put("vod", vodSites);

        // 2. 解析擴展場景 (sites_xxx)
        for (String key : object.keySet()) {
            if (key.startsWith("sites_")) {
                String contextId = key.substring(6);
                List<Site> otherSites = Json.safeListElement(object, key).stream().map(e -> Site.objectFrom(e, spider)).distinct().toList();
                for (Site site : otherSites) site.setContext(contextId);
                siteMap.put(contextId, otherSites);
            }
        }

        BaseLoader.get().parseJar(spider, true);
        AppDatabase.get().getSiteDao().clear();
        
        // 同步所有場景的站點數據
        Map<String, Site> dbSites = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity(), (a, b) -> a));
        for (List<Site> list : siteMap.values()) {
            list.forEach(site -> site.sync(dbSites.get(site.getKey())));
        }

        String local = Setting.getLocalSpider();
        Site home = getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().get(0));
        if (!local.isEmpty()) home = getSites().stream().filter(item -> item.getApi().equals(local)).findFirst().orElse(home);
        setHome(config, home, false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        List<Site> items = siteMap.get(context);
        return items == null ? Collections.emptyList() : items;
    }

    public String getContext() {
        return context;
    }

    public List<Scenario> getScenarios() {
        return scenarios == null ? Collections.emptyList() : scenarios;
    }

    public Scenario getScenario() {
        return getScenarios().stream().filter(item -> item.getId().equals(context)).findFirst().orElse(new Scenario());
    }

    public void setContext(String context) {
        this.context = context;
        // 切換場景時，通常需要更新首頁站點為該場景的第一個站點
        if (!getSites().isEmpty()) {
            setHome(getSites().get(0));
        }
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    public void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }



    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }


    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }
    public List<String> getparseAds() {
        return parseAds == null ? Collections.emptyList() : parseAds;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public void setHome(String api) {
        if (api == null || api.isEmpty()) return;
        for (Site site : getSites()) {
            if (api.equals(site.getApi())) {
                home = site;
                break;
            }
        }
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean load = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        if (load) WallConfig.get().config(Config.find(wall, config.getName(), Config.WALL).update());
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        Site site = getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(null);
        if (site == null) {
            site = new Site();
            site.setKey(key);
            // 只有在真的有資料時才設定 API，否則保持為空以便 SiteApi 識別
        }
        return site;
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private void setGistU(String gistU) {
        //this.gistU = gistU;
        boolean load = !TextUtils.isEmpty(gistU);
        //if (load) WallConfig.get().config(Config.find(wall, config.getName(), 2).update());
        if (load) Setting.putGistUrl(gistU.trim());
    }

    private void setGistT(String gistT) {
        //this.gistT = gistT;
        boolean load = !TextUtils.isEmpty(gistT);
        //if (load) WallConfig.get().config(Config.find(wall, config.getName(), 2).update());
        if (load) Setting.putGistToken(gistT.trim());
    }

}
