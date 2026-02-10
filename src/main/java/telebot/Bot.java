package telebot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private final TelegramClient telegramClient;
    private final PhotoStorage photo_db = new PhotoStorage("photos.csv");
    private final Map<Long, UserState> userStateMap = new ConcurrentHashMap<>();

    public Bot(String botToken) {
        telegramClient = new OkHttpTelegramClient(botToken);
        List<BotCommand> commands = List.of(
                new BotCommand("/random_pic", "Display a random picture"),
                new BotCommand("/upload_pic", "Upload a new picture")
        );
        try {
            telegramClient.execute(
                    new SetMyCommands(commands, new BotCommandScopeDefault(), null)
            );
        } catch (TelegramApiException e) {
            logger.error("Failed to set bot commands", e);
        }
    }

    public void consume(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        long chat_id = message.getChatId();
        UserState state = getUserState(chat_id);

        // Handle non-default user states, otherwise handle regular text and photos separately
        if (!state.equals(UserState.DEFAULT)) {
            handleStates(chat_id, state, message);
        } else if (message.hasText()) {
            handleText(chat_id, message.getText());
        } else if (message.hasPhoto()) {
            handlePhoto(chat_id, message.getPhoto().getLast());
        }
    }

    private UserState getUserState(long chatId) {
        return userStateMap.getOrDefault(chatId, UserState.DEFAULT);
    }

    private void setUserState(long chatId, UserState state) {
        userStateMap.put(chatId, state);
    }

    private void handleStates(long chat_id, UserState state,Message message) {
        if (state.equals(UserState.AWAITING_PHOTO)) {
            if (message.hasPhoto()) {
                SendMessage reply = SendMessage.builder()
                        .chatId(chat_id)
                        .text("Photo has been uploaded!")
                        .build();
                try {
                    telegramClient.execute(reply);
                } catch (TelegramApiException e) {
                    logger.error("Failed to send true AWAITING_PHOTO reply", e);
                }
                setUserState(chat_id, UserState.DEFAULT);
            } else {
                SendMessage reply = SendMessage.builder()
                        .chatId(chat_id)
                        .text("Please send a photo to be uploaded!")
                        .build();
                try {
                    telegramClient.execute(reply);
                } catch (TelegramApiException e) {
                    logger.error("Failed to send false AWAITING_PHOTO reply", e);
                }
            }
        }
    }

    private void handleText(long chat_id, String text) {
        if (text.equals("/random_pic")) {
            PhotoStorage.Photo photo = photo_db.getRandomPhoto();
            SendPhoto reply = SendPhoto.builder()
                    .chatId(chat_id)
                    .photo(new InputFile(photo.fileId()))
                    .caption(photo.caption())
                    .build();
            try {
                telegramClient.execute(reply);
            } catch (TelegramApiException e) {
                logger.error("Failed to send random_pic reply", e);
            }

        } else if (text.equals("/upload_pic")) {
            setUserState(chat_id, UserState.AWAITING_PHOTO);
            SendMessage reply = SendMessage.builder()
                    .chatId(chat_id)
                    .text("Got it! Please send a photo (optionally with captions) in your next message!")
                    .build();
            try {
                telegramClient.execute(reply);
            } catch (TelegramApiException e) {
                logger.error("Failed to send upload_pic reply", e);
            }
        } else {
            SendMessage reply = SendMessage.builder()
                    .chatId(chat_id)
                    .text("Unknown command, but i love yu lin li")
                    .build();
            try {
                telegramClient.execute(reply);
            } catch (TelegramApiException e) {
                logger.error("Failed to send default reply", e);
            }
        }
    }

    private void handlePhoto(long chat_id, PhotoSize photo) {
        System.out.println(photo.getFileId());
    }
}
