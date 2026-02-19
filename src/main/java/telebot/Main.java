package telebot;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import java.net.InetSocketAddress;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static void main() {
        String botToken = System.getenv("BOT_TOKEN");
        String redisUrl = System.getenv("REDIS_URL");
        int port = Integer.parseInt(System.getenv("PORT"));

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.start();

            System.out.println("Dummy server running on port " + port);
        } catch (Exception e) {
            logger.error("Dummy server failed to start!");
        }

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new Bot(botToken, redisUrl));
            logger.info("Bot successfully started!");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to run bot!", e);
        }
    }
}
