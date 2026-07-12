package com.fongmi.chaquo;

public class Common {

    public static final String ASSET_DIR = "chaquopy";
    public static final String ASSET_BUILD_JSON = "build.json";
    public static final String ASSET_STDLIB = "stdlib";
    public static final String ASSET_BOOTSTRAP = "bootstrap";
    public static final String ASSET_BOOTSTRAP_NATIVE = "bootstrap-native";
    public static final String ASSET_CACERT = "cacert.pem";
    public static final String ASSET_APP = "app";
    public static final String ASSET_REQUIREMENTS = "requirements";
    public static final String ABI_COMMON = "common";

    public static String assetZip(String name) {
        return name + ".imy";
    }

    public static String assetZip(String name, String abi) {
        return name + "-" + abi + ".imy";
    }
}
