package org.xbib.netty.http.server.annotation;

import org.xbib.netty.http.server.endpoint.service.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code Endpoint} annotation decorates methods which are mapped
 * to a HTTP endpoint within the server, and provide its contents.
 * The annotated methods must have the same signature and contract
 * as {@link Service#handle}, but can have arbitrary names.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Endpoint {

    /**
     * The path that this field maps to (must begin with '/').
     *
     * @return the path that this field maps to
     */
    String path();

    /**
     * The HTTP methods supported by this endpoint (default is "GET" and "HEAD").
     *
     * @return the HTTP methods supported by this endpoint
     */
    String[] methods() default {"GET", "HEAD"};

    String[] contentTypes();
}
