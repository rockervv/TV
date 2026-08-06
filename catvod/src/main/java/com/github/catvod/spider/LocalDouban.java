package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.*;

public class LocalDouban extends Spider {

    private final String siteUrl = "https://frodo.douban.com/api/v2";
    private final String apikey = "?apikey=0ac44ae016490db2204ce0a042db2916";
    private String extend;

    @Override
    public String getName() {
        return "寶盒 (APP)";
    }

    @Override
    public int getSearchable() {
        return 0;
    }

    @Override
    public int getChangeable() {
        return 1;
    }

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("Host", "frodo.douban.com");
        header.put("Connection", "Keep-Alive");
        header.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        try {
            this.extend = extend;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        try {
            List<Class> classes = new ArrayList<>();
            List<String> typeIds = Arrays.asList("hot_gaia", "tv_hot",   "movie", "tv", "rank_list_movie", "rank_list_tv", "show_hot");
            List<String> typeNames = Arrays.asList("熱門電影", "熱播劇集", "電影篩選", "電視篩選", "電影榜單", "電視劇榜單",  "熱播綜藝");
            for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));
            
            String recommendUrl = "http://api.douban.com/api/v2/subject_collection/subject_real_time_hotest/items" + apikey;
            String json = OkHttp.string(recommendUrl, getHeader());
            if (TextUtils.isEmpty(json)) return Result.string(classes, new ArrayList<>(), getFilterData());
            JSONObject jsonObject = new JSONObject(json);
            JSONArray items = jsonObject.optJSONArray("subject_collection_items");
            
