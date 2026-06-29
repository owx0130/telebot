package telebot;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import telebot.web.WebServer;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int DEFAULT_WEB_PORT = 8080;

    static void main() {
        Dotenv dotenv = Dotenv.load();
        String botToken = dotenv.get("BOT_TOKEN");
        String redisUrl = dotenv.get("REDIS_URL");
        int webPort = parsePort(dotenv.get("WEB_PORT"));
        boolean devBypassAuth = "true".equalsIgnoreCase(dotenv.get("WEBAPP_DEV_AUTH_BYPASS"));
        String ngrokAuthtoken = dotenv.get("NGROK_AUTHTOKEN");

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            WebServer webServer = new WebServer(webPort, botToken, redisUrl, devBypassAuth, ngrokAuthtoken);
            webServer.start();

            // Prefer the live ngrok HTTPS URL for the Mini App button; fall back to the local server URL.
            String publicUrl = webServer.getPublicUrl();
            String effectiveWebAppUrl = (publicUrl != null) ? publicUrl : "http://localhost:" + webPort;
            logger.info("Mini App button URL: {}", effectiveWebAppUrl);

            botsApplication.registerBot(botToken, new Bot(botToken, redisUrl, effectiveWebAppUrl));
            logger.info("Bot successfully started!");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to run bot!", e);
        }
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) return DEFAULT_WEB_PORT;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid WEB_PORT '{}', falling back to {}", value, DEFAULT_WEB_PORT);
            return DEFAULT_WEB_PORT;
        }
    }
}
