package comapp;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing system-wide parameters.
 * <p>
 * Lookup order for {@link #getParameter(String, String)}:
 * <ol>
 *   <li>In-memory map (set programmatically via {@link #setParameter}).</li>
 *   <li>Properties file loaded by {@link ConfigServlet} (if available).</li>
 *   <li>The supplied {@code defaultValue}.</li>
 * </ol>
 */
public class SystemParameters {

    private static final ConcurrentHashMap<String, String> parameters = new ConcurrentHashMap<>();

    private SystemParameters() {
        // Utility class, no instantiation
    }

    /**
     * Returns the value of the given parameter, or {@code defaultValue} if not set.
     * Checks the in-memory map first, then the ConfigServlet properties file.
     *
     * @param key          parameter name
     * @param defaultValue value to return if parameter is not found anywhere
     * @return resolved parameter value
     */
    public static String getParameter(String key, String defaultValue) {
        // 1. In-memory map (highest priority)
        String value = parameters.get(key);
        if (value != null) {
            return value;
        }

        // 2. Properties file loaded by ConfigServlet
        try {
            Properties props = ConfigServlet.getProperties();
            if (props != null) {
                value = props.getProperty(key);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // ConfigServlet may not be initialised yet (e.g. during early startup)
        }

        // 3. Default
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
     * Loads all entries from a {@link Properties} object into the in-memory map.
     * Called by {@link ConfigServlet} during startup.
     *
     * @param props Properties to load
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
     * @param key parameter name to remove
     */
    public static void removeParameter(String key) {
        parameters.remove(key);
    }

    /**
     * Checks if a parameter is set in the in-memory map.
     *
     * @param key parameter name
     * @return {@code true} if the parameter exists in the in-memory map
     */
    public static boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
}