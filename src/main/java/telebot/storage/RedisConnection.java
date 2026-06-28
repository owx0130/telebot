package telebot.storage;

import redis.clients.jedis.Jedis;

import java.net.URI;

/**
 * Owns the single shared {@link Jedis} connection and the Redis key conventions.
 * Repositories take a {@code RedisConnection} and operate through {@link #jedis()}.
 */
public class RedisConnection implements AutoCloseable {
    private static final String USER_PREFIX = "user_";

    private final Jedis jedis;

    public RedisConnection(String redisUrl) {
        this.jedis = new Jedis(URI.create(redisUrl));
    }

    public Jedis jedis() {
        return jedis;
    }

    /** Redis key for a user's hash (state, stored photo ID, Wordle session). */
    public String userKey(long chatId) {
        return USER_PREFIX + chatId;
    }

    @Override
    public void close() {
        jedis.close();
    }
}
