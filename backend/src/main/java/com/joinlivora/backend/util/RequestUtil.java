package com.joinlivora.backend.util;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtil {

    public static String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    public static String getClientCountry(HttpServletRequest request) {
        return request.getHeader("CF-IPCountry");
    }

    public static String getUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    public static String getDeviceFingerprint(HttpServletRequest request) {
        return request.getHeader("X-Device-Fingerprint");
    }
}
