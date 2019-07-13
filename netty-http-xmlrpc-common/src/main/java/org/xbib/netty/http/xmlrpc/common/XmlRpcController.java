package org.xbib.netty.http.xmlrpc.common;


public abstract class XmlRpcController {

    private XmlRpcWorkerFactory workerFactory = getDefaultXmlRpcWorkerFactory();

    private int maxThreads;

    private TypeFactory typeFactory = new TypeFactoryImpl(this);

    /** Creates the controllers default worker factory.
     * @return The default factory for workers.
     */
    protected abstract XmlRpcWorkerFactory getDefaultXmlRpcWorkerFactory();

    /** Sets the maximum number of concurrent requests. This includes
     * both synchronous and asynchronous requests.
     * @param pMaxThreads Maximum number of threads or 0 to disable
     * the limit.
     */
    public void setMaxThreads(int pMaxThreads) {
        maxThreads = pMaxThreads;
    }

    /** Returns the maximum number of concurrent requests. This includes
     * both synchronous and asynchronous requests.
     * @return Maximum number of threads or 0 to disable
     * the limit.
     */
    public int getMaxThreads() {
        return maxThreads;
    }

    /** Sets the clients worker factory.
     * @param pFactory The factory being used to create workers.
     */
    public void setWorkerFactory(XmlRpcWorkerFactory pFactory) {
        workerFactory = pFactory;
    }

    /** Returns the clients worker factory.
     * @return The factory being used to create workers.
     */
    public XmlRpcWorkerFactory getWorkerFactory() {
        return workerFactory;
    }

    /** Returns the controllers default configuration.
     * @return The default configuration.
     */
    public abstract XmlRpcConfig getConfig();

    /** Sets the type factory.
     * @param pTypeFactory The type factory.
     */
    public void setTypeFactory(TypeFactory pTypeFactory) {
        typeFactory = pTypeFactory;
    }

    /** Returns the type factory.
     * @return The type factory.
     */
    public TypeFactory getTypeFactory() {
        return typeFactory;
    }
}
