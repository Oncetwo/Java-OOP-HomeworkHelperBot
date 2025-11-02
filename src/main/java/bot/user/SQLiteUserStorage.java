package bot.user;

import java.sql.*;

public class SQLiteUserStorage implements UserStorage {
    private final String DB_URL = "jdbc:sqlite:users.db"; // Путь к файлу базы данных
    private Connection connection;

    
    @Override
    public void initialize() { // Инициализация (создание таблицы, если её нет)
        try {
            connection = DriverManager.getConnection(DB_URL); // октрываем соединение с бд по указанному адресу
            String sql = "CREATE TABLE IF NOT EXISTS users (" + // формирование sql запроса для создания таблицы
                         "chatId INTEGER PRIMARY KEY," +
                         "name TEXT," +
                         "groupName TEXT," +
                         "state TEXT," +
                         "waitingForButton INTEGER," +
                         "hasCustomSchedule INTEGER DEFAULT 0" +
                         ")";
            
            // вызываем у объекта connection метод, который создает объект типа: отправитель запросов
            Statement statment = connection.createStatement(); 
            statment.execute(sql); // вызываем метод execute (объекта statment), который выполняет запрос
            statment.close(); // закрываем statment (запрос на создание отправили, он больше не нужен)
            
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка инициализации базы данных", e); 
            // непроверяемое исключение (чтобы остановить выполнение программы с сообщением
        }
    }

    
    @Override 
    public User getUser(long chatId) { // Получить пользователя по chatId (возвращает объект типа юзер)
        try {
            String sql = "SELECT * FROM users WHERE chatId = ?"; // "выбрать все поля из таблицы users, где находится заданное id
            // PreparedStatment - тип отправителя запросов, который позволяет использовать параметры
            PreparedStatement pstatment = connection.prepareStatement(sql); // создание запроса на основе строки sql 
            pstatment.setLong(1, chatId);  // в первый ? подставить chatId
            
            ResultSet result = pstatment.executeQuery(); // результат выполнения sql запроса (возвращает объект ResultSet, т.е. возвращает данные)
            
            if (result.next()) { // перемещает курсор к следующей строке в результате запроса
                String name = result.getString("name");
                String group = result.getString("groupName");
                
                String stateStr = result.getString("state");
                RegistrationState state = RegistrationState.valueOf(stateStr); // valueOf возвращает элемент перечисления
                
                boolean waitingForButton = result.getInt("waitingForButton") == 1; // если значение в колонке совпало с 1, то вернет true, иначе false
                boolean hasCustomSchedule = result.getInt("hasCustomSchedule") == 1;
                		
                
                result.close();
                pstatment.close();
                
                User user = new User(chatId, name, group, state);
                user.setWaitingForButton(waitingForButton); 
                user.setHasCustomSchedule(hasCustomSchedule);
                return user;
            }
            result.close();
            pstatment.close();
            return null;
            
        } catch (SQLException e) {
            throw new RuntimeException("Ощибка получения пользователя по id", e);
        }
    }

    
    @Override
    public void saveUser(User user) { // сохранить нового пользователя
        if (userExists(user.getChatId())) { // проверка, существует ли пользователь уже
            throw new RuntimeException("Пользователь уже существует");
        }
        try {
            String sql = "INSERT INTO users (chatId, name, groupName, state, waitingForButton, hasCustomSchedule) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement pstatment = connection.prepareStatement(sql); // создание запроса на основе строки sql
            
            pstatment.setLong(1, user.getChatId());
            pstatment.setString(2, user.getName());
            pstatment.setString(3, user.getGroup());
            pstatment.setString(4, user.getState().name());
            pstatment.setInt(5, user.getWaitingForButton() ? 1 : 0);
            pstatment.setInt(6, user.getHasCustomSchedule() ? 1 : 0);
            
            pstatment.executeUpdate(); // выполнение запроса, который изменяет данные
            
            pstatment.close();
            
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка сохранения пользователя", e);
        }
    }

   
    @Override
    public void updateUser(User user) { // обновить пользователя
        if (!userExists(user.getChatId())) { // проверка на существование пользоваетля
            throw new RuntimeException("Пользоваетля с таким ID еще не существует в базе данных");
        }
        try {
            String sql = "UPDATE users SET name = ?, groupName = ?, state = ?, waitingForButton = ?, hasCustomSchedule = ? WHERE chatId = ?";  // обновляет поля пользователя с указанным ID
            PreparedStatement pstatment = connection.prepareStatement(sql);
            
            pstatment.setString(1, user.getName());
            pstatment.setString(2, user.getGroup());
            pstatment.setString(3, user.getState().name());
            pstatment.setInt(4, user.getWaitingForButton() ? 1 : 0);
            pstatment.setInt(5, user.getHasCustomSchedule() ? 1 : 0);
            pstatment.setLong(6, user.getChatId());
            
            pstatment.executeUpdate();
            
            pstatment.close();
            
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка обновления данных пользователя", e);
        }
    }

   
    @Override
    public void deleteUser(long chatId) { // Удалить пользователя
        try {
            String sql = "DELETE FROM users WHERE chatId = ?"; // удалить строку с определенным Id из 
            PreparedStatement pstatment = connection.prepareStatement(sql);
            
            pstatment.setLong(1, chatId);
            
            pstatment.executeUpdate();
            
            pstatment.close();
            
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка удаления пользователя", e);
        }
    }

    
    @Override
    public boolean userExists(long chatId) { // Проверить, существует ли пользователь
        try {
            String sql = "SELECT 1 FROM users WHERE chatId = ?"; // возвращает 1, если строка с заданным ID найдена в бд
            PreparedStatement pstatment = connection.prepareStatement(sql);
            
            pstatment.setLong(1, chatId);
            
            ResultSet result = pstatment.executeQuery(); // результат выполнения sql запроса (возвращает объект ResultSet, т.е. возвращает данные)
            boolean exists = result.next(); // перемещает курсор к следующей строке в результате запроса
            
            result.close();
            pstatment.close();
            
            return exists;
            
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка проверки существования пользователя", e);
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null) {
            	connection.close();
            }
        } catch (SQLException e) {
            // ignore
        }
    }
}