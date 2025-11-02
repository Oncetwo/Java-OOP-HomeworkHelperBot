package bot.schedule;

import bot.user.User;
import bot.user.UserStorage;

public class ScheduleManager {
    private final ScheduleStorage commonStorage;    // для schedules.db (общее расписание)
    private final ScheduleStorage customStorage;    // для custom_schedules.db (кастомное расписание)
    private final UserStorage userStorage;

    public ScheduleManager(UserStorage userStorage) {
        this.commonStorage = new SQLiteScheduleStorage("schedules.db");
        this.customStorage = new SQLiteScheduleStorage("custom_schedules.db");
        this.userStorage = userStorage;
        
        // Инициализируем обе БД при создании менеджера
        this.commonStorage.initialize();
        this.customStorage.initialize();
    }

    /**
     * Получить расписание для пользователя
     * Автоматически определяет - брать общее или кастомное расписание
     */
    public Schedule getScheduleForUser(long userId) {
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }

        if (user.getHasCustomSchedule()) {
            // У пользователя есть кастомное расписание - берем из custom_schedules.db
            return customStorage.getScheduleByGroupId(String.valueOf(userId));
        } else {
            // Берем общее расписание из schedules.db
            return commonStorage.getScheduleByGroupName(user.getGroup());
        }
    }

    /**
     * Сохранить кастомное расписание для пользователя
     */
    public void saveCustomSchedule(long userId, Schedule schedule) {
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }

        // Настраиваем schedule для кастомного хранения
        schedule.setGroupId(String.valueOf(userId));  // Используем userId как groupId в кастомной БД
        schedule.setGroupName(user.getGroup());       // Сохраняем оригинальное имя группы

        if (customStorage.scheduleExists(String.valueOf(userId))) {
            customStorage.updateSchedule(schedule);
        } else {
            customStorage.saveSchedule(schedule);
        }

        // Устанавливаем флаг кастомного расписания
        user.setHasCustomSchedule(true);
        userStorage.updateUser(user);
    }

    /**
     * Сбросить кастомное расписание и вернуться к общему
     */
    public void resetToOriginalSchedule(long userId) {
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }

        // Удаляем кастомное расписание
        customStorage.deleteSchedule(String.valueOf(userId));

        // Сбрасываем флаг
        user.setHasCustomSchedule(false);
        userStorage.updateUser(user);
    }

    /**
     * Сохранить общее расписание (для админа или при загрузке из УрФУ)
     */
    public void saveCommonSchedule(Schedule schedule) {
        if (commonStorage.scheduleExists(schedule.getGroupId())) {
            commonStorage.updateSchedule(schedule);
        } else {
            commonStorage.saveSchedule(schedule);
        }
    }

    /**
     * Получить общее расписание по имени группы (без учета кастомных настроек)
     */
    public Schedule getCommonScheduleByGroupName(String groupName) {
        return commonStorage.getScheduleByGroupName(groupName);
    }

    /**
     * Проверить, есть ли у пользователя кастомное расписание
     */
    public boolean hasCustomSchedule(long userId) {
        User user = userStorage.getUser(userId);
        return user != null && user.getHasCustomSchedule();
    }

    /**
     * Закрыть соединения с БД
     */
    public void close() {
        commonStorage.close();
        customStorage.close();
    }
}