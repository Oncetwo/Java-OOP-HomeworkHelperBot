package bot.schedule;
import bot.user.exception.UserNotFoundException;
import java.util.Map;
import java.util.List;

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
        
        this.commonStorage.technicalMaintenance(200);
    }

    
    public Schedule getScheduleForUser(long userId) { // получить расписание для пользователя
        User user = userStorage.getUser(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
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
            throw new UserNotFoundException(userId);
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
            throw new UserNotFoundException(userId);
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
    
    
    public void copyCommonToCustom(long userId) { // скопировать из общего расписания в кастомное
        User user = userStorage.getUser(userId);
        
        if (user == null) {
            throw new UserNotFoundException(userId);
        }

        // Получаем оригинальное (общее) расписание по имени группы
        Schedule original = commonStorage.getScheduleByGroupName(user.getGroup());
        if (original == null) {
            // Если общего расписания нет — ничего не делаем или бросаем понятное исключение
            throw new IllegalStateException("Общее расписание для группы '" + user.getGroup() + "' не найдено");
        }

        // Создаём новый объект Schedule для кастомной БД 
        Schedule copy = new Schedule(String.valueOf(userId), original.getGroupName());
        // Копируем пары 
        for (Map.Entry<String, List<Lesson>> entry : original.getWeeklySchedule().entrySet()) {
            String day = entry.getKey();
            for (Lesson les : entry.getValue()) {
                // Создаём новый объект Lesson, чтобы не ссылаться на те же объекты
                Lesson lessonCopy = new Lesson(les.getSubject(), les.getStartTime(), les.getEndTime(), les.getClassroom());
                copy.addLesson(day, lessonCopy);
            }
        }

        String customGroupId = String.valueOf(userId);
        // Сохраняем или обновляем в customStorage — чтобы не вызывать исключение при повторном вызове
        if (customStorage.scheduleExists(customGroupId)) {
            customStorage.updateSchedule(copy);
        } else {
            customStorage.saveSchedule(copy);
        }

        // Обновляем флаг у пользователя
        user.setHasCustomSchedule(true);
        userStorage.updateUser(user);
    }



    public void close() {
        commonStorage.close();
        customStorage.close();
    }
}