package com.jxl.ai.intelliconf.author_discovery.util;

import cn.hutool.core.util.StrUtil;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AuthorDiscoveryTextUtil {

    private AuthorDiscoveryTextUtil() {
    }

    public static String normalizeName(String name) {
        if (StrUtil.isBlank(name)) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static List<String> titleCoreWords(String title) {
        if (StrUtil.isBlank(title)) {
            return List.of();
        }
        return Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(item -> item.length() >= 5)
                .filter(item -> !List.of("using", "based", "study", "paper", "model", "method").contains(item))
                .limit(8)
                .toList();
    }

    public static String maskEmail(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return "";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.length() <= 2 ? "***" : local.substring(0, 2) + "***") + email.substring(at);
    }
}
