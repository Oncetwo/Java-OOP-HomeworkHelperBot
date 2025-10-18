package bot.user;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

 // SQLite - легковесная база данных, которая хранится в одном файле

public class SQLiteUserStorage implements UserStorageInterface {
    
    private static final String DATABASE_URL = "jdbc:sqlite:users.db"; 
    // "jdbc:sqlite:" - протокол подключения к SQLite
    // "users.db" - имя файла базы данных (создастся в корне проекта)
    // переменная, которая хранит строку-адрес для подключения к бд
    
    @Override
    public void initialize() { // Инициализирует базу данных, т.е. создает таблицу, если она не существует.
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                "chat_id INTEGER PRIMARY KEY, " +      
                "name TEXT, " +                        
                "group_name TEXT, " +                  
                "state TEXT NOT NULL" + // Состояние регистрации (не может быть NULL)
                ")";
        
        try (Connection connection = DriverManager.getConnection(DATABASE_URL); // try автоматически закрывает Connection и Statement
        		// connection это тип - соединение с базой данных
        		// DriverManager - это класс из библиотеки
        		// getConnection - метод получения соединения с бд
        		
             Statement statement = connection.createStatement()) {
             // Statement - тип отправителя SQL команд
        	 // createStatement - метод создания отправителя команд
        	
            statement.execute(createTableSQL); // Выполняем SQL команду создания таблицы
            
        } 
        catch (SQLException e) {
            System.err.println("Ошибка инициализации базы данных: " + e.getMessage());
        }
    }
    

    @Override
    public User getUser(long chatId) { // Получает пользователя из базы данных по ID чата.
        // SQL запрос с параметром (?) для защиты от SQL-инъекций
        String sql = "SELECT * FROM users WHERE chat_id = ?";
        
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            // Подставляем значение параметра (заменяем ? на actual chatId)
            statement.setLong(1, chatId);
            
            // Выполняем запрос и получаем результат
            ResultSet resultSet = statement.executeQuery();
            
            // Если пользователь найден, создаем объект User
            if (resultSet.next()) {
                return createUserFromResultSet(resultSet);
            }
            
        } catch (SQLException e) {
            System.err.println("Ошибка получения пользователя: " + e.getMessage());
        }
        
        return null; // Пользователь не найден
    }
    
    /**
     * Сохраняет пользователя в базу данных.
     * INSERT OR REPLACE - если пользователь с таким chat_id существует, он будет обновлен
     */
    @Override
    public void saveUser(User user) {
        String sql = "INSERT OR REPLACE INTO users (chat_id, name, group_name, state) VALUES (?, ?, ?, ?)";
        
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            // Устанавливаем значения для каждого параметра в запросе
            statement.setLong(1, user.getChatId());
            statement.setString(2, user.getName());
            statement.setString(3, user.getGroup());
            statement.setString(4, user.getState().name());  // Enum преобразуем в String
            
            // Выполняем запрос (для INSERT/UPDATE/DELETE используем executeUpdate)
            statement.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка сохранения пользователя: " + e.getMessage());
        }
    }
    
    /**
     * Обновляет данные пользователя в базе данных.
     * В SQLite INSERT OR REPLACE работает как обновление при совпадении chat_id
     */
    @Override
    public void updateUser(User user) {
        // Используем тот же метод, что и для сохранения
        saveUser(user);
    }
    
    /**
     * Удаляет пользователя из базы данных.
     */
    @Override
    public void deleteUser(long chatId) {
        String sql = "DELETE FROM users WHERE chat_id = ?";
        
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setLong(1, chatId);
            statement.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка удаления пользователя: " + e.getMessage());
        }
    }
    
    /**
     * Проверяет существование пользователя в базе данных.
     */
    @Override
    public boolean userExists(long chatId) {
        return getUser(chatId) != null;
    }
    
    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Создает объект User из ResultSet (результата SQL запроса).
     * @param resultSet результат выполнения SQL запроса
     * @return объект User
     * @throws SQLException если произошла ошибка при чтении данных
     */
    private User createUserFromResultSet(ResultSet resultSet) throws SQLException {
        // Получаем значения из колонок ResultSet
        Long chatId = resultSet.getLong("chat_id");
        String name = resultSet.getString("name");
        String group = resultSet.getString("group_name");
        
        // Преобразуем строку обратно в Enum
        RegistrationState state = RegistrationState.valueOf(resultSet.getString("state"));
        
        return new User(chatId, name, group, state);
    }
}