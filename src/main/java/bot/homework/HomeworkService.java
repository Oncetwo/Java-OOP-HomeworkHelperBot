package bot.homework;

import java.sql.*;
import java.time.LocalDate;

public class HomeworkService { //добавляет дз в БД и возвращается ID

    private static final String DB_URL = "jdbc:sqlite:homework.db";
    private final SQLiteHomeworkStorage delegate;

    public HomeworkService(SQLiteHomeworkStorage delegate) {
        this.delegate = delegate;
    }

    public long addHomeworkAndReturnId(long chatId, String subject, String description, LocalDate dueDate, int remindBeforeDays) throws SQLException {
        delegate.addHomework(chatId, subject, description, dueDate, remindBeforeDays);

        String sql = "SELECT id FROM homework WHERE chatId = ? AND subject = ? AND dueDate = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, subject);
            ps.setString(3, dueDate != null ? dueDate.toString() : null);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                } else {
                    return -1L;
                }
            }
        }
    }
}
