package org.xbib.netty.http.common.mime;

public interface MimeMultipartListener {

   void handle(String type, String subtype, MimeMultipart part);
}
