package is.xyz.mpv;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.Keep;
import java.util.ArrayList;
import java.util.List;

@Keep
public final class MPVLib {

    static {
        String[] libs = {"avutil", "swresample", "avcodec", "swscale", "avformat", "avfilter", "avdevice", "mpv", "player"};
        for (String lib : libs) {
            try {
                System.loadLibrary(lib);
            } catch (Throwable e) {
                android.util.Log.e("MPVLib", "Failed to load library: " + lib + " - " + e.getMessage());
            }
        }
    }

    public static native void create(Context context);
    public static native void init();
    public static native void destroy();
    public static native void attachSurface(Surface surface);
    public static native void detachSurface();
    public static native int command(String[] cmd);
    public static native int setOptionString(String name, String value);
    public static native int setPropertyString(String name, String value);
    public static native int setPropertyInt(String name, int value);
    public static native int setPropertyBoolean(String name, boolean value);
    public static native int setPropertyDouble(String name, double value);
    public static native String getPropertyString(String name);
    public static native Integer getPropertyInt(String name);
    public static native Double getPropertyDouble(String name);
    public static native Boolean getPropertyBoolean(String name);
    public static native int observeProperty(String name, int format);
    public static native Bitmap grabThumbnail(int dimension);

    // --- Observer Pattern ---

    private static final List<EventObserver> observers = new ArrayList<>();
    private static final List<LogObserver> logObservers = new ArrayList<>();

    public static void addObserver(EventObserver observer) {
        synchronized (observers) {
            observers.add(observer);
        }
    }

    public static void removeObserver(EventObserver observer) {
        synchronized (observers) {
            observers.remove(observer);
        }
    }

    public static void addLogObserver(LogObserver observer) {
        synchronized (logObservers) {
            logObservers.add(observer);
        }
    }

    public static void removeLogObserver(LogObserver observer) {
        synchronized (logObservers) {
            logObservers.remove(observer);
        }
    }

    // --- JNI Callbacks ---

    @Keep
    public static void eventProperty(String property) {
        synchronized (observers) {
            for (EventObserver o : observers) o.eventProperty(property);
        }
    }

    @Keep
    public static void eventProperty(String property, long value) {
        synchronized (observers) {
            for (EventObserver o : observers) o.eventProperty(property, value);
        }
    }

    @Keep
    public static void eventProperty(String property, boolean value) {
        synchronized (observers) {
            for (EventObserver o : observers) o.eventProperty(property, value);
        }
    }

    @Keep
    public static void eventProperty(String property, String value) {
        synchronized (observers) {
            for (EventObserver o : observers) o.eventProperty(property, value);
        }
    }

    @Keep
    public static void eventProperty(String property, double value) {
        synchronized (observers) {
            for (EventObserver o : observers) o.eventProperty(property, value);
        }
    }

    @Keep
    public static void event(int eventId) {
        synchronized (observers) {
            for (EventObserver o : observers) o.event(eventId);
        }
    }

    @Keep
    public static void logMessage(String prefix, int level, String text) {
        synchronized (logObservers) {
            for (LogObserver o : logObservers) o.logMessage(prefix, level, text);
        }
    }

    // --- Interfaces ---

    public interface EventObserver {
        void eventProperty(String property);
        void eventProperty(String property, long value);
        void eventProperty(String property, boolean value);
        void eventProperty(String property, String value);
        void eventProperty(String property, double value);
        void event(int eventId);
    }

    public interface LogObserver {
        void logMessage(String prefix, int level, String text);
    }
}
