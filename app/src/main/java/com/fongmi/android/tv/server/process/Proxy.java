package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;

import java.io.InputStream;
import java.util.Map;
import android.util.Log;  // 別忘了加這行

import fi.iki.elonen.NanoHTTPD;

public class Proxy implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/proxy".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            params.putAll(session.getHeaders());

            // ✅ Log 輸出你正在請求什麼
            String doType = params.get("do");
            String url = params.get("url");
            Log.d("ProxyJS", "do=" + doType + ", url=" + url);

            Object[] rs = BaseLoader.get().proxyLocal(params);

            // ✅ 若是 JS，嘗試讀取前幾個字元印出
            if ("js".equals(doType) && rs[2] instanceof InputStream) {
                InputStream is = (InputStream) rs[2];
                byte[] buf = new byte[100];
                int len = is.read(buf);
                String preview = new String(buf, 0, Math.max(0, len));
                Log.d("ProxyJS", "JS preview: " + preview.replace("\n", "\\n").replace("\r", ""));
                // reset InputStream for actual usage（這步略過，實際上 proxyLocal 應包裝可重複讀）
            }


            if (rs[0] instanceof NanoHTTPD.Response) return (NanoHTTPD.Response) rs[0];
            NanoHTTPD.Response response = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.lookup((Integer) rs[0]), (String) rs[1], (InputStream) rs[2]);
            if (rs.length > 3 && rs[3] != null) for (Map.Entry<String, String> entry : ((Map<String, String>) rs[3]).entrySet()) response.addHeader(entry.getKey(), entry.getValue());
            return response;
        } catch (Exception e) {
            return Nano.error("Proxy " + path + " error: " + e.getMessage());
        }
    }

}
