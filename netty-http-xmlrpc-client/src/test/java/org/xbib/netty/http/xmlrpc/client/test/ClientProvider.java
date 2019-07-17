package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;

import java.io.IOException;

/** This interface allows to perform a unit test with various
 * transports. Basically, the implementation creates the client,
 * including the transport, and the server, if required.
 */
public interface ClientProvider {
	/** Returns the clients default configuration.
	 * @return The clients configuration.
	 * @throws Exception Creating the configuration failed.
	 */
	XmlRpcClientConfigImpl getConfig() throws Exception;

	/** Returns a new client instance.
	 * @return A client being used for performing the test.
	 */
	XmlRpcClient getClient();

	/** Returns the providers server instance.
     * @return A server instance, which is being used for performing the test.
	 */
    XmlRpcServer getServer();

    /** Performs a shutdown of the server.
     */
    void shutdown() throws IOException;
}
