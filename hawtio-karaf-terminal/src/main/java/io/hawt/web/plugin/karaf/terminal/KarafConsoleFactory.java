package io.hawt.web.plugin.karaf.terminal;

import java.io.PipedInputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.threadio.ThreadIO;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf is incompatible between 2.2 and 2.x and also between 2.x and 3.x versions.
 * So we need to do some reflection to support all versions.
 */
public class KarafConsoleFactory {

    private final static Logger LOG = LoggerFactory.getLogger(KarafConsoleFactory.class);

    private static final String KARAF3_CONSOLE_CLASSNAME = "org.apache.karaf.shell.console.Console";
    private static final String KARAF2_CONSOLE_CLASSNAME = "org.apache.karaf.shell.console.jline.Console";

    public static final int TERM_WIDTH = 120;
    public static final int TERM_HEIGHT = 400;

    public static CommandSession getSession(Object console) {
        try {
            return (CommandSession) console.getClass().getMethod("getSession", null).invoke(console);
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public static void close(Object console, boolean param) {
        try {
            console.getClass().getMethod("close", boolean.class).invoke(console, param);
        } catch (Exception e) {
            // ignore
        }
    }

    public static Object createConsole(CommandProcessor commandProcessor,
                                       PipedInputStream in,
                                       PrintStream pipedOut,
                                       ThreadIO threadIO,
                                       BundleContext bundleContext) throws Exception {

        Class<?> clazz2 = null;
        Class<?> clazz3 = null;
        try {
            clazz2 = bundleContext.getBundle().loadClass(KARAF2_CONSOLE_CLASSNAME);
            if (clazz2 == null) {
                clazz3 = bundleContext.getBundle().loadClass(KARAF3_CONSOLE_CLASSNAME);
            }
        } catch (Exception e) {
            // ignore
        }

        if (clazz2 == null && clazz3 == null) {
            throw new ClassNotFoundException("Cannot load Console class for either Karaf 2.x or 3.x");
        }

        if (clazz2 != null) {
            Constructor ctr = clazz2.getConstructors()[0];
            if (ctr.getParameterTypes().length <= 7) {
                LOG.debug("Using old Karaf Console API");

                // last parameter may be BundleContext if its redhat version of karaf
                // for ASF releases its a Runnable, and we should pass in null
                Object last = null;
                Class<?> type = ctr.getParameterTypes()[6];
                if (type != null && type.getSimpleName().equals("BundleContext")) {
                    last = bundleContext;
                }

                // the old API does not have the threadIO parameter, so its only 7 parameters
                return ctr.newInstance(commandProcessor,
                        in,
                        pipedOut,
                        pipedOut,
                        new WebTerminal(TERM_WIDTH, TERM_HEIGHT),
                        null,
                        last);
            } else {
                LOG.debug("Using new Karaf Console API");

                // last parameter may be BundleContext if its redhat version of karaf
                // for ASF releases its a Runnable, and we should pass in null
                Object last = null;
                Class<?> type = ctr.getParameterTypes()[8];
                if (type != null && type.getSimpleName().equals("BundleContext")) {
                    last = bundleContext;
                }

                // the new API has the threadIO parameter, so it has 9 parameters
                return ctr.newInstance(commandProcessor,
                        threadIO,
                        in,
                        pipedOut,
                        pipedOut,
                        new WebTerminal(TERM_WIDTH, TERM_HEIGHT),
                        null,
                        null,
                        last);
            }
        }

        if (clazz3 != null) {
            Constructor ctr = clazz3.getConstructors()[0];
        }

        throw new ClassNotFoundException("Cannot load Console class for either Karaf 2.x or 3.x");
    }

}
