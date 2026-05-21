package comapp.middleware;

import comapp.ConfigServlet;
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
 * request type, prepares a clean data payload and (TODO) forwards an
 * ASSISTENZA callback task to Genesys Cloud.
 *
 * Expected JSON structure sent by AWS:
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
 * Note: AWS API Gateway wraps each value inside a JSON array – see
 * {@link #extractSingleValue(JSONObject, String)} for the unwrapping logic.
 *
 * Properties read from {@link ConfigServlet}:
 * <ul>
 *   <li>{@code genesys.clientId}     – Genesys OAuth client ID</li>
 *   <li>{@code genesys.clientSecret} – Genesys OAuth client secret</li>
 *   <li>{@code genesys.urlRegion}    – Genesys region (default: mypurecloud.ie)</li>
 * </ul>
 */
@WebServlet(
    name = "LeadIntegrationServlet",
    urlPatterns = "/api/lead"
)
public class LeadIntegrationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // ─────────────────────────────────────────────────────────────────────────
    // Görev 1 + 2 + 3 + 4 – doPost
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // ── Görev 1: İzleme altyapısını başlat ───────────────────────────────
        TrackId trackId = TrackId.get(request.getSession(), true);
        trackId.logHeadersParameters(request, GenesysUser.log);

        // Gelen ham payload'u oku
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            GenesysUser.log.error("{} Payload okunurken hata oluştu", trackId, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Request body okunamadı");
            return;
        }

        try {
            String rawPayload = sb.toString();
            GenesysUser.log.info("{} Ham payload: {}", trackId, rawPayload);

            // ── Görev 2: AWS JSON'unu çözümleme ve dizi temizleme ─────────────
            JSONObject root        = new JSONObject(rawPayload);
            JSONObject requestBody = root.getJSONObject("requestBody");
            JSONObject parameters  = requestBody.getJSONObject("parameters");

            String request_type = extractSingleValue(parameters, "request_type");
            String phone_number = extractSingleValue(parameters, "phone_number");
            String created_at   = extractSingleValue(parameters, "created_at");
            String pod          = extractSingleValue(parameters, "pod");
            String pdr          = extractSingleValue(parameters, "pdr");

            GenesysUser.log.info(
                    "{} Çıkarılan alanlar → request_type={}, phone_number={}, " +
                    "created_at={}, pod={}, pdr={}",
                    trackId, request_type, phone_number, created_at, pod, pdr);

            // ── Görev 3: Validasyon ve iş kuralları ───────────────────────────

            // Zorunlu alan kontrolü
            if (request_type == null || phone_number == null || created_at == null) {
                GenesysUser.log.error(
                        "{} Zorunlu alanlar eksik – request_type={}, phone_number={}, created_at={}",
                        trackId, request_type, phone_number, created_at);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Zorunlu alanlar eksik");
                return;
            }

            // Ticari talep filtresi: sessizce 200 döndür, Genesys'e gönderme
            if (StringUtils.equalsIgnoreCase(request_type, "COMMERCIALE")) {
                GenesysUser.log.info(
                        "{} Talep ticari olduğu için atlandı (request_type={})",
                        trackId, request_type);
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            // Yalnızca ASSISTENZA türleri devam eder
            if (!StringUtils.equalsAnyIgnoreCase(
                    request_type, "ASSISTENZA", "ASSISTENZA SITO HC")) {
                GenesysUser.log.warn(
                        "{} Bilinmeyen request_type={} – talep atlanıyor",
                        trackId, request_type);
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            // ── Görev 4: Genesys veri hazırlığı ve başarılı yanıt ─────────────

            // Temiz veri nesnesi oluştur
            JSONObject cleanData = new JSONObject();
            cleanData.put("phoneNumber", phone_number);
            cleanData.put("createdAt",   created_at);
            cleanData.put("requestType", request_type);
            if (pod != null) cleanData.put("pod", pod);
            if (pdr != null) cleanData.put("pdr", pdr);

            // Genesys kimlik bilgilerini dış yapılandırmadan oku
            Properties props       = ConfigServlet.getProperties();
            String clientId        = props.getProperty("genesys.clientId",     "CLIENT_ID_PLACEHOLDER");
            String clientSecret    = props.getProperty("genesys.clientSecret", "CLIENT_SECRET_PLACEHOLDER");
            String urlRegion       = props.getProperty("genesys.urlRegion",    "mypurecloud.ie");

            GenesysUser guser = new GenesysUser(
                    trackId.toString(),
                    clientId,
                    clientSecret,
                    urlRegion,
                    "",
                    ""
            );

            GenesysUser.log.info("{} cleanData:\n{}", trackId, cleanData.toString(2));

            // TODO: Genesys callback oluştur
            // String queueId   = props.getProperty("genesys.queueId", "");
            // String cbUserName = phone_number;
            // Genesys.createCallBack(trackId.toString(), guser, phone_number, queueId, cbUserName);

            // AWS'ye başarılı yanıt dön
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":\"SUCCESS\"}");

        } catch (Exception e) {
            GenesysUser.log.error("{} Lead isteği işlenirken beklenmedik hata", trackId, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Sunucu tarafında bir hata oluştu");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı metod
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AWS API Gateway, değerleri tek elemanlı JSON dizileri olarak gönderir
     * (ör. {@code "user_surname": ["VECCHI"]}).
     * Bu metod diziyi açarak saf string değeri döndürür.
     *
     * @param parameters JSON alan nesnesi
     * @param key        aranacak alan adı
     * @return string değeri ya da alan yoksa {@code null}
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
