package bot.homework;

import java.sql.*;
import java.time.LocalDate;

public class HomeworkLinkStorage { // реализует связь

    private static final String DB_URL = "jdbc:sqlite:homework.db";

    public HomeworkLinkStorage() {
        init();
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS homework_link (" +
                "homework_id INTEGER PRIMARY KEY, " +
                "schedule_day TEXT, " +
                "lesson_index INTEGER)";
        try (Connection c = DriverManager.getConnection(DB_URL); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void linkLatestHomeworkByUserSubjectDate(long userId, String subject, LocalDate dueDate,
            String scheduleDay, Integer lessonIndex) throws SQLException {
    	String find = "SELECT id FROM homework WHERE chatId = ? AND subject = ? AND dueDate = ? ORDER BY id DESC LIMIT 1"; //поиск дз
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(find)) { //делаем соединение и создаём запрос
            ps.setLong(1, userId);
            ps.setString(2, subject);
            ps.setString(3, dueDate != null ? dueDate.toString() : null);
            try (ResultSet result = ps.executeQuery()) { //осуществляем связь между дз и расписанием, здесь получаем её ID
                if (result.next()) { //если дз было создано
                    long hwId = result.getLong("id");
                    String upsert = "INSERT OR REPLACE INTO homework_link(homework_id, schedule_day, lesson_index) VALUES (?, ?, ?)"; //вставка или замена таблицы
                    try (PreparedStatement ps2 = connection.prepareStatement(upsert)) { //обновляем таблицу
                        ps2.setLong(1, hwId);
                        ps2.setString(2, scheduleDay);
                        if (lessonIndex == null) ps2.setNull(3, Types.INTEGER);
                        else ps2.setInt(3, lessonIndex);
                        ps2.executeUpdate();
                    }
                }
            }
        }
    }
    public void unlinkHomework(long homeworkId) {
        String sql = "DELETE FROM homework_link WHERE homework_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, homeworkId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при удалении связи homework_link", e);
        }
    }

}
