package telebot.handler;

import telebot.telegram.Messenger;

/**
 * Telegram-facing controller for the Wordle feature. The game itself is a Telegram Mini App
 * (a hosted web page); this handler only launches it by sending a {@code web_app} button.
 * All game rules and session state live behind the web API (see {@code telebot.web}).
 */
public class WordleHandler {
    private final Messenger messenger;
    private final String webAppBaseUrl;

    public WordleHandler(Messenger messenger, String webAppBaseUrl) {
        this.messenger = messenger;
        this.webAppBaseUrl = webAppBaseUrl;
    }

    /** {@code /wordle} (hard) or {@code /wordle_easy}: send a button that opens the Mini App. */
    public void start(long chatId, boolean hardMode) {
        if (webAppBaseUrl == null || webAppBaseUrl.isEmpty()) {
            messenger.sendText(chatId, "Wordle isn't configured right now. Please try again later.");
            return;
        }
        String url = hardMode ? withParam(webAppBaseUrl, "hard=true") : webAppBaseUrl;
        messenger.sendWebAppButton(chatId,
                "Tap to play Wordle — " + (hardMode ? "Hard Mode" : "Easy") + "!",
                "🟩 Play Wordle",
                url);
    }

    private static String withParam(String url, String param) {
        return url + (url.contains("?") ? "&" : "?") + param;
    }
}
