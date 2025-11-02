package bot.schedule;

import bot.user.User;
import bot.user.UserStorage;

public class ScheduleManager {
    private final ScheduleStorage commonStorage;    // для schedules.db 
    private final ScheduleStorage customStorage;    // для custom_schedules.db 
    private final UserStorage userStorage;

    public ScheduleManager(UserStorage userStorage) { // конструтор (делаем две бд и инициализирцем их)
        this.commonStorage = new SQLiteScheduleStorage("schedules.db");
        this.customStorage = new SQLiteScheduleStorage("custom_schedules.db");
        this.userStorage = userStorage;
        
        this.commonStorage.initialize();
        this.customStorage.initialize();
    }

    
    public Schedule getScheduleForUser(long userId) { // получить расписание для пользователя
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }

        if (user.getHasCustomSchedule()) {  // У пользователя есть кастомное расписание - берем из custom_schedules.db
            return customStorage.getScheduleByGroupId(String.valueOf(userId));
        } else {
            return commonStorage.getScheduleByGroupName(user.getGroup());
        }
    }


    public void saveCustomSchedule(long userId, Schedule schedule) { // сохранить кастомное расписание для пользователя
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }

        // Настраиваем schedule для кастомного хранения
        schedule.setGroupId(String.valueOf(userId)); // Используем userId как groupId в кастомной БД
        schedule.setGroupName(user.getGroup()); // Сохраняем оригинальное имя группы

        if (customStorage.scheduleExists(String.valueOf(userId))) {
            customStorage.updateSchedule(schedule);
        } else {
            customStorage.saveSchedule(schedule);
        }

        user.setHasCustomSchedule(true); // Устанавливаем флаг кастомного расписания
        userStorage.updateUser(user);
    }

    
    public void resetToOriginalSchedule(long userId) { // сбросить кастомное расписание и вернуться к общему (для пользователя надо будет)
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден: " + userId);
        }

        customStorage.deleteSchedule(String.valueOf(userId)); // удаляем кастомное расписание

        user.setHasCustomSchedule(false); // Сбрасываем флаг
        userStorage.updateUser(user);
    }


    public void saveCommonSchedule(Schedule schedule) { // сохранить общее расписание
        if (commonStorage.scheduleExists(schedule.getGroupId())) {
            commonStorage.updateSchedule(schedule);
        } else {
            commonStorage.saveSchedule(schedule);
        }
    }


    public void close() {
        commonStorage.close();
        customStorage.close();
    }
}