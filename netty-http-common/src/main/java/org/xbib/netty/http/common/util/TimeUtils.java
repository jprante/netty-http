package org.xbib.netty.http.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimeUtils {

    public static String formatInstant(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    public static String formatMillis(long millis) {
        return formatInstant(Instant.ofEpochMilli(millis));
    }

    public static String formatSeconds(long seconds) {
        return formatInstant(Instant.now().plusSeconds(seconds));
    }

    private static final String RFC1036_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss zzz";

    private static final String ASCIITIME_PATTERN = "EEE MMM d HH:mm:ss yyyyy";

    private static final DateTimeFormatter[] dateTimeFormatters = {
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern(RFC1036_PATTERN),
            DateTimeFormatter.ofPattern(ASCIITIME_PATTERN)
    };

    public static Instant parseDate(String date) {
        if (date == null) {
            return null;
        }
        int semicolonIndex = date.indexOf(';');
        String trimmedDate = semicolonIndex >= 0 ? date.substring(0, semicolonIndex) : date;
        // RFC 2616 allows RFC 1123, RFC 1036, ASCII time
        for (DateTimeFormatter formatter : dateTimeFormatters) {
            try {
                return Instant.from(formatter.withZone(ZoneId.of("UTC")).parse(trimmedDate));
            } catch (DateTimeParseException e) {
                // skip
            }
        }
        return null;
    }

}