            return Result.string(classes, parseVodListFromJSONArray(items), getFilterData());
        } catch (Exception e) {
            if (e instanceof java.io.InterruptedIOException) return "";
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            String sort = extend.get("sort") == null ? "T" : extend.get("sort");
            String tags = getTags(extend);
            int start = (Integer.parseInt(pg) - 1) * 20;
            String cateUrl;
            String itemKey = "items";

            switch (tid) {
                case "hot_gaia":
                    sort = extend.get("sort") == null ? "recommend" : extend.get("sort");
                    String area = extend.get("area") == null ? "全部" : extend.get("area");
                    sort = sort + "&area=" + URLEncoder.encode(area);
                    cateUrl = siteUrl + "/movie/hot_gaia" + apikey + "&sort=" + sort + "&start=" + start + "&count=20";
                    break;
                case "tv_hot":
                case "show_hot":
                case "rank_list_movie":
                case "rank_list_tv":
                    String collectionType = tid;
                    if (tid.equals("tv_hot")) collectionType = extend.get("type") == null ? "tv_hot" : extend.get("type");
                    if (tid.equals("show_hot")) collectionType = extend.get("type") == null ? "show_hot" : extend.get("type");
                    if (tid.contains("rank_list")) collectionType = extend.get("榜单") == null ? (tid.contains("movie") ? "movie_real_time_hotest" : "tv_real_time_hotest") : extend.get("榜单");
                    cateUrl = siteUrl + "/subject_collection/" + collectionType + "/items" + apikey + "&start=" + start + "&count=20";
                    itemKey = "subject_collection_items";
                    break;
                case "anime_hot":
                    String animeTags = TextUtils.isEmpty(tags) ? "動畫" : "動畫," + tags;
                    cateUrl = siteUrl + "/tv/recommend" + apikey + "&sort=" + sort + "&tags=" + URLEncoder.encode(animeTags) + "&start=" + start + "&count=20";
                    break;
                case "tv":
                    cateUrl = siteUrl + "/tv/recommend" + apikey + "&sort=" + sort + "&tags=" + URLEncoder.encode(tags) + "&start=" + start + "&count=20";
                    break;
                default:
                    cateUrl = siteUrl + "/movie/recommend" + apikey + "&sort=" + sort + "&tags=" + URLEncoder.encode(tags) + "&start=" + start + "&count=20";
                    break;
            }

            String json = OkHttp.string(cateUrl, getHeader());
            if (TextUtils.isEmpty(json)) return Result.get().vod(new ArrayList<>()).page(Integer.parseInt(pg), 0, 20, 0).string();
            JSONObject object = new JSONObject(json);
            JSONArray array = object.optJSONArray(itemKey);
            List<Vod> list = parseVodListFromJSONArray(array);
            int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 20, total = Integer.MAX_VALUE;
            return Result.get().vod(list).page(page, count, limit, total).string();
        } catch (Exception e) {
            SpiderDebug.log(e);
            return Result.error(e.getMessage());
        }
    }

    private List<Vod> parseVodListFromJSONArray(JSONArray items) throws Exception {
        List<Vod> list = new ArrayList<>();
        try {
            if (items == null) return list;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String pic = getPic(item);
                if (TextUtils.isEmpty(pic)) continue;

                String vodId = "msearch:" + item.optString("id");
                String name = item.optString("title");
                String remark = getRating(item);
                list.add(new Vod(vodId, name, pic, remark));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return list;
    }

    private String getRating(JSONObject item) {
        try {
            JSONObject rating = item.optJSONObject("rating");
            return rating == null ? "" : "評分：" + rating.optString("value");
        } catch (Exception e) {
            return "";
        }
    }

    private String getPic(JSONObject item) {
        try {
            JSONObject pic = item.optJSONObject("pic");
            if (pic == null) return "";
            String url = pic.optString("normal");
            if (TextUtils.isEmpty(url)) return "";
            return url + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
        } catch (Exception e) {
            return "";
        }
    }

    private String getTags(HashMap<String, String> extend) {
        try {
            StringBuilder tags = new StringBuilder();
            for (String key : extend.keySet()) {
                if (key.equals("sort") || key.equals("type") || key.equals("榜单")) continue;
                String value = extend.get(key);
                if (!TextUtils.isEmpty(value)) tags.append(value).append(",");
            }
            return Util.substring(tags.toString());
        } catch (Exception e) {
            return "";
        }
    }

    private JSONObject getFilterData() {
        try {
            return new JSONObject("{\"hot_gaia\":[{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"熱度\",\"v\":\"recommend\"},{\"n\":\"最新\",\"v\":\"time\"},{\"n\":\"評分\",\"v\":\"rank\"}]},{\"key\":\"area\",\"name\":\"地區\",\"value\":[{\"n\":\"全部\",\"v\":\"全部\"},{\"n\":\"華語\",\"v\":\"華語\"},{\"n\":\"歐美\",\"v\":\"歐美\"},{\"n\":\"韓國\",\"v\":\"韓國\"},{\"n\":\"日本\",\"v\":\"日本\"}]}],\"tv_hot\":[{\"key\":\"type\",\"name\":\"分類\",\"value\":[{\"n\":\"綜合\",\"v\":\"tv_hot\"},{\"n\":\"國產劇\",\"v\":\"tv_domestic\"},{\"n\":\"歐美劇\",\"v\":\"tv_american\"},{\"n\":\"日劇\",\"v\":\"tv_japanese\"},{\"n\":\"韓劇\",\"v\":\"tv_korean\"},{\"n\":\"動畫\",\"v\":\"tv_animation\"}]}],\"anime_hot\":[{\"key\":\"類型\",\"name\":\"類型\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"熱血\",\"v\":\"熱血\"},{\"n\":\"搞笑\",\"v\":\"搞笑\"},{\"n\":\"戀愛\",\"v\":\"戀愛\"},{\"n\":\"校園\",\"v\":\"校園\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},{\"n\":\"懸疑\",\"v\":\"懸疑\"},{\"n\":\"治癒\",\"v\":\"治癒\"},{\"n\":\"運動\",\"v\":\"運動\"},{\"n\":\"機甲\",\"v\":\"機甲\"},{\"n\":\"少女\",\"v\":\"少女\"},{\"n\":\"少年\",\"v\":\"少年\"}]},{\"key\":\"地區\",\"name\":\"地區\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"中國大陸\",\"v\":\"中國大陸\"},{\"n\":\"美國\",\"v\":\"美國\"},{\"n\":\"韓國\",\"v\":\"韓國\"},{\"n\":\"英國\",\"v\":\"英國\"},{\"n\":\"法國\",\"v\":\"法國\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"近期熱度\",\"v\":\"T\"},{\"n\":\"首播時間\",\"v\":\"R\"},{\"n\":\"高分優先\",\"v\":\"S\"}]},{\"key\":\"年代\",\"name\":\"年代\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2010年代\",\"v\":\"2010年代\"},{\"n\":\"2000年代\",\"v\":\"2000年代\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"更早\",\"v\":\"更早\"}]}],\"show_hot\":[{\"key\":\"type\",\"name\":\"分類\",\"value\":[{\"n\":\"綜合\",\"v\":\"show_hot\"},{\"n\":\"國內\",\"v\":\"show_domestic\"},{\"n\":\"國外\",\"v\":\"show_foreign\"}]}],\"movie\":[{\"key\":\"類型\",\"name\":\"類型\",\"value\":[{\"n\":\"全部類型\",\"v\":\"\"},{\"n\":\"喜劇\",\"v\":\"喜劇\"},{\"n\":\"愛情\",\"v\":\"愛情\"},{\"n\":\"動作\",\"v\":\"動作\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"動畫\",\"v\":\"動畫\"},{\"n\":\"懸疑\",\"v\":\"懸疑\"},{\"n\":\"犯罪\",\"v\":\"犯罪\"},{\"n\":\"驚悚\",\"v\":\"驚悚\"},{\"n\":\"冒險\",\"v\":\"冒險\"},{\"n\":\"音樂\",\"v\":\"音樂\"},{\"n\":\"歷史\",\"v\":\"歷史\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"戰爭\",\"v\":\"戰爭\"},{\"n\":\"傳記\",\"v\":\"傳記\"},{\"n\":\"歌舞\",\"v\":\"歌舞\"},{\"n\":\"武俠\",\"v\":\"武俠\"},{\"n\":\"情色\",\"v\":\"情色\"},{\"n\":\"災難\",\"v\":\"災難\"},{\"n\":\"西部\",\"v\":\"西部\"},{\"n\":\"紀錄片\",\"v\":\"紀錄片\"},{\"n\":\"短片\",\"v\":\"短片\"}]},{\"key\":\"地區\",\"name\":\"地區\",\"value\":[{\"n\":\"全部地區\",\"v\":\"\"},{\"n\":\"華語\",\"v\":\"華語\"},{\"n\":\"歐美\",\"v\":\"歐美\"},{\"n\":\"中國\",\"v\":\"中國\"},{\"n\":\"美國\",\"v\":\"美國\"},{\"n\":\"中國香港\",\"v\":\"中國香港\"},{\"n\":\"中國台灣\",\"v\":\"中國台灣\"},{\"n\":\"韓國\",\"v\":\"韓國\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"英國\",\"v\":\"英國\"},{\"n\":\"法國\",\"v\":\"法國\"},{\"n\":\"菲律賓\",\"v\":\"菲律賓\"},{\"n\":\"德國\",\"v\":\"德國\"},{\"n\":\"意大利\",\"v\":\"意大利\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"泰國\",\"v\":\"泰國\"},{\"n\":\"俄羅斯\",\"v\":\"俄羅斯\"},{\"n\":\"加拿大\",\"v\":\"加拿大\"},{\"n\":\"澳大利亞\",\"v\":\"澳大利亞\"},{\"n\":\"愛爾蘭\",\"v\":\"愛爾蘭\"},{\"n\":\"瑞典\",\"v\":\"瑞典\"},{\"n\":\"巴西\",\"v\":\"巴西\"},{\"n\":\"丹麥\",\"v\":\"丹麥\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"近期熱度\",\"v\":\"T\"},{\"n\":\"首映時間\",\"v\":\"R\"},{\"n\":\"高分優先\",\"v\":\"S\"}]},{\"key\":\"年代\",\"name\":\"年代\",\"value\":[{\"n\":\"全部年代\",\"v\":\"\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2010年代\",\"v\":\"2010年代\"},{\"n\":\"2000年代\",\"v\":\"2000年代\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"},{\"n\":\"70年代\",\"v\":\"70年代\"},{\"n\":\"60年代\",\"v\":\"60年代\"},{\"n\":\"更早\",\"v\":\"更早\"}]}],\"tv\":[{\"key\":\"類型\",\"name\":\"類型\",\"value\":[{\"n\":\"不限\",\"v\":\"\"},{\"n\":\"電視劇\",\"v\":\"電視劇\"},{\"n\":\"綜藝\",\"v\":\"綜藝\"}]},{\"key\":\"電視劇形式\",\"name\":\"電視劇形式\",\"value\":[{\"n\":\"不限\",\"v\":\"\"},{\"n\":\"喜劇\",\"v\":\"喜劇\"},{\"n\":\"愛情\",\"v\":\"愛情\"},{\"n\":\"懸疑\",\"v\":\"懸疑\"},{\"n\":\"動畫\",\"v\":\"動畫\"},{\"n\":\"武俠\",\"v\":\"武俠\"},{\"n\":\"古裝\",\"v\":\"古裝\"},{\"n\":\"家庭\",\"v\":\"家庭\"},{\"n\":\"犯罪\",\"v\":\"犯罪\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"歷史\",\"v\":\"歷史\"},{\"n\":\"戰爭\",\"v\":\"戰爭\"},{\"n\":\"動作\",\"v\":\"動作\"},{\"n\":\"冒險\",\"v\":\"冒險\"},{\"n\":\"傳記\",\"v\":\"傳記\"},{\"n\":\"劇情\",\"v\":\"劇情\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},{\"n\":\"驚悚\",\"v\":\"驚悚\"},{\"n\":\"災難\",\"v\":\"災難\"},{\"n\":\"歌舞\",\"v\":\"歌舞\"},{\"n\":\"音樂\",\"v\":\"音樂\"}]},{\"key\":\"綜藝形式\",\"name\":\"綜藝形式\",\"value\":[{\"n\":\"不限\",\"v\":\"\"},{\"n\":\"真人秀\",\"v\":\"真人秀\"},{\"n\":\"脫口秀\",\"v\":\"脫口秀\"},{\"n\":\"音樂\",\"v\":\"音樂\"},{\"n\":\"歌舞\",\"v\":\"歌舞\"}]},{\"key\":\"地區\",\"name\":\"地區\",\"value\":[{\"n\":\"全部地區\",\"v\":\"\"},{\"n\":\"華語\",\"v\":\"華語\"},{\"n\":\"歐美\",\"v\":\"歐美\"},{\"n\":\"中國\",\"v\":\"中國\"},{\"n\":\"美國\",\"v\":\"美國\"},{\"n\":\"中國香港\",\"v\":\"中國香港\"},{\"n\":\"韓國\",\"v\":\"韓國\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"英國\",\"v\":\"英國\"},{\"n\":\"泰國\",\"v\":\"泰國\"},{\"n\":\"中國台灣\",\"v\":\"中國台灣\"},{\"n\":\"意大利\",\"v\":\"意大利\"},{\"n\":\"法國\",\"v\":\"法國\"},{\"n\":\"德國\",\"v\":\"德國\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"俄羅斯\",\"v\":\"俄羅斯\"},{\"n\":\"瑞典\",\"v\":\"瑞典\"},{\"n\":\"巴西\",\"v\":\"巴西\"},{\"n\":\"丹麥\",\"v\":\"丹麥\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"加拿大\",\"v\":\"加拿大\"},{\"n\":\"愛爾蘭\",\"v\":\"愛爾蘭\"},{\"n\":\"澳大利亞\",\"v\":\"澳大利亞\"}]},{\"key\":\"sort\",\"name\":\"排序\",\"value\":[{\"n\":\"近期熱度\",\"v\":\"T\"},{\"n\":\"首播時間\",\"v\":\"R\"},{\"n\":\"高分優先\",\"v\":\"S\"}]},{\"key\":\"年代\",\"name\":\"年代\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2024\",\"v\":\"2024\"},{\"n\":\"2023\",\"v\":\"2023\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2010年代\",\"v\":\"2010年代\"},{\"n\":\"2000年代\",\"v\":\"2000年代\"},{\"n\":\"90年代\",\"v\":\"90年代\"},{\"n\":\"80年代\",\"v\":\"80年代\"},{\"n\":\"70年代\",\"v\":\"70年代\"},{\"n\":\"60年代\",\"v\":\"60年代\"},{\"n\":\"更早\",\"v\":\"更早\"}]},{\"key\":\"平台\",\"name\":\"平台\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"騰訊視頻\",\"v\":\"騰訊視頻\"},{\"n\":\"愛奇藝\",\"v\":\"愛奇藝\"},{\"n\":\"優酷\",\"v\":\"優酷\"},{\"n\":\"湖南衛視\",\"v\":\"湖南衛視\"},{\"n\":\"Netflix\",\"v\":\"Netflix\"},{\"n\":\"HBO\",\"v\":\"HBO\"},{\"n\":\"BBC\",\"v\":\"BBC\"},{\"n\":\"NHK\",\"v\":\"NHK\"},{\"n\":\"CBS\",\"v\":\"CBS\"},{\"n\":\"NBC\",\"v\":\"NBC\"},{\"n\":\"tvN\",\"v\":\"tvN\"}]}],\"rank_list_movie\":[{\"key\":\"榜單\",\"name\":\"榜單\",\"value\":[{\"n\":\"實時熱門電影\",\"v\":\"movie_real_time_hotest\"},{\"n\":\"一週口碑電影榜\",\"v\":\"movie_weekly_best\"},{\"n\":\"豆瓣電影Top250\",\"v\":\"movie_top250\"}]}],\"rank_list_tv\":[{\"key\":\"榜單\",\"name\":\"榜單\",\"value\":[{\"n\":\"實時熱門電視\",\"v\":\"tv_real_time_hotest\"},{\"n\":\"華語口碑劇集榜\",\"v\":\"tv_chinese_best_weekly\"},{\"n\":\"全球口碑劇集榜\",\"v\":\"tv_global_best_weekly\"},{\"n\":\"國內口碑綜藝榜\",\"v\":\"show_chinese_best_weekly\"},{\"n\":\"國外口碑綜藝榜\",\"v\":\"show_global_best_weekly\"}]}]}");
        } catch (Exception e) {
            return null;
        }
    }
}
