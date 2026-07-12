package com.fongmi.quickjs.utils;

import android.util.Base64;

import com.github.catvod.utils.Util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    public static String md5(String text) {
        try {
            return Util.md5(text);
        } catch (Exception e) {
            return "";
        }
    }

    public static String aes(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        try {
            byte[] keyBytes = Arrays.copyOf(key.getBytes(StandardCharsets.UTF_8), 16);
            byte[] ivBytes = Arrays.copyOf(iv.getBytes(StandardCharsets.UTF_8), 16);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance(mode.contains("/") ? mode : mode + "/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] content = inBase64 ? Base64.decode(input.replace("_", "/").replace("-", "+"), Base64.DEFAULT) : input.getBytes(StandardCharsets.UTF_8);
            byte[] result = cipher.doFinal(content);
            return outBase64 ? Base64.encodeToString(result, Base64.DEFAULT) : new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static String rsa(String mode, boolean pub, boolean encrypt, String input, boolean inBase64, String key, boolean outBase64) {
        try {
            Cipher cipher = Cipher.getInstance(mode.isEmpty() ? "RSA/ECB/PKCS1Padding" : mode);
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, generateKey(pub, key));
            byte[] content = inBase64 ? Base64.decode(input, Base64.DEFAULT) : input.getBytes(StandardCharsets.UTF_8);
            byte[] result = cipher.doFinal(content);
            return outBase64 ? Base64.encodeToString(result, Base64.DEFAULT) : new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static Key generateKey(boolean pub, String key) throws Exception {
        key = key.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s+", "");
        byte[] bytes = Base64.decode(key, Base64.DEFAULT);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (pub) return factory.generatePublic(new X509EncodedKeySpec(bytes));
        else return factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }
}
