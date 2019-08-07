package org.xbib.netty.http.common;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public interface HttpHeaders {

    String getHeader(CharSequence header);

    List<String> getAllHeaders(CharSequence header);

    Iterator<Map.Entry<CharSequence, CharSequence>> iterator();
}
