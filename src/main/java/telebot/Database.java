package telebot;

import redis.clients.jedis.Jedis;

import java.net.URI;
import java.util.Map;
import java.util.Random;

public class Database {
    private static final String USER_PREFIX = "user_";
    private static final String USER_STATE_FIELD = "state";
    private static final String USER_STORED_PHOTO_ID_FIELD = "storedPhotoID";
    private static final String PHOTOS_LIST_KEY = "photos";

    private final Random random = new Random();
    private final Jedis jedis;

    public Database(String redisUrl) {
        jedis = new Jedis(URI.create(redisUrl));
    }

    private String userKey(long chatId) {
        return USER_PREFIX + chatId;
    }

    public void addUser(long chatId) {
        String key = userKey(chatId);
        if (!jedis.exists(key)) {
            jedis.hset(key, Map.of(
                    USER_STATE_FIELD, UserState.DEFAULT.name(),
                    USER_STORED_PHOTO_ID_FIELD, ""
            ));
        }
    }

    public UserState getUserState(long chatId) {
        return UserState.fromString(jedis.hget(userKey(chatId), USER_STATE_FIELD));
    }

    public void setUserState(long chatId, UserState state) {
        jedis.hset(userKey(chatId), USER_STATE_FIELD, state.name());
    }

    public String getUserStoredPhotoID(long chatId) {
        return jedis.hget(userKey(chatId), USER_STORED_PHOTO_ID_FIELD);
    }

    public void setUserStoredPhotoID(long chatId, String fileID) {
        jedis.hset(userKey(chatId), USER_STORED_PHOTO_ID_FIELD, fileID);
    }

    public Photo getRandomPhoto() {
        long len = jedis.llen(PHOTOS_LIST_KEY);
        String fileID = jedis.lindex(PHOTOS_LIST_KEY, random.nextLong(len));
        return new Photo(fileID, jedis.get(fileID));
    }

    public void uploadPhoto(String fileID, String caption) {
        jedis.rpush(PHOTOS_LIST_KEY, fileID);
        jedis.set(fileID, caption);
    }
}
