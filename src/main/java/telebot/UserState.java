package telebot;

public enum UserState {
    DEFAULT,
    AWAITING_PHOTO,
    AWAITING_CAPTION,
    UNKNOWN;

    public static UserState getEnumState(String state) {
        return switch (state) {
            case ("DEFAULT") -> UserState.DEFAULT;
            case ("AWAITING_PHOTO") -> UserState.AWAITING_PHOTO;
            case ("AWAITING_CAPTION") -> UserState.AWAITING_CAPTION;

            default -> UserState.UNKNOWN;
        };
    }

    public static String getStringState(UserState state) {
        return switch (state) {
            case UserState.DEFAULT -> "DEFAULT";
            case UserState.AWAITING_PHOTO -> "AWAITING_PHOTO";
            case UserState.AWAITING_CAPTION -> "AWAITING_CAPTION";

            default -> "";
        };
    }
}
