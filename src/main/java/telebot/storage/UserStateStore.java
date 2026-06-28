package telebot.storage;

import telebot.model.UserState;

import java.util.Map;

/**
 * Stores the core per-user fields on the {@code user_<id>} hash: the conversation
 * {@link UserState} and the photo ID temporarily held during an upload.
 */
public class UserStateStore {
    private static final String STATE_FIELD = "state";
    private static final String STORED_PHOTO_ID_FIELD = "storedPhotoID";

    private final RedisConnection redis;

    public UserStateStore(RedisConnection redis) {
        this.redis = redis;
    }

    /** Initializes the user hash on first contact; leaves an existing user untouched. */
    public void addUser(long chatId) {
        String key = redis.userKey(chatId);
        if (!redis.jedis().exists(key)) {
            redis.jedis().hset(key, Map.of(
                    STATE_FIELD, UserState.DEFAULT.name(),
                    STORED_PHOTO_ID_FIELD, ""
            ));
        }
    }

    public UserState getUserState(long chatId) {
        return UserState.fromString(redis.jedis().hget(redis.userKey(chatId), STATE_FIELD));
    }

    public void setUserState(long chatId, UserState state) {
        redis.jedis().hset(redis.userKey(chatId), STATE_FIELD, state.name());
    }

    public String getUserStoredPhotoID(long chatId) {
        return redis.jedis().hget(redis.userKey(chatId), STORED_PHOTO_ID_FIELD);
    }

    public void setUserStoredPhotoID(long chatId, String fileID) {
        redis.jedis().hset(redis.userKey(chatId), STORED_PHOTO_ID_FIELD, fileID);
    }
}
