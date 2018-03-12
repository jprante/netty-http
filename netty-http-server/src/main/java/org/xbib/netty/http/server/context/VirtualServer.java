package org.xbib.netty.http.server.context;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The {@code VirtualServer} class represents a virtual server.
 */
public class VirtualServer {

    private final String name;

    private final Set<String> aliases;

    private final Set<String> methods;

    private final ContextInfo emptyContext;

    private final Map<String, ContextInfo> contexts;

    private volatile boolean allowGeneratedIndex;

    /**
     * Constructs a VirtualServer with the given name.
     *
     * @param name the name, or null if it is the default server
     */
    public VirtualServer(String name) {
        this.name = name;
        this.aliases = new HashSet<>();
        this.methods = new HashSet<>();
        this.contexts = new HashMap<>();
        this.emptyContext = new ContextInfo(this);
        contexts.put("*", new ContextInfo(this)); // for "OPTIONS *"
    }

    /**
     * Returns the name.
     *
     * @return the name, or null if it is the default server
     */
    public String getName() {
        return name;
    }

    /**
     * Adds an alias for this virtual server.
     *
     * @param alias the alias
     */
    public void addAlias(String alias) {
        aliases.add(alias);
    }

    /**
     * Returns the aliases.
     *
     * @return the (unmodifiable) set of aliases (which may be empty)
     */
    public Set<String> getAliases() {
        return Collections.unmodifiableSet(aliases);
    }

    /**
     * Returns whether auto-generated indices are allowed.
     *
     * @return whether auto-generated indices are allowed
     */
    public boolean isAllowGeneratedIndex() {
        return allowGeneratedIndex;
    }

    /**
     * Sets whether auto-generated indices are allowed. If false, and a
     * directory resource is requested, an error will be returned instead.
     *
     * @param allowed specifies whether generated indices are allowed
     */
    public void setAllowGeneratedIndex(boolean allowed) {
        this.allowGeneratedIndex = allowed;
    }

    /**
     * Returns all HTTP methods explicitly supported by at least one context
     * (this may or may not include the methods with required or built-in support).
     *
     * @return all HTTP methods explicitly supported by at least one context
     */
    public Set<String> getMethods() {
        return methods;
    }

    /**
     * Returns the context handler for the given path.
     * If a context is not found for the given path, the search is repeated for
     * its parent path, and so on until a base context is found. If neither the
     * given path nor any of its parents has a context, an empty context is returned.
     *
     * @param path the context's path
     * @return the context info for the given path, or an empty context if none exists
     */
    public ContextInfo getContext(String path) {
        path = trimRight(path, '/'); // remove trailing slash
        ContextInfo info = null;
        while (info == null && path != null) {
            info = contexts.get(path);
            path = getParentPath(path);
        }
        return info != null ? info : emptyContext;
    }

    /**
     * Adds a context and its corresponding context handler to this server.
     * Paths are normalized by removing trailing slashes (except the root).
     *
     * @param path    the context's path (must start with '/')
     * @param handler the context handler for the given path
     * @param methods the HTTP methods supported by the context handler (default is "GET")
     * @throws IllegalArgumentException if path is malformed
     */
    public void addContext(String path, ContextHandler handler, String... methods) {
        if (path == null || !path.startsWith("/") && !path.equals("*")) {
            throw new IllegalArgumentException("invalid path: " + path);
        }
        path = trimRight(path, '/');
        ContextInfo info = new ContextInfo(this);
        ContextInfo existing = contexts.putIfAbsent(path, info);
        info = existing != null ? existing : info;
        info.addHandler(handler, methods);
    }

    /**
     * Adds contexts for all methods of the given object that
     * are annotated with the {@link Context} annotation.
     *
     * @param o the object whose annotated methods are added
     * @throws IllegalArgumentException if a Context-annotated
     *                                  method has an {@link Context invalid signature}
     */
    public void addContexts(Object o) throws IllegalArgumentException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            // add to contexts those with @Context annotation
            for (Method m : c.getDeclaredMethods()) {
                Context context = m.getAnnotation(Context.class);
                if (context != null) {
                    //m.setAccessible(true); // allow access to private method
                    addContext(context.value(), new MethodContextHandler(m, o), context.methods());
                }
            }
        }
    }

    /**
     * Returns the given string with all occurrences of the given character
     * removed from its right side.
     *
     * @param s the string to trim
     * @param c the character to remove
     * @return the trimmed string
     */
    private static String trimRight(String s, char c) {
        int len = s.length() - 1;
        int end = len;
        while (end >= 0 && s.charAt(end) == c) {
            end--;
        }
        return end == len ? s : s.substring(0, end + 1);
    }

    /**
     * Returns the parent of the given path.
     *
     * @param path the path whose parent is returned (must start with '/')
     * @return the parent of the given path (excluding trailing slash),
     * or null if given path is the root path
     */
    private static String getParentPath(String path) {
        path = trimRight(path, '/'); // remove trailing slash
        int slash = path.lastIndexOf('/');
        return slash == -1 ? null : path.substring(0, slash);
    }

}
