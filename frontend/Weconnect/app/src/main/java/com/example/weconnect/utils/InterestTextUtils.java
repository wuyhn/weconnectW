package com.example.weconnect.utils;

import java.util.regex.Pattern;

public final class InterestTextUtils {
    private static final Pattern LEADING_ICON_PATTERN =
            Pattern.compile("^[\\p{So}\\p{Sk}\\uFE0F\\u200D\\s]+");

    private InterestTextUtils() {}

    public static String stripLeadingIcon(String value) {
        if (value == null) return "";
        return LEADING_ICON_PATTERN.matcher(value).replaceFirst("").trim();
    }
}
