package comapp.middleware;

import comapp.ConfigServlet;
import comapp.cloud.Genesys;
import comapp.cloud.GenesysUser;
import comapp.cloud.TrackId;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * REST endpoint exposed at /api/lead.
 *
 * Receives lead notifications from AWS API Gateway, validates and filters by
 * request type, prepares a clean data payload and forwards an ASSISTENZA
 * callback task to Genesys Cloud.
 *
 * Expected JSON structure sent by AWS API Gateway:
 * <pre>
 * {
 *   "requestBody": {
 *     "parameters": {
 *       "request_type": ["ASSISTENZA"],
 *       "phone_number":  ["0611234567"],
 *       "created_at":    ["2026-05-21T10:00:00"],
 *       "pod":           ["IT001E12345678"],
 *       "pdr":           ["11223344556677"]
 *     }
 *   }
 * }
 * </pre>
 *
 * AWS API Gateway wraps each value inside a single-element JSON array.
 * See {@link #extractSingleValue(JSONObject, String)} for the unwrapping logic.
 *
 * Properties read from {@link ConfigServlet}:
 * <ul>
 *   <li>{@code genesys.clientId}     – Genesys OAuth client ID</li>
 *   <li>{@code genesys.clientSecret} – Genesys OAuth client secret</li>
 *   <li>{@code genesys.urlRegion}    – Genesys region (default: mypurecloud.ie)</li>
 *   <li>{@code genesys.queueId}      – Target callback queue ID</li>
 * </ul>
 */
@WebServlet(
    name = "LeadIntegrationServlet",
    urlPatterns = "/api/lead"
)
public class LeadIntegrationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        TrackId trackId = TrackId.get(request.getSession(), true);
        trackId.logHeadersParameters(request, GenesysUser.log);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            GenesysUser.log.error("{} Failed to read request body", trackId, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to read request body");
            return;
        }

        try {
            String rawPayload = sb.toString();
            GenesysUser.log.info("{} Raw payload: {}", trackId, rawPayload);

            JSONObject root        = new JSONObject(rawPayload);
            JSONObject requestBody = root.getJSONObject("requestBody");
            JSONObject parameters  = requestBody.getJSONObject("parameters");

            String request_type = extractSingleValue(parameters, "request_type");
            String phone_number = extractSingleValue(parameters, "phone_number");
            String created_at   = extractSingleValue(parameters, "created_at");
            String pod          = extractSingleValue(parameters, "pod");
            String pdr          = extractSingleValue(parameters, "pdr");

            GenesysUser.log.info(
                    "{} Extracted fields: request_type={}, phone_number={}, created_at={}, pod={}, pdr={}",
                    trackId, request_type, phone_number, created_at, pod, pdr);

            if (request_type == null || phone_number == null || created_at == null) {
                GenesysUser.log.error(
                        "{} Missing required fields: request_type={}, phone_number={}, created_at={}",
                        trackId, request_type, phone_number, created_at);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required fields");
                return;
            }

            if (StringUtils.equalsIgnoreCase(request_type, "COMMERCIALE")) {
                GenesysUser.log.info(
                        "{} Commercial request skipped (request_type={})", trackId, request_type);
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            if (!StringUtils.equalsAnyIgnoreCase(
                    request_type, "ASSISTENZA", "ASSISTENZA SITO HC")) {
                GenesysUser.log.warn(
                        "{} Unknown request_type={} – request skipped", trackId, request_type);
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            JSONObject cleanData = new JSONObject();
            cleanData.put("phoneNumber", phone_number);
            cleanData.put("createdAt",   created_at);
            cleanData.put("requestType", request_type);
            if (pod != null) cleanData.put("pod", pod);
            if (pdr != null) cleanData.put("pdr", pdr);

            Properties props    = ConfigServlet.getProperties();
            String clientId     = props.getProperty("genesys.clientId",     "CLIENT_ID_PLACEHOLDER");
            String clientSecret = props.getProperty("genesys.clientSecret", "CLIENT_SECRET_PLACEHOLDER");
            String urlRegion    = props.getProperty("genesys.urlRegion",    "mypurecloud.ie");

            GenesysUser guser = new GenesysUser(
                    trackId.toString(), clientId, clientSecret, urlRegion, "", "");

            GenesysUser.log.info("{} cleanData: {}", trackId, cleanData);

            JSONObject routingData = new JSONObject();
            routingData.put("queueId", props.getProperty("genesys.queueId", ""));

            JSONArray callbackNumbers = new JSONArray();
            callbackNumbers.put(phone_number);

            JSONObject data = new JSONObject();
            if (cleanData.has("requestType")) data.put("requestType", cleanData.getString("requestType"));
            if (cleanData.has("pod"))         data.put("pod",         cleanData.getString("pod"));
            if (cleanData.has("pdr"))         data.put("pdr",         cleanData.getString("pdr"));

            JSONObject callbackBody = new JSONObject();
            callbackBody.put("routingData",      routingData);
            callbackBody.put("callbackNumbers",  callbackNumbers);
            callbackBody.put("callbackUserName", "HeraComm Lead");
            callbackBody.put("data",             data);

            GenesysUser.log.info("{} callbackBody: {}", trackId, callbackBody);

            String callbackUrl = "https://api."
                    + props.getProperty("genesys.urlRegion", urlRegion)
                    + "/api/v2/conversations/callbacks";

            try {
                org.apache.http.entity.StringEntity entity =
                        new org.apache.http.entity.StringEntity(callbackBody.toString(), "UTF-8");

                JSONObject result = Genesys.postJson(
                        trackId.toString(), guser, callbackUrl, entity,
                        "application/json; charset=UTF-8");

                if (result != null) {
                    GenesysUser.log.info("{} Lead successfully forwarded to Genesys. conversationId={}",
                            trackId, result.optString("id", "?"));
                } else {
                    GenesysUser.log.error("{} Genesys forwarding failed – null response", trackId);
                }
            } catch (Exception e) {
                GenesysUser.log.error("{} Error while forwarding to Genesys", trackId, e);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":\"SUCCESS\"}");

        } catch (Exception e) {
            GenesysUser.log.error("{} Unexpected error processing lead request", trackId, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * Unwraps a value from an AWS API Gateway parameter object.
     * AWS wraps every value inside a single-element JSON array, e.g. {@code ["VECCHI"]}.
     *
     * @param parameters the JSON object containing the parameters
     * @param key        the field name to extract
     * @return the string value, or {@code null} if the key is absent
     */
    private String extractSingleValue(JSONObject parameters, String key) {
        if (parameters == null || !parameters.has(key)) {
            return null;
        }
        Object value = parameters.get(key);
        if (value instanceof JSONArray arr) {
            return arr.length() > 0 ? arr.getString(0) : null;
        }
        return value.toString();
    }
}