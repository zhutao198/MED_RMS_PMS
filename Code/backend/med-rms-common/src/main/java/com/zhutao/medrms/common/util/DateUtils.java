package com.zhutao.medrms.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATETIME_T = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String PATTERN_DATETIME_MS = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(PATTERN_DATETIME);
    public static final DateTimeFormatter DATETIME_T_FORMATTER = DateTimeFormatter.ofPattern(PATTERN_DATETIME_T);
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern(PATTERN_DATETIME_MS);

    private DateUtils() {}

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATETIME_FORMATTER);
    }

    public static String formatIso(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(ISO_FORMATTER);
    }

    public static LocalDateTime parse(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DATETIME_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTimeStr, DATETIME_T_FORMATTER);
            } catch (Exception e2) {
                return LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);
            }
        }
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static String nowStr() {
        return format(now());
    }

    public static String nowIsoStr() {
        return formatIso(now());
    }
}