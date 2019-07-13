package org.xbib.netty.http.common.util;

import java.util.Objects;
import java.util.TreeSet;

@SuppressWarnings("serial")
public class LimitedSet<T extends CharSequence> extends TreeSet<T> {

    private final int sizeLimit;

    private final int elementMaximumLength;

    public LimitedSet(int sizeLimit, int elementMaximumLength) {
        this.sizeLimit = sizeLimit;
        this.elementMaximumLength = elementMaximumLength;
    }

    @Override
    public boolean add(T t) {
        Objects.requireNonNull(t);
        if (size() < sizeLimit && t.length() <= elementMaximumLength) {
            return super.add(t);
        }
        return false;
    }
}
