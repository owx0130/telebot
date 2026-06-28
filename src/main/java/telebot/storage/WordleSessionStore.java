package telebot.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores the in-progress Wordle session (answer, guesses, hard-mode flag) on the
 * same {@code user_<id>} hash. Absent fields read back as sensible defaults, so a
 * freshly created user needs no Wordle seeding.
 */
public class WordleSessionStore {
    private static final String ANSWER_FIELD = "wordleAnswer";
    private static final String GUESSES_FIELD = "wordleGuesses";
    private static final String HARD_MODE_FIELD = "wordleHardMode";

    private final RedisConnection redis;

    public WordleSessionStore(RedisConnection redis) {
        this.redis = redis;
    }

    public String getWordleAnswer(long chatId) {
        return redis.jedis().hget(redis.userKey(chatId), ANSWER_FIELD);
    }

    public void setWordleAnswer(long chatId, String answer) {
        redis.jedis().hset(redis.userKey(chatId), ANSWER_FIELD, answer);
    }

    public List<String> getWordleGuesses(long chatId) {
        String raw = redis.jedis().hget(redis.userKey(chatId), GUESSES_FIELD);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(raw.split(",")));
    }

    public void setWordleGuesses(long chatId, List<String> guesses) {
        redis.jedis().hset(redis.userKey(chatId), GUESSES_FIELD, String.join(",", guesses));
    }

    public boolean isWordleHardMode(long chatId) {
        return "true".equals(redis.jedis().hget(redis.userKey(chatId), HARD_MODE_FIELD));
    }

    public void setWordleHardMode(long chatId, boolean hardMode) {
        redis.jedis().hset(redis.userKey(chatId), HARD_MODE_FIELD, String.valueOf(hardMode));
    }

    public void clearWordleSession(long chatId) {
        redis.jedis().hset(redis.userKey(chatId), Map.of(
                ANSWER_FIELD, "",
                GUESSES_FIELD, "",
                HARD_MODE_FIELD, "false"
        ));
    }
}
