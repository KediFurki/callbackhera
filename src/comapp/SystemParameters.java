package comapp;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing system-wide parameters.
 * Parameters can be set programmatically or loaded from a Properties object.
 */
public class SystemParameters {

    private static final ConcurrentHashMap<String, String> parameters = new ConcurrentHashMap<>();

    private SystemParameters() {
        // Utility class, no instantiation
    }

    /**
     * Returns the value of the given parameter, or defaultValue if not set.
     *
     * @param key          parameter name
     * @param defaultValue value to return if parameter is not set
     * @return parameter value or default
     */
    public static String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    /**
     * Sets a parameter value.
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
     * Loads parameters from a Properties object.
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
     * Removes a parameter.
     *
     * @param key parameter name to remove
     */
    public static void removeParameter(String key) {
        parameters.remove(key);
    }

    /**
     * Checks if a parameter is set.
     *
     * @param key parameter name
     * @return true if the parameter exists
     */
    public static boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
}
