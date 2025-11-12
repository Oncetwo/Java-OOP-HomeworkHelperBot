package bot.schedule;

import bot.user.exception.UserNotFoundException;
import bot.user.User;
import bot.user.UserStorage;

import java.util.Map;
import java.util.List;

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

        String customGroupId = String.valueOf(userId);

        // если кастомное расписание существует в customStorage — отдаём его.
        if (customStorage.scheduleExists(customGroupId)) {
            return customStorage.getScheduleByGroupId(customGroupId);
        }

        // иначе возвращаем общее по имени группы 
        return commonStorage.getScheduleByGroupName(user.getGroup());
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

        // Перечитываем пользователя и обновляем флаг (на случай, если объект user в памяти устарел)
        User fresh = userStorage.getUser(userId);
        if (fresh != null) {
            fresh.setHasCustomSchedule(true);
            userStorage.updateUser(fresh);
        } 
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


    public void copyCommonToCustom(long userId) { // скопировать из общего расписания в кастомное
        User user = userStorage.getUser(userId);

        if (user == null) {
            throw new UserNotFoundException(userId);
        }

        // Получаем оригинальное (общее) расписание по имени группы
        Schedule original = commonStorage.getScheduleByGroupName(user.getGroup());
        if (original == null) {
            // Если общего расписания нет — ничего не делаем 
            throw new IllegalStateException("Общее расписание для группы '" + user.getGroup() + "' не найдено");
        }

        // Создаём новый объект Schedule для кастомной БД (не мутируем original)
        Schedule copy = new Schedule(String.valueOf(userId), original.getGroupName());
        // Копируем пары 
        for (Map.Entry<String, List<Lesson>> entry : original.getWeeklySchedule().entrySet()) {
            String day = entry.getKey(); // текущий день недели
            for (Lesson l : entry.getValue()) { // перебираем все уроки
                // Создаём новый объект Lesson, чтобы не ссылаться на те же объекты
                Lesson lessonCopy = new Lesson(l.getSubject(), l.getStartTime(), l.getEndTime(), l.getClassroom());
                copy.addLesson(day, lessonCopy);
            }
        }

        String customGroupId = String.valueOf(userId);
        // Сохраняем или обновляем в customStorage 
        if (customStorage.scheduleExists(customGroupId)) {
            customStorage.updateSchedule(copy);
        } else {
            customStorage.saveSchedule(copy);
        }

        // Обновляем флаг у пользователя (перечитываем перед записью на всякий случай)
        User fresh = userStorage.getUser(userId);
        if (fresh != null) {
            fresh.setHasCustomSchedule(true);
            userStorage.updateUser(fresh);
        }
    }


    public void saveCommonSchedule(Schedule schedule) { // сохранить общее расписание
        if (commonStorage.scheduleExists(schedule.getGroupId())) {
            commonStorage.updateSchedule(schedule);
        } else {
            commonStorage.saveSchedule(schedule);
        }
    }
    
    public boolean customScheduleExists(long userId) { // проверка есть ли кастомное расписание у пользователя
        return customStorage.scheduleExists(String.valueOf(userId));
    }

    public void close() {
        commonStorage.close();
        customStorage.close();
    }
}