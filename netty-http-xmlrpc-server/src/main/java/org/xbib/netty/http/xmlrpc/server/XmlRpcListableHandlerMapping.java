package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHandler;

/**
 * A listable handler mapping is able to provide support for
 * XML-RPC meta data, as specified 
 * <a href="http://scripts.incutio.com/xmlrpc/introspection.html">
 * here</a>.<br>
 *
 * @see <a href="http://scripts.incutio.com/xmlrpc/introspection.html">
 * Specification of XML-RPC introspection</a>
 */
public interface XmlRpcListableHandlerMapping extends XmlRpcHandlerMapping {
    /** This method implements the introspection method
     * <code>system.listMethods</code>, which is specified
     * as follows:
     * <cite>
     *   <p>This method may be used to enumerate the methods implemented
     *   by the XML-RPC server.</p>
     *   <p>The <code>system.listMethods</code> method requires no
     *   parameters. It returns an array of strings, each of which is
     *   the name of a method implemented by the server.
     * </cite>
     * <p>Note, that the specification doesn't require that the list
     * must be exhaustive. We conclude, that a valid method
     * "handlerName" doesn't need to be in the list. For example,
     * a handler, which implements {@link XmlRpcHandler}, but not
     * {@link XmlRpcMetaDataHandler}, should possibly excluded:
     * Otherwise, the listable handler mapping could not provide
     * meaningful replies to <code>system.methodSignature</code>,
     * and <code>system.methodHelp</code>.
     *
     * @throws XmlRpcException An internal error occurred.
     */
    String[] getListMethods() throws XmlRpcException;

    /** This method implements the introspection method
     * <code>system.methodSignature</code>, which is specified
     * as follows:
     * <cite>
     *   <p>This method takes one parameter, the name of a method
     *    implemented by the XML-RPC server. It returns an array
     *    of possible signatures for this method. A signature is
     *    an array of types. The first of these types is the return
     *    type of the method, the rest are parameters.</p>
     *   <p>Multiple signatures (ie. overloading) are permitted:
     *    this is the reason that an array of signatures are returned
     *    by this method.</p>
     *   <p>Signatures themselves are restricted to the top level
     *    parameters expected by a method. For instance if a method
     *    expects one array of structs as a parameter, and it returns
     *    a string, its signature is simply "string, array". If it
     *    expects three integers, its signature is
     *    "string, int, int, int".</p>
     *   <p>If no signature is defined for the method, a none-array
     *    value is returned. Therefore this is the way to test for a
     *    non-signature, if $resp below is the response object from
     *    a method call to system.methodSignature:
     *    <pre>
     *      $v=$resp->value();
     *      if ($v->kindOf()!="array") {
     *        // then the method did not have a signature defined
     *      }
     *    </pre>
     *    See the introspect.php demo included in this distribution
     *    for an example of using this method.</p>
     * </cite>
     * @see XmlRpcMetaDataHandler#getSignatures()
     */
    String[][] getMethodSignature(String pHandlerName) throws XmlRpcException;

    /** This method implements the introspection method
     * <code>system.methodSignature</code>, which is specified
     * as follows:
     * <cite>
     *   <p>This method takes one parameter, the name of a
     *     method implemented by the XML-RPC server. It
     *     returns a documentation string describing the
     *     use of that method. If no such string is available,
     *     an empty string is returned.</p>
     *   <p>The documentation string may contain HTML markup.</p>
     * </cite>
     */
    String getMethodHelp(String pHandlerName) throws XmlRpcException;
}
