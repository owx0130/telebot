package telebot;

import redis.clients.jedis.Jedis;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class Database {
    private static final String USER_PREFIX = "user_";
    private static final String USER_STATE_FIELD = "state";
    private static final String USER_STORED_PHOTO_ID_FIELD = "storedPhotoID";
    private static final String PHOTOS_SET_NAME = "photos";

    private final Jedis jedis;

    public Database(String redisUrl) {
        jedis = new Jedis(URI.create(redisUrl));
    }

    public void addUser(long chat_id) {
        String user_key = USER_PREFIX + chat_id;
        if (!jedis.exists(user_key)) {
            Map<String, String> user_info = new HashMap<>();
            user_info.put(USER_STATE_FIELD, "DEFAULT");
            user_info.put(USER_STORED_PHOTO_ID_FIELD, "");

            jedis.hset(user_key, user_info);
        }
    }

    public UserState getUserState(long chat_id) {
        String user_key = USER_PREFIX + chat_id;
        String state = jedis.hget(user_key, USER_STATE_FIELD);
        return UserState.getEnumState(state);
    }

    public void setUserState(long chat_id, UserState state) {
        String user_key = USER_PREFIX + chat_id;
        String string_state = UserState.getStringState(state);
        jedis.hset(user_key, USER_STATE_FIELD, string_state);
    }

    public String getUserStoredPhotoID(long chat_id) {
        String user_key = USER_PREFIX + chat_id;
        return jedis.hget(user_key, USER_STORED_PHOTO_ID_FIELD);
    }

    public void setUserStoredPhotoID(long chat_id, String fileID) {
        String user_key = USER_PREFIX + chat_id;
        jedis.hset(user_key, USER_STORED_PHOTO_ID_FIELD, fileID);
    }

    public Photo getRandomPhoto() {
        String fileID = jedis.srandmember(PHOTOS_SET_NAME);
        String caption = jedis.get(fileID);
        return new Photo(fileID, caption);
    }

    public void uploadPhoto(String fileID, String caption) {
        jedis.sadd(PHOTOS_SET_NAME, fileID);
        jedis.set(fileID, caption);
    }

    public record Photo(String fileID, String caption) {
    }
}
