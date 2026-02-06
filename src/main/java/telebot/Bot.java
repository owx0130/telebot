package telebot;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;

    public Bot(String botToken) {
        telegramClient = new OkHttpTelegramClient(botToken);
    }

    public void consume(Update update) {
        // We check if the update has a message and the message has text
        if (update.hasMessage() && update.getMessage().hasText()) {
            // Set variables
            String message_text = update.getMessage().getText();
            long chat_id = update.getMessage().getChatId();

            if (message_text.equals("/cat")) {
                SendPhoto msg = SendPhoto
                    .builder()
                    .chatId(chat_id)
                    .photo(new InputFile("https://png.pngtree.com/background/20230519/original/pngtree-this-is-a-picture-of-a-tiger-cub-that-looks-straight-picture-image_2660243.jpg"))
                    .caption("This is a little cat :)")
                    .build();
                try {
                    telegramClient.execute(msg); // Call method to send the photo
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }

            else if (message_text.equals("/markup")) {
                SendMessage message = SendMessage // Create a message object object
                        .builder()
                        .chatId(chat_id)
                        .text("Here is your keyboard")
                        .build();

                // Add the keyboard to the message
                message.setReplyMarkup(ReplyKeyboardMarkup
                        .builder()
                        // Add first row of 3 buttons
                        .keyboardRow(new KeyboardRow("Row 1 Button 1", "Row 1 Button 2", "Row 1 Button 3"))
                        // Add second row of 3 buttons
                        .keyboardRow(new KeyboardRow("Row 2 Button 1", "Row 2 Button 2", "Row 2 Button 3"))
                        .build());
                try {
                    telegramClient.execute(message); // Sending our message object to user
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }

            SendMessage message = SendMessage // Create a message object
                .builder()
                .chatId(chat_id)
                .text("i love yu lin li")
                .build();
            try {
                telegramClient.execute(message); // Sending our message object to user
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
