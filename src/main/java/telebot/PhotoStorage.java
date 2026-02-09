package telebot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PhotoStorage {
    public record Photo(String fileId, String caption) {}

    private final List<Photo> photos = new ArrayList<>();

    public PhotoStorage(String csvPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2); // split into 2 columns
                if (parts.length >= 1) {
                    String fileId = parts[0];
                    String caption = parts.length == 2 ? parts[1] : "";
                    photos.add(new Photo(fileId, caption));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Photo getRandomPhoto() {
        if (photos.isEmpty()) return null;
        return photos.get(new Random().nextInt(photos.size()));
    }
}
