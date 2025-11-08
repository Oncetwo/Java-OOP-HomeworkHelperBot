package bot.schedule;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;

import bot.user.exception.ScheduleStorageException;

public class SQLiteScheduleStorage implements ScheduleStorage {
    private final String dbUrl;
    private Connection connection;

    public SQLiteScheduleStorage(String dbFileName) { // Конструктор с путем к БД (потому что нам нужно 2 бд)
        this.dbUrl = "jdbc:sqlite:" + dbFileName;
    }

    
    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection(dbUrl); // устанавливаем соединения с бд
            
            // Таблица mapping (группа -> groupId)
            String mappingSql = "CREATE TABLE IF NOT EXISTS group_mapping (" +
                               "groupName TEXT PRIMARY KEY," +
                               "groupId TEXT NOT NULL," +
                               "lastUpdated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                               ")";
            
            // Таблица групп
            String groupsSql = "CREATE TABLE IF NOT EXISTS groups (" +
                              "groupId TEXT PRIMARY KEY," +
                              "groupName TEXT UNIQUE NOT NULL" +
                              ")";
            
            // Таблица расписания
            String scheduleSql = "CREATE TABLE IF NOT EXISTS schedule_lessons (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "groupId TEXT," +
                                "dayOfWeek TEXT NOT NULL," +
                                "subject TEXT NOT NULL," +
                                "startTime TEXT NOT NULL," +
                                "endTime TEXT NOT NULL," +
                                "classroom TEXT," +
                                "FOREIGN KEY (groupId) REFERENCES groups(groupId)" +
                                ")";
            
            Statement statement = connection.createStatement(); // создаем отправителя запросов 
            statement.execute(mappingSql); // выполняем запрос (создаем таблицу)
            statement.execute(groupsSql);
            statement.execute(scheduleSql);
            statement.close();
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка инициализации БД расписания: ", e);
        }
    }

    
    @Override
    public String getGroupIdByName(String groupName) {
        try {
            String sql = "SELECT groupId FROM group_mapping WHERE groupName = ?";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, groupName);
            
            ResultSet result =  pstatment.executeQuery(); // оно вернет курсор на начало строки
            if (result.next()) { // если нашли результат 
                String groupId = result.getString("groupId");
                result.close();
                pstatment.close();
                
                updateMappingTimestamp(groupName);
                
                return groupId;
            }
            result.close();
            pstatment.close();
            return null;
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения groupId по имени группы: ", e);
        }
    }
    
    
    @Override
    public void saveGroupMapping(String groupName, String groupId) {
        try {
            String sql = "INSERT OR REPLACE INTO group_mapping (groupName, groupId) VALUES (?, ?)";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, groupName);
            pstatment.setString(2, groupId);
            pstatment.executeUpdate();
            pstatment.close();
            
            updateMappingTimestamp(groupName);
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка сохранения mapping группы: ", e);
        }
    }

    
    @Override
    public boolean groupMappingExists(String groupName) {
        try {
            String sql = "SELECT 1 FROM group_mapping WHERE groupName = ?"; // возвращает 1, если строка с заданным именем группы найдена в бд
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, groupName);
            
            ResultSet result = pstatment.executeQuery();
            boolean exists = result.next();
            
            result.close();
            pstatment.close();
            return exists;
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка проверки mapping группы: ", e);
        }
    }
    
    
    @Override
    public void updateMappingTimestamp(String groupName) {
        try {
            String sql = "UPDATE group_mapping SET lastUpdated = CURRENT_TIMESTAMP WHERE groupName = ?";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, groupName);
            pstatment.executeUpdate();
            pstatment.close();
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка обновления времени mapping", e);
        }
    }
    
    
    @Override
    public void technicalMaintenance(int daysOld) {
        try {
            String sql = "DELETE FROM group_mapping WHERE lastUpdated < datetime('now', ?)";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, "-" + daysOld + " days");
            
            int deletedCount = pstatment.executeUpdate();
            pstatment.close();
            
            System.out.println("Удалено устаревших mapping: " + deletedCount);
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка очистки старых mapping", e);
        }
    }


    @Override
    public Schedule getScheduleByGroupId(String groupId) {
        try {
            String groupSql = "SELECT groupName FROM groups WHERE groupId = ?"; // вернет имя группы, если найдена строка с заданным айди
            PreparedStatement pstatment = connection.prepareStatement(groupSql);
            pstatment.setString(1, groupId);
            
            ResultSet result = pstatment.executeQuery();
            if (!result.next()) { // если строка не найдена
            	result.close();
            	pstatment.close();
                return null;
            }
            
            String groupName = result.getString("groupName");
            result.close();
            pstatment.close();

            String lessonsSql = "SELECT dayOfWeek, subject, startTime, endTime, classroom " +
                               "FROM schedule_lessons WHERE groupId = ? ORDER BY dayOfWeek, startTime"; // ORDER BY dayOfWeek, startTime сортирует по дню недели и времени начала
            
            PreparedStatement lessonsStmt = connection.prepareStatement(lessonsSql);
            lessonsStmt.setString(1, groupId);
            
            ResultSet lessonsResult = lessonsStmt.executeQuery();
            
            Schedule schedule = new Schedule(groupId, groupName);
            
            while (lessonsResult.next()) {
                String dayOfWeek = lessonsResult.getString("dayOfWeek");
                String subject = lessonsResult.getString("subject");
                LocalTime startTime = LocalTime.parse(lessonsResult.getString("startTime")); // извлекаем как строку и парсим в LocalTime объект
                LocalTime endTime = LocalTime.parse(lessonsResult.getString("endTime"));
                String classroom = lessonsResult.getString("classroom");
                
                Lesson lesson = new Lesson(subject, startTime, endTime, classroom);
                schedule.addLesson(dayOfWeek, lesson); // добавляет пару в расписание под нужным днем
            }
            
            lessonsResult.close();
            lessonsStmt.close();
            return schedule;
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка получения расписания по ID группы: ", e);
        }
    }

    
    @Override
    public Schedule getScheduleByGroupName(String groupName) {
        try {
            String groupId = getGroupIdByName(groupName); // Находим groupId через mapping
            if (groupId == null) {
                return null;  // если группа не найдена
            }
            
            return getScheduleByGroupId(groupId);
            
        } catch (Exception e) {
            throw new ScheduleStorageException("Ошибка получения расписания по имени группы: ", e);
        }
    }

    @Override
    public void saveSchedule(Schedule schedule) {
        if (scheduleExists(schedule.getGroupId())) {
            throw new ScheduleStorageException("Расписание для этой группы уже существует");
        }
        
        try {
            if (!groupMappingExists(schedule.getGroupName())) { // Сохраняем mapping (если его еще нет)
                saveGroupMapping(schedule.getGroupName(), schedule.getGroupId());
            } else {
                updateMappingTimestamp(schedule.getGroupName()); // Обновляем время если mapping уже существует
            }

            // Сохраняем в групс
            String groupSql = "INSERT INTO groups (groupId, groupName) VALUES (?, ?)";
            PreparedStatement groupStmt = connection.prepareStatement(groupSql);
            groupStmt.setString(1, schedule.getGroupId());
            groupStmt.setString(2, schedule.getGroupName());
            groupStmt.executeUpdate();
            groupStmt.close();

            // Сохраняем пары
            String lessonSql = "INSERT INTO schedule_lessons (groupId, dayOfWeek, subject, startTime, endTime, classroom) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement lessonStmt = connection.prepareStatement(lessonSql);
            
            for (Map.Entry<String, List<Lesson>> entry : schedule.getWeeklySchedule().entrySet()) {
                String day = entry.getKey();
                for (Lesson lesson : entry.getValue()) {
                    lessonStmt.setString(1, schedule.getGroupId());
                    lessonStmt.setString(2, day);
                    lessonStmt.setString(3, lesson.getSubject());
                    lessonStmt.setString(4, lesson.getStartTime().toString());
                    lessonStmt.setString(5, lesson.getEndTime().toString());
                    lessonStmt.setString(6, lesson.getClassroom());
                    lessonStmt.addBatch(); // добавляем в пачку, но пока не выполняем
                }
            }
            lessonStmt.executeBatch(); // выполняем все запросы за раз
            lessonStmt.close();
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка сохранения расписания для группы", e);
        }
    }

    @Override
    public void updateSchedule(Schedule schedule) {
        if (!scheduleExists(schedule.getGroupId())) {
            throw new ScheduleStorageException("Расписание для этой группы не существует");
        }
        
        deleteSchedule(schedule.getGroupId());
        
        saveSchedule(schedule);
    }
    
    
    @Override
    public void deleteSchedule(String groupId) {
        try {
            // Удаляем пары
            String lessonsSql = "DELETE FROM schedule_lessons WHERE groupId = ?";
            PreparedStatement lessonsStmt = connection.prepareStatement(lessonsSql);
            lessonsStmt.setString(1, groupId);
            lessonsStmt.executeUpdate();
            lessonsStmt.close();

            // Удаляем группу
            String groupSql = "DELETE FROM groups WHERE groupId = ?";
            PreparedStatement groupStmt = connection.prepareStatement(groupSql);
            groupStmt.setString(1, groupId);
            groupStmt.executeUpdate();
            groupStmt.close();
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка удаления расписания для группы: ", e);
        }
    }
    

    @Override
    public boolean scheduleExists(String groupId) {
        try {
            String sql = "SELECT 1 FROM groups WHERE groupId = ?";
            PreparedStatement pstatment = connection.prepareStatement(sql);
            pstatment.setString(1, groupId);
            
            ResultSet result = pstatment.executeQuery();
            boolean exists = result.next();
            
            result.close();
            pstatment.close();
            
            return exists;
            
        } catch (SQLException e) {
            throw new ScheduleStorageException("Ошибка проверки существования расписания для группы: ", e);
        }
    }
    
    
    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
        }
    }
}