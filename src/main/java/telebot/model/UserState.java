package telebot.model;

public enum UserState {
    DEFAULT,
    AWAITING_PHOTO,
    AWAITING_CAPTION,
    AWAITING_WORDLE_GUESS,
    UNKNOWN;

    public static UserState fromString(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return UNKNOWN;
        }
    }
}
