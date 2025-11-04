package bot.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(long userId) {
        super("Пользователь не найден: " + userId);
    }
    
    public UserNotFoundException(String message) {
        super(message);
    }
}
