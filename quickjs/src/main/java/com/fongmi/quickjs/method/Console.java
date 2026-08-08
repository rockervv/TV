package com.fongmi.quickjs.method;

import com.orhanobut.logger.Logger;
import com.whl.quickjs.wrapper.QuickJSContext;

public class Console implements QuickJSContext.Console {

    private final String tag;

    public Console(String tag) {
        this.tag = tag;
    }

    @Override
    public void log(String info) {
        Logger.t(tag).d(info);
    }

    @Override
    public void info(String info) {
        Logger.t(tag).i(info);
    }

    @Override
    public void warn(String info) {
        Logger.t(tag).w(info);
    }

    @Override
    public void error(String info) {
        Logger.t(tag).e(info);
    }
}