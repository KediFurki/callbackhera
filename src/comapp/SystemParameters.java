package comapp;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing system-wide parameters.
 *
 * Lookup order for {@link #getParameter(String, String)}:
 * <ol>
 *   <li>In-memory map (set programmatically via {@link #setParameter} or
 *       bulk-loaded via {@link #loadProperties}).</li>
 *   <li>Properties file loaded by {@link ConfigServlet} (if initialised).</li>
 *   <li>The supplied {@code defaultValue}.</li>
 * </ol>
 */
public class SystemParameters {

    private static final ConcurrentHashMap<String, String> parameters = new ConcurrentHashMap<>();

    private SystemParameters() {}

    /**
     * Returns the value for the given key, falling back to the ConfigServlet
     * properties file and finally to {@code defaultValue}.
     *
     * @param key          parameter name
     * @param defaultValue value returned when the key is not found
     * @return resolved value
     */
    public static String getParameter(String key, String defaultValue) {
        String value = parameters.get(key);
        if (value != null) {
            return value;
        }
        try {
            Properties props = ConfigServlet.getProperties();
            if (props != null) {
                value = props.getProperty(key);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // ConfigServlet may not be initialised yet during early startup
        }
        return defaultValue;
    }

    /**
     * Sets a parameter value in the in-memory map.
     *
     * @param key   parameter name
     * @param value parameter value
     */
    public static void setParameter(String key, String value) {
        if (key != null && value != null) {
            parameters.put(key, value);
        }
    }

    /**
     * Bulk-loads all entries from a {@link Properties} object into the
     * in-memory map. Called by {@link ConfigServlet} during startup.
     *
     * @param props source properties
     */
    public static void loadProperties(Properties props) {
        if (props != null) {
            for (String key : props.stringPropertyNames()) {
                parameters.put(key, props.getProperty(key));
            }
        }
    }

    /**
     * Removes a parameter from the in-memory map.
     *
     * @param key parameter name
     */
    public static void removeParameter(String key) {
        parameters.remove(key);
    }

    /**
     * Returns {@code true} if the key exists in the in-memory map.
     *
     * @param key parameter name
     * @return {@code true} if present
     */
    public static boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
}