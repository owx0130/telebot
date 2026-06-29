package telebot;

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import telebot.handler.PhotoHandler;
import telebot.handler.WordleHandler;
import telebot.model.UserState;
import telebot.storage.RedisConnection;
import telebot.storage.UserStateStore;
import telebot.telegram.Messenger;

/**
 * Composition root and update router. Wires the storage, messaging, and feature
 * handlers together, then dispatches each incoming {@link Update} to the right
 * handler based on the user's {@link UserState} and message content.
 */
public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private final UserStateStore userStates;
    private final Messenger messenger;
    private final PhotoHandler photoHandler;
    private final WordleHandler wordleHandler;

    public Bot(String botToken, String redisUrl, String webAppUrl) {
        RedisConnection redis = new RedisConnection(redisUrl);
        this.userStates = new UserStateStore(redis);

        this.messenger = new Messenger(botToken);
        messenger.registerCommands();

        this.photoHandler = new PhotoHandler(messenger, userStates, redis);
        this.wordleHandler = new WordleHandler(messenger, webAppUrl);
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        long chatId = message.getChatId();

        userStates.addUser(chatId);
        UserState state = userStates.getUserState(chatId);

        if (state == UserState.AWAITING_PHOTO) {
            photoHandler.handleAwaitingPhoto(chatId, message);
        } else if (state == UserState.AWAITING_CAPTION) {
            photoHandler.handleAwaitingCaption(chatId, message);
        } else if (message.hasText()) {
            handleCommand(chatId, message.getText());
        } else if (message.hasPhoto()) {
            photoHandler.handleUnexpectedPhoto(chatId);
        }
    }

    private void handleCommand(long chatId, String text) {
        switch (text) {
            case "/random_photo" -> photoHandler.sendRandomPhoto(chatId);
            case "/upload_photo" -> photoHandler.startUpload(chatId);
            case "/wordle" -> wordleHandler.start(chatId, true);
            case "/wordle_easy" -> wordleHandler.start(chatId, false);
            default -> messenger.sendText(chatId, "Unknown command, but i love yu lin li");
        }
    }
}
