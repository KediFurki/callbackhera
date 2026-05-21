package comapp.middleware.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * End-to-end smoke test for {@code LeadIntegrationServlet}.
 *
 * Run this class directly (e.g. from Eclipse "Run As → Java Application")
 * while Tomcat is running locally to verify the full pipeline:
 * JSON parsing → business-rule filtering → Genesys callback POST.
 *
 * No external libraries required – pure java.net / java.io only.
 */
public class LeadTester {

    private static final Logger log = LogManager.getLogger(LeadTester.class);

    public static void main(String[] args) {

        // Target URL – update port or context path if your Tomcat setup differs
        // Default: http://localhost:8080/<context-root>/api/lead
        String targetUrl = "http://localhost:8080/lead-middleware/api/lead";

        // Simulates the exact JSON structure produced by AWS API Gateway
        // (every field value is wrapped in a single-element array)
        String jsonInputString =
                "{\"requestBody\": {\"parameters\": {"
                + "\"request_type\": [\"ASSISTENZA\"], "
                + "\"phone_number\": [\"+393400000000\"], "
                + "\"created_at\": [\"2026-02-13 16:40:42\"], "
                + "\"pod\": [\"IT001E31273318\"], "
                + "\"pdr\": [\"15350120801985\"]}}}";

        log.info("=== LeadTester START ===");
        log.info("Target URL : {}", targetUrl);
        log.info("Request body: {}", jsonInputString);

        try {
            URL url = new URL(targetUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            log.info("Sending POST request...");

            // Write the request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input);
            }

            // Read the HTTP response code
            int responseCode = connection.getResponseCode();
            log.info("HTTP Response Code: {}", responseCode);
            System.out.println("HTTP Response Code: " + responseCode);

            // Use error stream for non-2xx responses
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 200 && responseCode <= 299
                                    ? connection.getInputStream()
                                    : connection.getErrorStream(),
                            StandardCharsets.UTF_8));

            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line).append(System.lineSeparator());
            }
            reader.close();

            String body = responseBody.toString().trim();
            log.info("Server response: {}", body);
            System.out.println("Server response: " + body);

            if (responseCode >= 200 && responseCode <= 299) {
                log.info("=== LeadTester SUCCESS ===");
            } else {
                log.error("=== LeadTester FAILED – HTTP {} ===", responseCode);
            }

        } catch (Exception e) {
            log.error("=== LeadTester ERROR ===", e);
            e.printStackTrace();
        }
    }
}