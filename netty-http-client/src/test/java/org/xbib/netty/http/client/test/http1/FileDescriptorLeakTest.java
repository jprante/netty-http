package org.xbib.netty.http.client.test.http1;

import com.sun.management.UnixOperatingSystemMXBean;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.logging.Level;
import java.util.logging.Logger;

@Disabled
class FileDescriptorLeakTest {

    private static final Logger logger = Logger.getLogger(FileDescriptorLeakTest.class.getName());

    @Test
    void testFileLeak() throws Exception {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        for (int i = 0; i< 10; i++) {
            if (os instanceof UnixOperatingSystemMXBean) {
                logger.info("before: number of open file descriptor : " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
            }
            try (Client client = Client.builder().setThreadCount(1).build()) {
                Request request = Request.get().url("http://xbib.org")
                        .setResponseListener(resp -> {
                            logger.log(Level.INFO, "status = " + resp.getStatus());
                        })
                        .build();
                client.execute(request);
            }
            if (os instanceof UnixOperatingSystemMXBean){
                logger.info("after: number of open file descriptor : " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
            }
        }
    }
}
