/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client.util;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * A {@link java.util.Set} with limited size. If the size is exceeded, an exception is thrown.
 */
public final class LimitedHashSet<E> extends LinkedHashSet<E> {

    private static final long serialVersionUID = 1838128758142912702L;

    private final int max;

    public LimitedHashSet(int max) {
        this.max = max;
    }

    @Override
    public boolean add(E element) {
        if (max < size()) {
            throw new IllegalStateException("limit exceeded");
        }
        return super.add(element);
    }

    @Override
    public boolean addAll(Collection<? extends E> elements) {
        boolean b = false;
        for (E element : elements) {
            if (max < size()) {
                throw new IllegalStateException("limit exceeded");
            }
            b = b || super.add(element);
        }
        return b;
    }
}
