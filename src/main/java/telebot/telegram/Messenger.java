package telebot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * The single point of contact with the Telegram send API. Owns the
 * {@link TelegramClient}, swallows/logs {@link TelegramApiException}, and registers
 * the bot's slash commands.
 */
public class Messenger {
    private static final Logger logger = LoggerFactory.getLogger(Messenger.class);

    private final TelegramClient telegramClient;

    public Messenger(String botToken) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    /** Registers the bot's slash-command menu shown in Telegram clients. */
    public void registerCommands() {
        try {
            telegramClient.execute(new SetMyCommands(
                    List.of(
                            new BotCommand("random_photo", "Display a random photo"),
                            new BotCommand("upload_photo", "Upload a new photo"),
                            new BotCommand("wordle", "Play Wordle (hard mode)"),
                            new BotCommand("wordle_easy", "Play Wordle (normal rules)")
                    ),
                    new BotCommandScopeDefault(),
                    null
            ));
        } catch (TelegramApiException e) {
            logger.error("Failed to set bot commands", e);
        }
    }

    public void sendText(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}", chatId, e);
        }
    }

    /**
     * Sends a message with a single inline button that opens a Telegram Mini App (Web App).
     * Web-app inline buttons are permitted in private chats, which is this bot's use case.
     */
    public void sendWebAppButton(long chatId, String text, String buttonText, String url) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(buttonText)
                .webApp(WebAppInfo.builder().url(url).build())
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(button))
                .build();
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to send web app button to chat {}", chatId, e);
        }
    }

    public void sendPhoto(long chatId, String fileID, String caption) {
        try {
            telegramClient.execute(SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(fileID))
                    .caption(caption)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to send photo to chat {}", chatId, e);
        }
    }
}
