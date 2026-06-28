package telebot.handler;

import org.telegram.telegrambots.meta.api.objects.message.Message;
import telebot.model.Photo;
import telebot.model.UserState;
import telebot.storage.PhotoRepository;
import telebot.storage.RedisConnection;
import telebot.storage.UserStateStore;
import telebot.telegram.Messenger;

/**
 * Owns the photo-gallery feature: starting an upload, the two-step upload flow
 * (photo then optional caption), serving a random photo, and the reminder shown
 * when a photo arrives without {@code /upload_photo}.
 */
public class PhotoHandler {
    private final Messenger messenger;
    private final UserStateStore userStates;
    private final PhotoRepository photos;

    public PhotoHandler(Messenger messenger, UserStateStore userStates, RedisConnection redis) {
        this.messenger = messenger;
        this.userStates = userStates;
        this.photos = new PhotoRepository(redis);
    }

    /** {@code /upload_photo}: begin the upload flow. */
    public void startUpload(long chatId) {
        userStates.setUserState(chatId, UserState.AWAITING_PHOTO);
        messenger.sendText(chatId, "Got it! Please send a photo (optionally with a caption) in your next message! If you want to cancel, click /cancel.");
    }

    /** {@code /random_photo}: send a random photo from the gallery with its caption. */
    public void sendRandomPhoto(long chatId) {
        Photo photo = photos.getRandomPhoto();
        messenger.sendPhoto(chatId, photo.fileID(), photo.caption());
    }

    /** AWAITING_PHOTO: accept the photo (with caption → done, without → ask for caption) or /cancel. */
    public void handleAwaitingPhoto(long chatId, Message message) {
        if (message.hasPhoto()) {
            String fileID = message.getPhoto().getLast().getFileId();
            if (message.hasCaption()) {
                photos.uploadPhoto(fileID, message.getCaption());
                userStates.setUserState(chatId, UserState.DEFAULT);
                messenger.sendText(chatId, "Photo has been uploaded!");
            } else {
                userStates.setUserStoredPhotoID(chatId, fileID);
                userStates.setUserState(chatId, UserState.AWAITING_CAPTION);
                messenger.sendText(chatId, "Do you want to input a caption? If so just send it in the next message! Otherwise just click /skip.");
            }
        } else if (message.hasText() && message.getText().equals("/cancel")) {
            userStates.setUserState(chatId, UserState.DEFAULT);
            messenger.sendText(chatId, "Upload photo operation cancelled!");
        } else {
            messenger.sendText(chatId, "Please send a photo to be uploaded! Or click /cancel to cancel operation.");
        }
    }

    /** AWAITING_CAPTION: accept caption text or /skip, then finalise the upload. */
    public void handleAwaitingCaption(long chatId, Message message) {
        if (message.hasText()) {
            String fileID = userStates.getUserStoredPhotoID(chatId);
            String caption = message.getText().equals("/skip") ? "" : message.getText();
            photos.uploadPhoto(fileID, caption);
            userStates.setUserStoredPhotoID(chatId, "");
            userStates.setUserState(chatId, UserState.DEFAULT);
            messenger.sendText(chatId, "Photo has been uploaded!");
        } else {
            messenger.sendText(chatId, "Please write a caption! Or click /skip to skip adding a caption.");
        }
    }

    /** DEFAULT state: a photo arrived without an upload flow in progress. */
    public void handleUnexpectedPhoto(long chatId) {
        messenger.sendText(chatId, "If you want to upload a photo, please click /upload_photo first!");
    }
}
