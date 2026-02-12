package telebot;

import redis.clients.jedis.Jedis;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class Database {
    private static final String USER_PREFIX = "user_";

    private final Jedis jedis;

    public Database(String redisUrl) {
        jedis = new Jedis(URI.create(redisUrl));
    }

    public void addUser(long chat_id) {
        String user_key = USER_PREFIX + chat_id;
        if (!jedis.exists(user_key)) {
            Map<String, String> user_info = new HashMap<>();
            user_info.put("state", "DEFAULT");
            user_info.put("storedPhotoID", "");

            jedis.hset(user_key, user_info);
        }
    }

    public UserState getUserState(long chat_id) {
        String user_key = USER_PREFIX + chat_id;
        String state = jedis.hget(user_key, "state");
        return UserState.getEnumState(state);
    }

    public void setUserState(long chat_id, UserState state) {
        String user_key = USER_PREFIX + chat_id;
        String string_state = UserState.getStringState(state);
        jedis.hset(user_key, "state", string_state);
    }

    public String getUserStoredPhotoID(long chat_id) {
        String user_key = USER_PREFIX + chat_id;
        return jedis.hget(user_key, "storedPhotoID");
    }

    public void setUserStoredPhotoID(long chat_id, String fileID) {
        String user_key = USER_PREFIX + chat_id;
        jedis.hset(user_key, "storedPhotoID", fileID);
    }

    public Photo getRandomPhoto() {
        String fileID = jedis.randomKey();
        while (fileID.startsWith(USER_PREFIX)) {
            fileID = jedis.randomKey();
        }
        String caption = jedis.get(fileID);
        return new Photo(fileID, caption);
    }

    public void uploadPhoto(String fileID, String caption) {
        jedis.set(fileID, caption);
    }

    public record Photo(String fileID, String caption) {
    }
}
