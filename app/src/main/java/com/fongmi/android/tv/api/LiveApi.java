package com.fongmi.android.tv.api;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Source;

public class LiveApi {

    public static void parse(@NonNull Live item) throws Exception {
        // LiveParser.start(item.recent());
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }
}
