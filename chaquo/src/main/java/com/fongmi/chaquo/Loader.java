package com.fongmi.chaquo;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.utils.Path;

public class Loader {

    private PyObject app;

    public Loader() {
        new Thread(() -> {
            try {
                synchronized (Loader.this) {
                    if (!Python.isStarted()) Python.start(new com.chaquo.python.android.AndroidPlatform(com.github.catvod.Init.context()));
                    app = Python.getInstance().getModule("app");
                    Loader.this.notifyAll();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public Spider spider(String api) {
        synchronized (this) {
            while (app == null) {
                try {
                    this.wait(100);
                } catch (InterruptedException ignored) {
                }
            }
        }
        PyObject obj = app.callAttr("spider", Path.py().getAbsolutePath(), api);
        return new Spider(app, obj, api);
    }
}
