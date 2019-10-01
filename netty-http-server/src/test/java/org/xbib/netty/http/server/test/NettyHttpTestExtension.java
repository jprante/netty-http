package org.xbib.netty.http.server.test;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.security.Security;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class NettyHttpTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        if (Security.getProvider("BC") == null) {
            // for insecure trust manager
            Security.addProvider(new BouncyCastleProvider());
        }
        System.setProperty("io.netty.noUnsafe", Boolean.toString(true));
        //System.setProperty("io.netty.leakDetection.level", "paranoid");
        Level level = Level.INFO;
        //Level level = Level.ALL;
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %5$s %6$s%n");
        LogManager.getLogManager().reset();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
        rootLogger.setLevel(level);
        for (Handler h : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter());
            h.setLevel(level);
        }
    }
}
