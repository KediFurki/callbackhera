package comapp;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Startup servlet that loads external .properties and configures log4j2.
 *
 * Configuration file location is determined by the environment variable
 * CONFIG_DIR (defaults to C:/Comapp/Config). The file name is derived from
 * the web-application context path: <CONFIG_DIR>/<web_app>.properties
 *
 * The properties file may contain:
 *   log4j2-properties-location = /path/to/log4j2.xml  (or .properties)
 *   environment = prod
 *   ... any other key=value pairs used by SystemParameters
 */
@WebServlet(
    name = "ConfigServlet",
    urlPatterns = "/ConfigServlet",
    loadOnStartup = 1
)
public class ConfigServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Application version read from MANIFEST.MF (Implementation-Version). */
    public static String version = "1.0.0";

    /** Full path to the .properties file, e.g. C:/Comapp/Config/lead-middleware.properties */
    public static String ConfigLocation;

    /** Context path without leading slash, e.g. "lead-middleware". */
    public static String web_app;

    public static Logger log = LogManager.getLogger("comapp");

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        ServletContext ctx = config.getServletContext();

        // Derive application name from context path
        String contextPath = ctx.getContextPath();
        if (contextPath != null && !contextPath.isBlank()) {
            web_app = contextPath.replace("/", "");
        } else {
            web_app = "lead-middleware";
        }

        // Resolve config directory
        String base = System.getenv("CONFIG_DIR");
        if (base == null || base.isBlank()) {
            base = "C:/Comapp/Config";
            log.warn("==== CONFIG_DIR environment variable not found! ====");
            log.warn("==== Falling back to default path: {} ====", base);
        } else {
            log.info("==== Using CONFIG_DIR: {} ====", base);
        }
        if (!base.endsWith("/") && !base.endsWith("\\")) {
            base = base + File.separator;
        }

        ConfigLocation = base + web_app + ".properties";

        // Read implementation version from MANIFEST.MF
        try (InputStream is = ctx.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                Manifest mf = new Manifest(is);
                String v = mf.getMainAttributes().getValue("Implementation-Version");
                if (v != null && !v.isBlank()) {
                    version = v;
                }
            }
        } catch (IOException e) {
            log.warn("Could not read MANIFEST.MF", e);
        }

        // Load properties and configure log4j2
        Properties props = getProperties();
        if (props != null && !props.isEmpty()) {

            // Push all properties into SystemParameters so the rest of the app can use them
            SystemParameters.loadProperties(props);

            // Optionally reconfigure log4j2
            String log4jPath = props.getProperty("log4j2-properties-location");
            if (log4jPath != null && !log4jPath.isBlank()) {
                File f = new File(log4jPath);
                if (f.exists()) {
                    try {
                        LoggerContext lc = (LoggerContext) LogManager.getContext(false);
                        lc.setConfigLocation(f.toURI());
                        lc.reconfigure();
                        log = LogManager.getLogger("comapp");
                        log.info("Loaded log4j2 config from {}", f.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("Error during log4j2 reconfiguration", e);
                    }
                } else {
                    log.warn("log4j2 config file not found: {}", f.getAbsolutePath());
                }
            } else {
                log.warn("Property 'log4j2-properties-location' not set in {}", ConfigLocation);
            }
        } else {
            log.warn("No properties loaded from {}", ConfigLocation);
        }

        log.info("==== comapp START ==== web_app={}, version={}, config={}",
                web_app, version, ConfigLocation);
    }

    @Override
    public void destroy() {
        log.info("==== comapp STOP ==== web_app={}", web_app);
        super.destroy();
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    /**
     * Loads and returns the properties from {@link #ConfigLocation}.
     * Returns an empty {@link Properties} object if the file does not exist.
     */
    public static Properties getProperties() {
        Properties p = new Properties();
        if (ConfigLocation == null || ConfigLocation.isBlank()) {
            return p;
        }
        File file = new File(ConfigLocation);
        if (!file.exists()) {
            log.warn("Properties file not found: {}", ConfigLocation);
            return p;
        }
        try (InputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            log.error("Error loading properties from {}", ConfigLocation, e);
        }
        return p;
    }

    /**
     * Saves (or updates) a single key-value pair in the properties file.
     *
     * @param key   property key
     * @param value property value
     */
    public static void saveProperties(String key, String value) {
        if (ConfigLocation == null || ConfigLocation.isBlank()) {
            log.error("ConfigLocation is null – cannot save property '{}'", key);
            return;
        }
        Properties p = getProperties();
        p.setProperty(key, value);
        try (OutputStream out = new FileOutputStream(ConfigLocation)) {
            p.store(out, null);
            // Keep SystemParameters in sync
            SystemParameters.setParameter(key, value);
            log.info("Saved property '{}' to {}", key, ConfigLocation);
        } catch (IOException e) {
            log.error("Error saving properties to {}", ConfigLocation, e);
        }
    }
}
