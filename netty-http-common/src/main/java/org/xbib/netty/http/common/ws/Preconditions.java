package org.xbib.netty.http.common.ws;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

public final class Preconditions {

    public static <T> T requireNonNull(T t, String message) {
        if (t == null) {
            throw new IllegalArgumentException(message + " must be non null");
        }
        return t;
    }

    public static String requireNonEmpty(String string, String message) {
        if (string == null || string.isEmpty()) {
            throw new IllegalArgumentException(message + " must be non empty");
        }
        return string;
    }

    public static <T extends ChannelHandler> T requireHandler(Channel channel, Class<T> handler) {
        T h = channel.pipeline().get(handler);
        if (h == null) {
            throw new IllegalArgumentException(
                    handler.getSimpleName() + " is absent in the channel pipeline");
        }
        return h;
    }

    public static long requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message + " must be positive: " + value);
        }
        return value;
    }

    public static int requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message + " must be non-negative: " + value);
        }
        return value;
    }

    public static short requireRange(int value, int from, int to, String message) {
        if (value >= from && value <= to) {
            return (short) value;
        }
        throw new IllegalArgumentException(
                String.format("%s must belong to range [%d, %d]: ", message, from, to));
    }
}
