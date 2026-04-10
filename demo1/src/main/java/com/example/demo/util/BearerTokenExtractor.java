package com.example.demo.util;

import com.example.demo.constant.HttpAuthConstants;

/**
 * Trích access token từ header Authorization (DRY cho controller).
 */
public final class BearerTokenExtractor {

    private BearerTokenExtractor() {
    }

    public static String fromAuthorizationHeader(String authorization) {
        if (authorization == null) {
            return "";
        }
        if (authorization.startsWith(HttpAuthConstants.BEARER_PREFIX)) {
            return authorization.substring(HttpAuthConstants.BEARER_PREFIX.length()).trim();
        }
        return authorization.trim();
    }
}
