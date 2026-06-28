package telebot.storage;

import telebot.model.Photo;

import java.util.Random;

/**
 * Stores the shared photo gallery: the {@code photos} list of Telegram file IDs
 * and a {@code <fileID> -> caption} string key per photo.
 */
public class PhotoRepository {
    private static final String PHOTOS_LIST_KEY = "photos";

    private final RedisConnection redis;
    private final Random random = new Random();

    public PhotoRepository(RedisConnection redis) {
        this.redis = redis;
    }

    public Photo getRandomPhoto() {
        long len = redis.jedis().llen(PHOTOS_LIST_KEY);
        String fileID = redis.jedis().lindex(PHOTOS_LIST_KEY, random.nextLong(len));
        return new Photo(fileID, redis.jedis().get(fileID));
    }

    public void uploadPhoto(String fileID, String caption) {
        redis.jedis().rpush(PHOTOS_LIST_KEY, fileID);
        redis.jedis().set(fileID, caption);
    }
}
