package com.dylibso.chicory.wasm;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;

final class ModuleDigest {
    private ModuleDigest() {}

    static String digest(byte[] bytes, String algorithm) {
        if ("none".equalsIgnoreCase(algorithm)) {
            return null;
        }

        var normalizedAlgorithm = algorithm.toLowerCase(Locale.ROOT).replace(":", "");
        var reflectiveDigest = reflectiveDigest(bytes, algorithm);
        if (reflectiveDigest != null) {
            return normalizedAlgorithm + ":" + Base64.getEncoder().encodeToString(reflectiveDigest);
        }

        CRC32 crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return "crc32:" + String.format(Locale.ROOT, "%08x", crc32.getValue());
    }

    private static byte[] reflectiveDigest(byte[] bytes, String algorithm) {
        try {
            Class<?> messageDigestClass = Class.forName("java.security.MessageDigest");
            Method getInstance = messageDigestClass.getMethod("getInstance", String.class);
            Object digest = getInstance.invoke(null, algorithm);
            Method update = messageDigestClass.getMethod("update", byte[].class);
            update.invoke(digest, (Object) bytes);
            Method finish = messageDigestClass.getMethod("digest");
            return (byte[]) finish.invoke(digest);
        } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
            return null;
        }
    }
}
