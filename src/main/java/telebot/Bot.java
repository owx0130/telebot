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
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
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
        List<BotCommand> commands = List.of(
                new BotCommand("random_pic", "Display a random picture"),
                new BotCommand("upload_pic", "Upload a new picture")
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

        // Add user to database. If user already exists, do nothing
        db.addUser(chat_id);
        UserState state = db.getUserState(chat_id);

        // Handle non-default user states, otherwise handle regular text and photos separately
        if (!state.equals(UserState.DEFAULT)) {
            handleStates(chat_id, state, message);
        } else if (message.hasText()) {
            handleText(chat_id, message.getText());
        } else if (message.hasPhoto()) {
            handlePhoto(chat_id, message.getPhoto().getLast());
        }
    }

    private void handleStates(long chat_id, UserState state, Message message) {
        if (state.equals(UserState.AWAITING_PHOTO)) {
            if (message.hasPhoto()) {
                // Ask user if they want to add a caption if they did not input one
                if (!message.hasCaption()) {
                    SendMessage reply = SendMessage.builder()
                            .chatId(chat_id)
                            .text("Do you want to input a caption? If so just send it in the next message! Otherwise just click /skip.")
                            .build();
                    try {
                        telegramClient.execute(reply);
                    } catch (TelegramApiException e) {
                        logger.error("Failed to send asking for caption reply", e);
                    }
                    db.setUserStoredPhotoID(chat_id, message.getPhoto().getLast().getFileId());
                    db.setUserState(chat_id, UserState.AWAITING_CAPTION);
                } else {
                    SendMessage reply = SendMessage.builder()
                            .chatId(chat_id)
                            .text("Photo has been uploaded!")
                            .build();
                    try {
                        telegramClient.execute(reply);
                    } catch (TelegramApiException e) {
                        logger.error("Failed to send true AWAITING_PHOTO reply", e);
                    }
                    db.setUserState(chat_id, UserState.DEFAULT);
                }
            } else if (message.getText().equals("/cancel")) {
                SendMessage reply = SendMessage.builder()
                        .chatId(chat_id)
                        .text("Upload picture operation cancelled!")
                        .build();
                try {
                    telegramClient.execute(reply);
                } catch (TelegramApiException e) {
                    logger.error("Failed to send cancel AWAITING_PHOTO reply", e);
                }
                db.setUserState(chat_id, UserState.DEFAULT);
            }
            else {
                SendMessage reply = SendMessage.builder()
                        .chatId(chat_id)
                        .text("Please send a photo to be uploaded! Or click /cancel to cancel operation.")
                        .build();
                try {
                    telegramClient.execute(reply);
                } catch (TelegramApiException e) {
                    logger.error("Failed to send false AWAITING_PHOTO reply", e);
                }
            }
        } else if (state.equals(UserState.AWAITING_CAPTION)) {
            if (message.hasText()) {
                String fileID = db.getUserStoredPhotoID(chat_id);
                String caption = message.getText();

                SendMessage reply = SendMessage.builder()
                        .chatId(chat_id)
                        .text("Photo has been uploaded!")
                        .build();
                try {
                    telegramClient.execute(reply);
                } catch (TelegramApiException e) {
                    logger.error("Failed to send true AWAITING_CAPTION reply", e);
                }
                db.uploadPhoto(fileID, caption);
                db.setUserState(chat_id, UserState.DEFAULT);
                db.setUserStoredPhotoID(chat_id, "");
            } else {
                SendMessage reply = SendMessage.builder()
                        .chatId(chat_id)
                        .text("Please write a caption! Or click /cancel to cancel operation.")
                        .build();
                try {
                    telegramClient.execute(reply);
                } catch (TelegramApiException e) {
                    logger.error("Failed to send false AWAITING_CAPTION reply", e);
                }
            }
        }
    }

    private void handleText(long chat_id, String text) {
        if (text.equals("/random_pic")) {
            Database.Photo photo = db.getRandomPhoto();
            SendPhoto reply = SendPhoto.builder()
                    .chatId(chat_id)
                    .photo(new InputFile(photo.fileID()))
                    .caption(photo.caption())
                    .build();
            try {
                telegramClient.execute(reply);
            } catch (TelegramApiException e) {
                logger.error("Failed to send random_pic reply", e);
            }
        } else if (text.equals("/upload_pic")) {
            SendMessage reply = SendMessage.builder()
                    .chatId(chat_id)
                    .text("Got it! Please send a photo (optionally with captions) in your next message!")
                    .build();
            try {
                telegramClient.execute(reply);
            } catch (TelegramApiException e) {
                logger.error("Failed to send upload_pic reply", e);
            }
            db.setUserState(chat_id, UserState.AWAITING_PHOTO);
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
