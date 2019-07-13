package org.xbib.netty.http.xmlrpc.common;

import java.util.ArrayList;
import java.util.List;

/**
 * A factory for {@link XmlRpcWorker} instances.
 */
public abstract class XmlRpcWorkerFactory {

    private final XmlRpcWorker singleton = newWorker();

    private final XmlRpcController controller;

    private final List<XmlRpcWorker> pool = new ArrayList<>();

    private int numThreads;

    /**
     * Creates a new instance.
     * @param pController The client controlling the factory.
     */
    public XmlRpcWorkerFactory(XmlRpcController pController) {
        controller = pController;
    }

    /**
     * Creates a new worker instance.
     * @return New instance of {@link XmlRpcWorker}.
     */
    protected abstract XmlRpcWorker newWorker();

    /**
     * Returns the factory controller.
     * @return The controller
     */
    public XmlRpcController getController() {
        return controller;
    }

    /** Returns a worker for synchronous processing.
     * @return An instance of {@link XmlRpcWorker}, which is ready
     * for use.
     * @throws XmlRpcLoadException The clients maximum number of concurrent
     * threads is exceeded.
     */
    public synchronized XmlRpcWorker getWorker() throws XmlRpcLoadException {
        int max = controller.getMaxThreads();
        if (max > 0  &&  numThreads == max) {
            throw new XmlRpcLoadException("Maximum number of concurrent requests exceeded: " + max);
        }
        if (max == 0) {
            return singleton;
        }
        ++numThreads;
        if (pool.size() == 0) {
            return newWorker();
        } else {
            return pool.remove(pool.size() - 1);
        }
    }

    /** Called, when the worker did its job. Frees resources and
     * decrements the number of concurrent requests.
     * @param pWorker The worker being released.
     */
    public synchronized void releaseWorker(XmlRpcWorker pWorker) {
        --numThreads;
        int max = controller.getMaxThreads();
        if (pWorker != singleton) {
            if (pool.size() < max) {
                pool.add(pWorker);
            }
        }
    }

    /**
     * Returns the number of currently running requests.
     * @return Current number of concurrent requests.
     */
    public synchronized int getCurrentRequests() {
        return numThreads;
    }
}
