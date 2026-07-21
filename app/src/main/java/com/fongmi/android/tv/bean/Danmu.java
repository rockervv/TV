package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.tickaroo.tikxml.TikXml;
import com.tickaroo.tikxml.annotation.Attribute;
import com.tickaroo.tikxml.annotation.Element;
import com.tickaroo.tikxml.annotation.TextContent;
import com.tickaroo.tikxml.annotation.Xml;

import java.util.Collections;
import java.util.List;

import okio.Buffer;

@Xml(name = "i")
public class Danmu {

    @Element(name = "d")
    public List<Data> data;

    public static Danmu fromXml(String str) {
        try {
            return new TikXml.Builder().exceptionOnUnreadXml(false).build().read(new Buffer().writeUtf8(str), Danmu.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new Danmu();
        }
    }

    public List<Data> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    @Xml(name = "d")
    public static class Data {

        @Attribute(name = "p")
        public String param;

        @TextContent
        public String text;

        public String getParam() {
            return TextUtils.isEmpty(param) ? "" : param;
        }

        public String getText() {
            return TextUtils.isEmpty(text) ? "" : text;
        }
    }
}
