package org.xbib.netty.http.common.test;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.NetworkUtils;
import java.util.logging.Level;
import java.util.logging.Logger;

class NetworkUtilsTest {

    @Test
    void testInterfaces() throws InterruptedException {
        Logger.getLogger("test").log(Level.INFO, NetworkUtils.getNetworkInterfacesAsString());
    }
}
