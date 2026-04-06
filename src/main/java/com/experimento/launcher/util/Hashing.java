package com.experimento.launcher.util;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hashing {

    private Hashing() {}

    public static String sha1Hex(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
