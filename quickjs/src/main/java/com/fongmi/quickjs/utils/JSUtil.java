package com.fongmi.quickjs.utils;

import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public class JSUtil {

    public static String decodeTo(String charsetName, JSArray array) throws CharacterCodingException {
        byte[] bytes = new byte[array.length()];
        for (int i = 0; i < array.length(); i++) bytes[i] = ((Number) array.get(i)).byteValue();
        return Charset.forName(charsetName).newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    public static JSArray toArray(QuickJSContext ctx, List<?> items) {
        JSArray array = ctx.createNewJSArray();
        for (int i = 0; i < items.size(); i++) array.set(items.get(i), i);
        return array;
    }

    public static JSArray toArray(QuickJSContext ctx, byte[] bytes) {
        JSArray array = ctx.createNewJSArray();
        for (int i = 0; i < bytes.length; i++) array.set((int) bytes[i], i);
        return array;
    }

    public static JSObject toObject(QuickJSContext ctx, Map<String, ?> map) {
        JSObject object = ctx.createNewJSObject();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) object.setProperty(entry.getKey(), (String) value);
            else if (value instanceof Integer) object.setProperty(entry.getKey(), (int) value);
            else if (value instanceof Long) object.setProperty(entry.getKey(), (long) value);
            else if (value instanceof Boolean) object.setProperty(entry.getKey(), (boolean) value);
            else if (value instanceof Double) object.setProperty(entry.getKey(), (double) value);
            else if (value instanceof JSObject) object.setProperty(entry.getKey(), (JSObject) value);
        }
        return object;
    }
}
