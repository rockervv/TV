package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.tickaroo.tikxml.annotation.Attribute;
import com.tickaroo.tikxml.annotation.Element;
import com.tickaroo.tikxml.annotation.PropertyElement;
import com.tickaroo.tikxml.annotation.TextContent;
import com.tickaroo.tikxml.annotation.Xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Xml(name = "tv")
public class Tv {
    @Attribute(name = "date")
    public String date;

    @Element(name = "channel")
    public List<Channel> channel;

    @Element(name = "programme")
    public List<Programme> programme;

    public String getDate() {
        return TextUtils.isEmpty(date) ? "" : date;
    }

    public List<Channel> getChannel() {
        return channel == null ? Collections.emptyList() : channel;
    }

    public List<Programme> getProgramme() {
        return programme == null ? Collections.emptyList() : programme;
    }

    @Xml(name = "channel")
    public static class Channel {

        @Attribute(name = "id")
        public String id;
        @Element(name = "icon")
        public Icon icon;
        @Element(name = "display-name")
        public List<DisplayName> displayName;

        public String getId() {
            return TextUtils.isEmpty(id) ? "" : id;
        }

        private Icon getIcon() {
            return icon == null ? new Icon() : icon;
        }

        public List<DisplayName> getDisplayName() {
            return displayName == null ? new ArrayList<>() : displayName;
        }

        public String getSrc() {
            return getIcon().getSrc();
        }

        public boolean hasSrc() {
            return !getIcon().getSrc().isEmpty();
        }

    }

    @Xml(name = "programme")
    public static class Programme {

        @Attribute(name = "start")
        public String start;

        @Attribute(name = "stop")
        public String stop;

        @Attribute(name = "channel")
        public String channel;

        @Element(name = "title")
        public List<Title> title;

        public String getStart() {
            return TextUtils.isEmpty(start) ? "" : start;
        }

        public String getStop() {
            return TextUtils.isEmpty(stop) ? "" : stop;
        }

        public String getChannel() {
            return TextUtils.isEmpty(channel) ? "" : channel;
        }

        public String getTitle() {
            return title == null ? "" : title.stream().map(Title::getText).filter(text -> !text.isEmpty()).findFirst().orElse("");
        }
    }

    @Xml(name = "title")
    public static class Title {

        @TextContent
        public String text;

        public String getText() {
            return TextUtils.isEmpty(text) ? "" : text;
        }
    }

    @Xml(name = "icon")
    public static class Icon {

        @Attribute(name = "src")
        public String src;

        public String getSrc() {
            return TextUtils.isEmpty(src) ? "" : src;
        }
    }

    @Xml(name = "display-name")
    public static class DisplayName {

        @TextContent
        public String text;

        public String getText() {
            return TextUtils.isEmpty(text) ? "" : text;
        }
    }

}
