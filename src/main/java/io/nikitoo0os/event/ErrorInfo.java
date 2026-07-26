package io.nikitoo0os.event;

public record ErrorInfo(String type, String message) {
    public static final int MAX_MESSAGE_LENGTH = 512;

    public static ErrorInfo from(Throwable failure) {
        if (failure == null) {
            return null;
        }
        String message = failure.getMessage();
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        return new ErrorInfo(failure.getClass().getName(), message);
    }
}
