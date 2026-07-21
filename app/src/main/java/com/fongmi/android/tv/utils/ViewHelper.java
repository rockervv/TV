package com.fongmi.android.tv.utils;

import android.view.View;
import android.view.ViewGroup;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;

public class ViewHelper {

    public static void setWidth(View view, int width) {
        if (view == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.width == width) return;
        params.width = width;
        view.setLayoutParams(params);
    }

    public static int getWidth(Live live, int padding, int size) {
        if (live.getWidth() == 0) for (Group item : live.getGroups()) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getName(), size)));
        return live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
    }

    public static int getWidth(Group group, int logo, int padding, int size) {
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) group.setWidth(Math.max(group.getWidth(), (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(item.getNumber() + item.getName(), size)));
        return group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + padding, ResUtil.getScreenWidth() / 2);
    }

    public static int getWidth(Epg epg, int padding, int size) {
        if (epg.getList().isEmpty()) return 0;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), size - 2);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), size)));
        int maxWidth = ResUtil.getScreenWidth() / 2;
        int minContentWidth = Math.min(minWidth + padding, maxWidth);
        return epg.getWidth() == 0 ? 0 : Math.max(minContentWidth, Math.min(epg.getWidth() + padding, maxWidth));
    }
}
