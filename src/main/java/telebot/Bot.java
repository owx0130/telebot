package telebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private final TelegramClient telegramClient;
    private final Database db;

    public Bot(String botToken, String redisUrl) {
        telegramClient = new OkHttpTelegramClient(botToken);
        db = new Database(redisUrl);
        try {
            telegramClient.execute(new SetMyCommands(
                    List.of(
                            new BotCommand("random_photo", "Display a random photo"),
                            new BotCommand("upload_photo", "Upload a new photo")
                    ),
                    new BotCommandScopeDefault(),
                    null
            ));
        } catch (TelegramApiException e) {
            logger.error("Failed to set bot commands", e);
        }
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        long chatId = message.getChatId();

        db.addUser(chatId);
        UserState state = db.getUserState(chatId);

        if (state == UserState.AWAITING_PHOTO) {
            handleAwaitingPhotoState(chatId, message);
        } else if (state == UserState.AWAITING_CAPTION) {
            handleAwaitingCaptionState(chatId, message);
        } else if (message.hasText()) {
            handleText(chatId, message.getText());
        } else if (message.hasPhoto()) {
            handlePhoto(chatId);
        }
    }

    private void handleAwaitingPhotoState(long chatId, Message message) {
        if (message.hasPhoto()) {
            String fileID = message.getPhoto().getLast().getFileId();
            if (message.hasCaption()) {
                db.uploadPhoto(fileID, message.getCaption());
                db.setUserState(chatId, UserState.DEFAULT);
                sendText(chatId, "Photo has been uploaded!");
            } else {
                db.setUserStoredPhotoID(chatId, fileID);
                db.setUserState(chatId, UserState.AWAITING_CAPTION);
                sendText(chatId, "Do you want to input a caption? If so just send it in the next message! Otherwise just click /skip.");
            }
        } else if (message.hasText() && message.getText().equals("/cancel")) {
            db.setUserState(chatId, UserState.DEFAULT);
            sendText(chatId, "Upload photo operation cancelled!");
        } else {
            sendText(chatId, "Please send a photo to be uploaded! Or click /cancel to cancel operation.");
        }
    }

    private void handleAwaitingCaptionState(long chatId, Message message) {
        if (message.hasText()) {
            String fileID = db.getUserStoredPhotoID(chatId);
            String caption = message.getText().equals("/skip") ? "" : message.getText();
            db.uploadPhoto(fileID, caption);
            db.setUserStoredPhotoID(chatId, "");
            db.setUserState(chatId, UserState.DEFAULT);
            sendText(chatId, "Photo has been uploaded!");
        } else {
            sendText(chatId, "Please write a caption! Or click /skip to skip adding a caption.");
        }
    }

    private void handleText(long chatId, String text) {
        switch (text) {
            case "/random_photo" -> {
                Photo photo = db.getRandomPhoto();
                try {
                    telegramClient.execute(SendPhoto.builder()
                            .chatId(chatId)
                            .photo(new InputFile(photo.fileID()))
                            .caption(photo.caption())
                            .build());
                } catch (TelegramApiException e) {
                    logger.error("Failed to send random photo", e);
                }
            }
            case "/upload_photo" -> {
                db.setUserState(chatId, UserState.AWAITING_PHOTO);
                sendText(chatId, "Got it! Please send a photo (optionally with a caption) in your next message! If you want to cancel, click /cancel.");
            }
            default -> sendText(chatId, "Unknown command, but i love yu lin li");
        }
    }

    private void handlePhoto(long chatId) {
        sendText(chatId, "If you want to upload a photo, please click /upload_photo first!");
    }

    private void sendText(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}", chatId, e);
        }
    }
}
