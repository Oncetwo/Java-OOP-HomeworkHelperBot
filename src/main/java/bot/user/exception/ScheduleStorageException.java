package bot.user.exception;
// чтобы бизнес-логика не знала о конкретных исключениях, а знала только то, что исключеение может возникнуть
public class ScheduleStorageException extends RuntimeException {
    public ScheduleStorageException(String message) { // Создает исключение с текстовым сообщением
        super(message); // Передает сообщение в конструктор RuntimeException
    }
    
    public ScheduleStorageException(String message, Throwable cause) { // Создает исключение с сообщением И причиной
        super(message, cause);
    }
}