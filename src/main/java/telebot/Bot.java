package telebot;

import java.util.List;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private static final Logger log = LoggerFactory.getLogger(Bot.class);
    private static final List<BotCommand> commands = List.of(
        new BotCommand("/upload_pic", "Upload a new picture to the database :)"),
        new BotCommand("/cat", "Get a cute cat")
    );
    private static PhotoStorage photo_db = new PhotoStorage("photos.csv");

    public Bot(String botToken) throws TelegramApiException {
        telegramClient = new OkHttpTelegramClient(botToken);
        try {
            telegramClient.execute(
                new SetMyCommands(commands, new BotCommandScopeDefault(), null)
            );
        } catch (TelegramApiException e) {
            log.error("Failed to set bot commands", e);
        }
    }

    public void consume(Update update) {
        // We check if the update has a message and the message has text
        if (update.hasMessage()) {
            if (update.getMessage().hasText()) {
                // Set variables
                String message_text = update.getMessage().getText();
                long chat_id = update.getMessage().getChatId();

                if (message_text.equals("/cat")) {
                    PhotoStorage.Photo photo = photo_db.getRandomPhoto();

                    SendPhoto msg = SendPhoto
                            .builder()
                            .chatId(chat_id)
                            .photo(new InputFile(photo.fileId()))
                            .caption(photo.caption())
                            .build();
                    try {
                        telegramClient.execute(msg);
                    } catch (TelegramApiException e) {
                        log.error("failed to send cat picture", e);
                    }
                }
                else {
                    SendMessage message = SendMessage // Create a message object
                            .builder()
                            .chatId(chat_id)
                            .text("i love yu lin li")
                            .build();
                    try {
                        telegramClient.execute(message); // Sending our message object to user
                    } catch (TelegramApiException e) {
                        log.error("failed to send default message", e);
                    }
                }
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                PhotoSize photo = update.getMessage().getPhoto()
                        .get(update.getMessage().getPhoto().size() - 1);
                System.out.println("file_id: " + photo.getFileId());
            }
        }
    }
}
