package telebot.handler;

import org.telegram.telegrambots.meta.api.objects.message.Message;
import telebot.game.Wordle;
import telebot.model.UserState;
import telebot.storage.RedisConnection;
import telebot.storage.UserStateStore;
import telebot.storage.WordleSessionStore;
import telebot.telegram.Messenger;

import java.util.List;

/**
 * Telegram-facing controller for the Wordle feature: starts a game, validates and
 * records guesses, renders the board, and detects the win. Pure game rules live in
 * {@link Wordle}; session persistence in {@link WordleSessionStore}.
 */
public class WordleHandler {
    private final Messenger messenger;
    private final UserStateStore userStates;
    private final WordleSessionStore sessions;
    private final Wordle wordle;

    public WordleHandler(Messenger messenger, UserStateStore userStates, RedisConnection redis) {
        this.messenger = messenger;
        this.userStates = userStates;
        this.sessions = new WordleSessionStore(redis);
        this.wordle = new Wordle();
    }

    /** {@code /wordle} (hard) or {@code /wordle_easy}: fetch today's word and start a session. */
    public void start(long chatId, boolean hardMode) {
        String answer = Wordle.fetchSolution();
        if (answer == null) {
            messenger.sendText(chatId, "Sorry, I couldn't fetch today's Wordle. Please try again later.");
            return;
        }
        sessions.setWordleAnswer(chatId, answer);
        sessions.setWordleGuesses(chatId, List.of());
        sessions.setWordleHardMode(chatId, hardMode);
        userStates.setUserState(chatId, UserState.AWAITING_WORDLE_GUESS);
        messenger.sendText(chatId, "Wordle started — " + (hardMode ? "Hard Mode" : "Easy") + "!\n"
                + "Guess the 5-letter word. You have unlimited guesses.\n"
                + (hardMode ? "Hard mode: revealed hints must be reused in later guesses.\n" : "")
                + "Send a word to guess, or /cancel to quit.");
    }

    /** AWAITING_WORDLE_GUESS: validate the guess, update the board, and check for a win or /cancel. */
    public void handleGuess(long chatId, Message message) {
        if (!message.hasText()) {
            messenger.sendText(chatId, "Please send a 5-letter word to guess, or /cancel to quit.");
            return;
        }

        String text = message.getText().trim();
        if (text.equals("/cancel")) {
            sessions.clearWordleSession(chatId);
            userStates.setUserState(chatId, UserState.DEFAULT);
            messenger.sendText(chatId, "Wordle game cancelled!");
            return;
        }

        String guess = text.toLowerCase();
        if (guess.length() != Wordle.WORD_LENGTH || !guess.chars().allMatch(Character::isLetter)) {
            messenger.sendText(chatId, "Please send a valid 5-letter word.");
            return;
        }
        if (!wordle.isValidWord(guess)) {
            messenger.sendText(chatId, "\"" + text + "\" is not in the word list. Try another word.");
            return;
        }

        String answer = sessions.getWordleAnswer(chatId);
        boolean hardMode = sessions.isWordleHardMode(chatId);
        List<String> guesses = sessions.getWordleGuesses(chatId);

        if (hardMode) {
            String violation = Wordle.hardModeViolation(guesses, answer, guess);
            if (violation != null) {
                messenger.sendText(chatId, violation + " Try another word.");
                return;
            }
        }

        guesses.add(guess);
        sessions.setWordleGuesses(chatId, guesses);
        messenger.sendText(chatId, Wordle.renderBoard(guesses, answer, hardMode));

        if (guess.equals(answer)) {
            messenger.sendText(chatId, "🎉 You got it in " + guesses.size()
                    + (guesses.size() == 1 ? " guess!" : " guesses!"));
            sessions.clearWordleSession(chatId);
            userStates.setUserState(chatId, UserState.DEFAULT);
        }
    }
}
