package org.xbib.netty.http.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class DateTimeUtil {

    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    private static final Locale ROOT_LOCALE = Locale.ROOT;

    private static final String RFC1036_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss zzz";

    private static final String ASCIITIME_PATTERN = "EEE MMM d HH:mm:ss yyyyy";

    private DateTimeUtil() {
    }

    public static String formatRfc1123(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    public static String formatRfc1123(long millis) {
        return formatRfc1123(Instant.ofEpochMilli(millis));
    }

    // RFC 2616 allows RFC 1123, RFC 1036, ASCII time
    private static final DateTimeFormatter[] dateTimeFormatters = {
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(ROOT_LOCALE).withZone(ZONE_UTC),
            DateTimeFormatter.ofPattern(RFC1036_PATTERN).withLocale(ROOT_LOCALE).withZone(ZONE_UTC),
            DateTimeFormatter.ofPattern(ASCIITIME_PATTERN).withLocale(ROOT_LOCALE).withZone(ZONE_UTC)
    };

    public static Instant parseDate(String date, int start, int end) {
        int length = end - start;
        if (length == 0) {
            return null;
        } else if (length < 0) {
            throw new IllegalArgumentException("Can't have end < start");
        } else if (length > 64) {
            throw new IllegalArgumentException("Can't parse more than 64 chars," +
                    "looks like a user error or a malformed header");
        }
        return parseDate(date.substring(start, end));
    }

    public static Instant parseDate(String input) {
        if (input == null) {
            return null;
        }
        int semicolonIndex = input.indexOf(';');
        String trimmedDate = semicolonIndex >= 0 ? input.substring(0, semicolonIndex) : input;
        for (DateTimeFormatter formatter : dateTimeFormatters) {
            try {
                return Instant.from(formatter.parse(trimmedDate));
            } catch (DateTimeParseException e) {
                //
            }
        }
        return null;
    }
}
