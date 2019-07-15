package org.xbib.netty.http.xmlrpc.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.xbib.netty.http.xmlrpc.common.LocalStreamConnection;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestProcessor;
import org.xml.sax.SAXException;

/** Another local transport for debugging and testing. This one is
 * similar to the {@link XmlRpcLocalTransport},
 * except that it adds request serialization. In other words, it is
 * particularly well suited for development and testing of XML serialization
 * and parsing.
 */
public class XmlRpcLocalStreamTransport extends XmlRpcStreamTransport {
    private final XmlRpcStreamRequestProcessor localServer;
    private LocalStreamConnection conn;
    private XmlRpcRequest request;

    /** Creates a new instance.
     * @param pClient The client, which is controlling the transport.
     * @param pServer An instance of {@link XmlRpcStreamRequestProcessor}.
     */
    public XmlRpcLocalStreamTransport(XmlRpcClient pClient,
                                      XmlRpcStreamRequestProcessor pServer) {
        super(pClient);
        localServer = pServer;
    }

    protected boolean isResponseGzipCompressed(XmlRpcStreamRequestConfig pConfig) {
        return pConfig.isGzipRequesting();
    }

    protected void close() throws XmlRpcClientException {
    }

    protected InputStream getInputStream() throws XmlRpcException {
        localServer.execute(conn.getConfig(), conn.getServerStreamConnection());
        return new ByteArrayInputStream(conn.getResponse().toByteArray());
    }

    protected ReqWriter newReqWriter(XmlRpcRequest pRequest)
            throws XmlRpcException, IOException, SAXException {
        request = pRequest;
        return super.newReqWriter(pRequest);
    }

    protected void writeRequest(ReqWriter pWriter)
            throws XmlRpcException, IOException, SAXException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pWriter.write(baos);
        XmlRpcStreamRequestConfig config = (XmlRpcStreamRequestConfig) request.getConfig();
        conn = new LocalStreamConnection(config, new ByteArrayInputStream(baos.toByteArray()));
    }
}
