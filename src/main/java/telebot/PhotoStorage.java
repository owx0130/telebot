package telebot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhotoStorage {
    public record Photo(String fileId, String caption) {}

    private static final Logger logger = LoggerFactory.getLogger(PhotoStorage.class);

    private final List<Photo> photos = new ArrayList<>();

    public PhotoStorage(String csvPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(","); // split into 2 columns
                String fileId = parts[0];
                String caption = parts[1];
                photos.add(new Photo(fileId, caption));
            }
        } catch (Exception e) {
            logger.error("Failed to set up photos database", e);
        }
    }

    public Photo getRandomPhoto() {
        if (photos.isEmpty()) return null;
        return photos.get(new Random().nextInt(photos.size()));
    }
}
