package bot.homework;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import bot.user.exception.ScheduleStorageException; 

public class SQLiteHomeworkStorage implements HomeworkStorage {

	private final String dbUrl = "jdbc:sqlite:homework.db"; // Путь к файлу базы данных
    private Connection connection;
    
    
    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection(dbUrl);

            // SQL для создания таблицы домашних заданий
            String sql = "CREATE TABLE IF NOT EXISTS homework (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, " + // уникальный идентификатор записи
                         "chatId INTEGER NOT NULL, " +  // id пользователя (chatId)
                         "subject TEXT NOT NULL, " + // название предмета
                         "description TEXT NOT NULL, " + // текст домашнего задания
                         "dueDate TEXT NOT NULL, " +  // дата дедлайна в формате YYYY-MM-DD
                         "completed INTEGER DEFAULT 0, " +  // статус выполнения 
                         "remindBeforeDays INTEGER DEFAULT 1" + // за сколько дней напоминать (по умолчанию 1)
                         ")";
            
            Statement statement = connection.createStatement();
            statement.execute(sql);
            statement.close();

        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка инициализации БД домашних заданий", e);
        }
    }


    @Override
    public void addHomework(long chatId, String subject, String description, LocalDate dueDate) {
        try {
            String sql = "INSERT INTO homework (chatId, subject, description, dueDate, completed, remindBeforeDays) " +
                         "VALUES (?, ?, ?, ?, 0, 1)";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setLong(1, chatId);
            pstatment.setString(2, subject);
            pstatment.setString(3, description);
            pstatment.setString(4, dueDate.toString());
            pstatment.executeUpdate();
            pstatment.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка добавления домашнего задания", e);
        }
    }


    @Override
    public List<HomeworkItem> getHomeworkByUser(long chatId) {
        List<HomeworkItem> homeworkList = new ArrayList<>();
        try {
            String sql = "SELECT * FROM homework WHERE chatId = ? ORDER BY dueDate";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setLong(1, chatId);
            ResultSet result = pstatment.executeQuery();

            // проходим по всем найденным строкам
            while (result.next()) {
                homeworkList.add(mapToHomeworkItem(result)); // преобразуем строку БД в объект HomeworkItem
            }

            result.close();
            pstatment.close();
            return homeworkList;
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения домашних заданий пользователя", e);
        }
    }


    @Override
    public List<HomeworkItem> getHomeworkBySubject(long chatId, String subject) { // получение домашних заданий по конкретному предмету.
        List<HomeworkItem> homeworkList = new ArrayList<>();
        try {
            String sql = "SELECT * FROM homework WHERE chatId = ? AND subject = ? ORDER BY dueDate";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setLong(1, chatId);
            pstatment.setString(2, subject);
            ResultSet result = pstatment.executeQuery();

            while (result.next()) {
                homeworkList.add(mapToHomeworkItem(result));
            }

            result.close();
            pstatment.close();
            return homeworkList;
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения домашних заданий по предмету", e);
        }
    }

    
    @Override
    public List<HomeworkItem> getActiveHomeworkBySubjects(long chatId, List<String> subjects) {
        List<HomeworkItem> homeworkList = new ArrayList<>();
        if (subjects == null || subjects.isEmpty()) {
        	return homeworkList;
        }

        try {
            String placeholders = String.join(",", subjects.stream().map(s -> "?").toList());
            String sql = "SELECT * FROM homework WHERE chatId = ? AND subject IN (" + placeholders + ") " +
                         "AND completed = 0 AND dueDate >= ?";

            PreparedStatement pstatement = connection.prepareStatement(sql);
            pstatement.setLong(1, chatId);

            int index = 2;
            for (String subj : subjects) {
            	pstatement.setString(index++, subj);
            }

            pstatement.setString(index, LocalDate.now().toString());

            ResultSet result = pstatement.executeQuery();
            while (result.next()) {
                homeworkList.add(mapToHomeworkItem(result));
            }

            result.close();
            pstatement.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения активных домашних заданий по предметам", e);
        }

        return homeworkList;
    }

    //хзхз
    @Override
    public List<HomeworkItem> getHomeworkForDate(LocalDate date) { // Получение всех заданий, которые нужно выполнить в конкретную дату
        List<HomeworkItem> homeworkList = new ArrayList<>();
        try {
            String sql = "SELECT * FROM homework WHERE dueDate = ? ORDER BY chatId";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, date.toString());
            ResultSet result = pstatment.executeQuery();

            while (result.next()) { // проходимся по всем записям, переводим их в объект дз и добавляем в список
                homeworkList.add(mapToHomeworkItem(result));
            }

            result.close();
            pstatment.close();
            return homeworkList;
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения домашних заданий по дате", e);
        }
    }

    /**
     * Получение списка заданий, для которых нужно отправить напоминание.
     * Сравниваем текущую дату с (dueDate - remindBeforeDays).
     */
    @Override
    public List<HomeworkItem> getHomeworkForReminder(LocalDate date) { // собирает список задание, о которых нужно наполнить 
        List<HomeworkItem> homeworkList = new ArrayList<>();
        try {
            String sql = "SELECT * FROM homework";
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                HomeworkItem item = mapToHomeworkItem(rs);
                LocalDate remindDate = item.getDueDate().minusDays(item.getRemindBeforeDays());
                
                // добавляем только те, где дата напоминания совпадает с сегодняшней и задание не выполнено
                if (remindDate.equals(date) && !item.isCompleted()) {
                    homeworkList.add(item);
                }
            }

            rs.close();
            st.close();
            return homeworkList;
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения домашних заданий для напоминания", e);
        }
    }


    @Override
    public void updateHomework(long id, String newSubject, String newDescription, LocalDate newDueDate) { // обновление записи дз
        try {
            String sql = "UPDATE homework SET subject = ?, description = ?, dueDate = ? WHERE id = ?";
            PreparedStatement pstatement = connection.prepareStatement(sql);
            pstatement.setString(1, newSubject);
            pstatement.setString(2, newDescription);
            pstatement.setString(3, newDueDate.toString());
            pstatement.setLong(4, id);
            pstatement.executeUpdate();
            pstatement.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка обновления домашнего задания", e);
        }
    }


    
    @Override
    public void markAsCompleted(long id, boolean completed) { // пометить задание, как выполненное (и наоборот тоже можно)
        try {
            String sql = "UPDATE homework SET completed = ? WHERE id = ?";
            PreparedStatement pstatement = connection.prepareStatement(sql);
            pstatement.setInt(1, completed ? 1 : 0); // если передали тру, записали 1, если фолс, записали 0
            pstatement.setLong(2, id);
            pstatement.executeUpdate();
            pstatement.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка отметки статуса выполнения ДЗ", e);
        }
    }



    @Override
    public void deleteHomework(long id) { // удалить конкретную запись дз 
        try {
            String sql = "DELETE FROM homework WHERE id = ?";
            PreparedStatement pstatement = connection.prepareStatement(sql);
            pstatement.setLong(1, id);
            pstatement.executeUpdate();
            pstatement.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка удаления домашнего задания", e);
        }
    }


    @Override
    public void deleteOldHomework(LocalDate date) { // удалить дз, у которых дедлайн прошел
        try {
            String sql = "DELETE FROM homework WHERE dueDate < ?";
            PreparedStatement pstatement = connection.prepareStatement(sql);
            pstatement.setString(1, date.toString());
            pstatement.executeUpdate();
            pstatement.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка удаления старых домашних заданий", e);
        }
    }


    @Override
    public void deleteAllHomeworkForUser(long chatId) { // удалить все дз пользователя
        try {
            String sql = "DELETE FROM homework WHERE chatId = ?";
            PreparedStatement pstatement = connection.prepareStatement(sql);
            pstatement.setLong(1, chatId);
            pstatement.executeUpdate();
            pstatement.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка удаления всех домашних заданий пользователя", e);
        }
    }


    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {}
    }


    private HomeworkItem mapToHomeworkItem(ResultSet rs) throws SQLException { // Преобразование строки из ResultSet в объект HomeworkItem
        return new HomeworkItem(
            rs.getLong("id"), // уникальный идентификатор задания
            rs.getLong("chatId"), // id пользователя
            rs.getString("subject"), // предмет
            rs.getString("description"), // текст задания
            LocalDate.parse(rs.getString("dueDate")), // дедлайн (преобразуем из строки в LocalDate)
            rs.getInt("completed") == 1, // статус (1 — true, 0 — false)
            rs.getInt("remindBeforeDays") // за сколько дней напоминать
        );
    }
}
