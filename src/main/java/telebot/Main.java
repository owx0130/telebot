package telebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static void main() {
        String botToken = System.getenv("BOT_TOKEN");
        String redisUrl = System.getenv("REDIS_URL");

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new Bot(botToken, redisUrl));
            logger.info("Bot successfully started!");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to run bot!", e);
        }
    }
}
