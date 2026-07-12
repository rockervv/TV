package com.fongmi.quickjs.utils;

import com.fongmi.quickjs.bean.Req;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Connect {

    public static Call to(String url, Req req) {
        return OkHttp.client(req.isRedirect(), req.getTimeout()).newCall(getRequest(url, req));
    }

    private static Request getRequest(String url, Req req) {
        Request.Builder builder = new Request.Builder().url(url).headers(Headers.of(req.getHeader()));
        if (req.getMethod().equalsIgnoreCase("post")) builder.post(getPostBody(req));
        if (req.getMethod().equalsIgnoreCase("head")) builder.head();
        return builder.build();
    }

    private static RequestBody getPostBody(Req req) {
        Map<String, String> header = req.getHeader();
        if (req.getPostType().equalsIgnoreCase("form")) {
            FormBody.Builder builder = new FormBody.Builder();
            Map<String, String> params = Json.toMap(req.getData());
            for (String key : params.keySet()) builder.add(key, params.get(key));
            return builder.build();
        } else if (req.getPostType().equalsIgnoreCase("form-data")) {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            Map<String, String> params = Json.toMap(req.getData());
            for (String key : params.keySet()) builder.addFormDataPart(key, params.get(key));
            return builder.build();
        } else if (req.getBody() != null) {
            MediaType type = MediaType.parse(header.containsKey("Content-Type") ? header.get("Content-Type") : "application/json; charset=utf-8");
            return RequestBody.create(type, req.getBody());
        } else {
            MediaType type = MediaType.parse("application/json; charset=utf-8");
            return RequestBody.create(type, req.getData().toString());
        }
    }

    public static JSObject success(QuickJSContext ctx, Req req, Response res) {
        try (res) {
            JSObject jsObject = ctx.createNewJSObject();
            JSObject jsHeader = ctx.createNewJSObject();
            setHeader(ctx, res, jsHeader);
            jsObject.setProperty("code", res.code());
            jsObject.setProperty("headers", jsHeader);
            if (req.getBuffer() == 0) {
                jsObject.setProperty("content", new String(res.body().bytes(), req.getCharset()));
            } else if (req.getBuffer() == 1) {
                jsObject.setProperty("content", JSUtil.toArray(ctx, res.body().bytes()));
            } else if (req.getBuffer() == 2) {
                jsObject.setProperty("content", Util.base64(res.body().bytes()));
            }
            return jsObject;
        } catch (Exception e) {
            return error(ctx);
        }
    }

    private static void setHeader(QuickJSContext ctx, Response res, JSObject jsHeader) {
        Map<String, List<String>> headers = res.headers().toMultimap();
        for (String key : headers.keySet()) {
            List<String> values = headers.get(key);
            if (values == null) continue;
            if (values.size() == 1) {
                jsHeader.setProperty(key, values.get(0));
            } else {
                jsHeader.setProperty(key, JSUtil.toArray(ctx, values));
            }
        }
    }

    public static JSObject error(QuickJSContext ctx) {
        JSObject jsObject = ctx.createNewJSObject();
        jsObject.setProperty("code", 500);
        jsObject.setProperty("content", "");
        jsObject.setProperty("headers", ctx.createNewJSObject());
        return jsObject;
    }
}
