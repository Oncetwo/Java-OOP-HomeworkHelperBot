package bot.user;

import java.util.List;

public interface UserStorageInterface {
    
    User getUser(long chatId); // Получает пользователя по идентификатору чата.
    
    void saveUser(User user); // Сохраняет или обновляет пользователя в хранилище
    
    void updateUser(User user); // Обновляет данные существующего пользователя.
    
    void deleteUser(long chatId); // Удаляет пользователя из хранилища.
    
    boolean userExists(long chatId); // Проверяет существование пользователя в хранилище.
    
    void initialize(); // Инициализирует хранилище (создает таблицы, подключается к БД и т.д.)
}