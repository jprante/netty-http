package org.xbib.netty.http.server.util;

import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class MimeTypeUtils {

    /**
     * A map from extension to MIME types, which is queried before
     * {@link URLConnection#guessContentTypeFromName(String)}, so that
     * important extensions are always mapped to the right MIME types.
     */
    private static final Map<String, String> EXTENSION_TO_MEDIA_TYPE;

    static {
        Map<String, String> map = new HashMap<>();
        // Text files
        add(map, "text/css", "css");
        add(map, "text/html", "html", "htm");
        add(map, "text/plain", "txt");

        // Image files
        add(map, "image/gif", "gif");
        add(map, "image/jpeg", "jpeg", "jpg");
        add(map, "image/png", "png");
        add(map, "image/svg+xml", "svg", "svgz");
        add(map, "image/x-icon", "ico");

        // Font files
        add(map, "application/x-font-ttf", "ttc", "ttf");
        add(map, "application/font-woff", "woff");
        add(map, "application/font-woff2", "woff2");
        add(map, "application/vnd.ms-fontobject", "eot");
        add(map, "font/opentype", "otf");

        // JavaScript, XML, etc
        add(map, "application/javascript", "js", "map");
        add(map, "application/json", "json");
        add(map, "application/pdf", "pdf");
        add(map, "application/xhtml+xml", "xhtml", "xhtm");
        add(map, "application/xml", "xml", "xsd");
        add(map, "application/xml-dtd", "dtd");

        EXTENSION_TO_MEDIA_TYPE = Collections.unmodifiableMap(map);
    }

    private static void add(Map<String, String> extensionToMediaType,
                            String mediaType, String... extensions) {
        for (String s : extensions) {
            extensionToMediaType.put(s, mediaType);
        }
    }

    public static String guessFromPath(String path, boolean preCompressed) {
        requireNonNull(path, "path");
        String s = path;
        // If the path is for a precompressed file, it will have an additional extension indicating the
        // encoding, which we don't want to use when determining content type.
        if (preCompressed) {
            s = s.substring(0, s.lastIndexOf('.'));
        }
        int dotIdx = s.lastIndexOf('.');
        int slashIdx = s.lastIndexOf('/');
        if (dotIdx < 0 || slashIdx > dotIdx) {
            // No extension
            return null;
        }
        String extension = s.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        String mediaType = EXTENSION_TO_MEDIA_TYPE.get(extension);
        if (mediaType != null) {
            return mediaType;
        }
        String guessedContentType = URLConnection.guessContentTypeFromName(path);
        return guessedContentType != null ? guessedContentType : "application/octet-stream";
    }
}
