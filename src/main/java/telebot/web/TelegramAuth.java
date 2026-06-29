package telebot.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Telegram Mini App {@code initData} and extracts the authenticated user id.
 *
 * <p>Follows Telegram's documented Web App data check: derive a secret key by HMAC-SHA256
 * of the bot token keyed by the constant {@code "WebAppData"}, then HMAC-SHA256 the sorted
 * {@code key=value} data-check-string and compare to the supplied {@code hash}. {@code auth_date}
 * is also checked for freshness.
 *
 * <p>When {@code devBypass} is set, the hash check is skipped (for local curl testing) and a
 * best-effort user id is returned (defaulting to 1).
 */
public class TelegramAuth {
    private static final Logger logger = LoggerFactory.getLogger(TelegramAuth.class);

    private static final Pattern USER_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final long MAX_AUTH_AGE_SECONDS = 24 * 60 * 60;
    private static final long DEV_USER_ID = 1L;

    private final String botToken;
    private final boolean devBypass;

    public TelegramAuth(String botToken, boolean devBypass) {
        this.botToken = botToken;
        this.devBypass = devBypass;
    }

    /**
     * Returns the authenticated Telegram user id, or {@code null} if {@code initData} is missing,
     * malformed, or fails validation.
     */
    public Long authenticate(String initData) {
        if (devBypass) {
            Long id = extractUserId(initData);
            return id != null ? id : DEV_USER_ID;
        }
        if (initData == null || initData.isEmpty()) {
            return null;
        }

        TreeMap<String, String> params = new TreeMap<>();
        String providedHash = null;
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            if (key.equals("hash")) {
                providedHash = value;
            } else {
                params.put(key, value);
            }
        }
        if (providedHash == null) {
            return null;
        }

        StringBuilder dataCheck = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (dataCheck.length() > 0) dataCheck.append('\n');
            dataCheck.append(e.getKey()).append('=').append(e.getValue());
        }

        try {
            byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                    botToken.getBytes(StandardCharsets.UTF_8));
            byte[] hash = hmacSha256(secretKey, dataCheck.toString().getBytes(StandardCharsets.UTF_8));
            if (!toHex(hash).equalsIgnoreCase(providedHash)) {
                logger.warn("Mini App initData hash mismatch");
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to verify Mini App initData", e);
            return null;
        }

        String authDate = params.get("auth_date");
        if (authDate != null) {
            try {
                long age = Instant.now().getEpochSecond() - Long.parseLong(authDate);
                if (age > MAX_AUTH_AGE_SECONDS) {
                    logger.warn("Mini App initData is stale ({}s old)", age);
                    return null;
                }
            } catch (NumberFormatException ignored) {
                // missing/garbled auth_date — treat as no freshness info, still accept the verified hash
            }
        }

        return extractUserId(params.get("user"));
    }

    /** Pulls the numeric {@code "id"} out of the (already decoded) {@code user} JSON blob. */
    private static Long extractUserId(String userJson) {
        if (userJson == null) return null;
        Matcher m = USER_ID_PATTERN.matcher(userJson);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
