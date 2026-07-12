package com.fongmi.quickjs.utils;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java.util.concurrent.CompletableFuture;

public class Async {

    public static CompletableFuture<Object> run(JSObject object, String name, Object... args) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        JSFunction func = object.getJSFunction(name);
        call(future, func, args);
        return future;
    }

    private static void call(CompletableFuture<Object> future, JSFunction func, Object... args) {
        Object result = func.call(args);
        if (result instanceof JSObject) {
            then(future, (JSObject) result);
        } else {
            future.complete(result);
        }
        func.release();
    }

    private static void then(CompletableFuture<Object> future, JSObject promise) {
        JSFunction then = promise.getJSFunction("then");
        if (then == null) {
            future.complete(promise);
        } else {
            consume(then, args -> future.complete(args[0]));
            consume(promise.getJSFunction("catch"), args -> future.completeExceptionally(new Exception(args[0].toString())));
        }
    }

    private static void consume(JSFunction func, JSCallFunction callback) {
        func.call(callback);
        func.release();
    }
}
