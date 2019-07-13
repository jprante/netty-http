package org.xbib.netty.http.xmlrpc.common;

/**
 *  Extension for HTTP based transport. Provides details like server URL,
 * user credentials, and so on.
 */
public interface XmlRpcHttpRequestConfig extends XmlRpcStreamRequestConfig, XmlRpcHttpConfig {

    /** Returns the user name being used for basic HTTP authentication.
     * @return User name or null, if no basic HTTP authentication is being used.
     */
    String getBasicUserName();

    /** Returns the password being used for basic HTTP authentication.
     * @return Password or null, if no basic HTTP authentication is beind used.
     * @throws IllegalStateException A user name is configured, but no password.
     */
    String getBasicPassword();

    /** Return the connection timeout in milliseconds
     * @return connection timeout in milliseconds or 0 if no set
     */
    int getConnectionTimeout();

    /** Return the reply timeout in milliseconds
     * @return reply timeout in milliseconds or 0 if no set
     */
    int getReplyTimeout();
}
