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
import com.fongmi.android.tv.bean.History;
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
import com.github.catvod.utils.Prefers;
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
    private Map<String, Site> homeMap;

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }

    public VodConfig() {
        this.ads = new ArrayList<>();
        this.parseAds = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.siteMap = new HashMap<>();
        this.homeMap = new HashMap<>();
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
        this.parse = null;
        this.ads.clear();
        this.parseAds.clear();
        this.doh.clear();
        this.rules.clear();
        this.siteMap.clear();
        this.homeMap.clear();
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
        android.util.Log.d("ScenarioTest", ">>> [Scanning Keys] JSON Top Level Keys: " + object.keySet());
        
        if (object.has("contexts")) {
            scenarios.addAll(Scenario.arrayFrom(object.getAsJsonArray("contexts").toString()));
        } 
        
        // 💡 檢查是否有透過 sites_xxx 定義但沒在 contexts 定義的場景
        List<String> existingIds = scenarios.stream().map(Scenario::getId).collect(Collectors.toList());
        boolean hasVod = existingIds.contains("vod");
        boolean addedOther = false;

        for (String key : object.keySet()) {
            if (key.startsWith("sites_")) {
                String id = key.substring(6);
                if (!existingIds.contains(id)) {
                    scenarios.add(new Scenario(id, id.toUpperCase()));
                    addedOther = true;
                    android.util.Log.d("ScenarioTest", ">>> Found implicit scenario: " + id);
                }
            }
        }

        // 💡 如果有其他場景但缺了 VOD，補上它
        if ((!scenarios.isEmpty() || addedOther) && !hasVod) {
            scenarios.add(0, new Scenario("vod", ResUtil.getString(R.string.home_vod)));
        }
        
        android.util.Log.d("ScenarioTest", ">>> Final Scenario Count: " + scenarios.size());
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
                android.util.Log.d("ScenarioTest", ">>> Loaded " + otherSites.size() + " sites for context: " + contextId);
            }
        }

        BaseLoader.get().parseJar(spider, true);
        AppDatabase.get().getSiteDao().clear();
        
        // 💡 修正同步污染：將那些沒有正確 context 的資料統一歸類為 vod
        App.execute(() -> {
            for (History h : AppDatabase.get().getHistoryDao().findAll()) {
                if (TextUtils.isEmpty(h.getContext())) h.save(false);
            }
        });

        // 同步所有場景的站點數據
        Map<String, Site> dbSites = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity(), (a, b) -> a));
        for (Map.Entry<String, List<Site>> entry : siteMap.entrySet()) {
            String ctx = entry.getKey();
            List<Site> list = entry.getValue();
            list.forEach(site -> site.sync(dbSites.get(site.getKey())));
            
            // 💡 獲取該場景的首頁站點
            String localKey = Prefers.getString("home_site_" + config.getId() + "_" + ctx, ctx.equals("vod") ? config.getHome() : "");
            Site home = list.stream().filter(item -> item.getKey().equals(localKey)).findFirst().orElse(list.isEmpty() ? new Site() : list.get(0));
            
            // 💡 如果是本地 Spider 設定覆蓋
            String globalLocal = Setting.getLocalSpider();
            if (ctx.equals("vod") && !globalLocal.isEmpty()) {
                home = list.stream().filter(item -> item.getApi().equals(globalLocal)).findFirst().orElse(home);
            }
            
            setHome(config, home, ctx, false);
        }
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

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getSite(String key) {
        // 💡 優先從當前場景找
        Site site = getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(null);
        if (site != null) return site;
        // 💡 找不到則掃描所有場景 (用於跨場景播放或跳轉)
        for (List<Site> list : siteMap.values()) {
            for (Site item : list) {
                if (item.getKey().equals(key)) return item;
            }
        }
        // 💡 真的找不到才返回空站點
        site = new Site();
        site.setKey(key);
        return site;
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean load = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        if (load) WallConfig.get().config(Config.find(wall, config.getName(), Config.WALL).update());
    }

    public Site getHome() {
        Site home = homeMap.get(context);
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, context, true);
        RefreshEvent.home();
    }

    public void setHome(String api) {
        if (api == null || api.isEmpty()) return;
        List<Site> sites = getSites();
        for (Site site : sites) {
            if (api.equals(site.getApi())) {
                setHome(site);
                return;
            }
        }
        // 💡 如果當前場景沒找到，但在 siteMap 的其他場景有，也允許設定（跨場景本地設定）
        for (List<Site> list : siteMap.values()) {
            for (Site site : list) {
                if (api.equals(site.getApi())) {
                    setHome(getConfig(), site, site.getContext(), true);
                    return;
                }
            }
        }
    }

    private void setHome(Config config, Site site, String context, boolean save) {
        homeMap.put(context, site);
        List<Site> sites = siteMap.get(context);
        if (sites != null) sites.forEach(item -> item.setSelected(item.equals(site)));
        if (save) {
            Prefers.put("home_site_" + config.getId() + "_" + context, site.getKey());
            if (context.equals("vod")) {
                config.setHome(site.getKey());
                config.save();
            }
        }
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
