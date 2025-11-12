package bot.schedule;
import bot.user.exception.UserNotFoundException;

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

        Schedule original = commonStorage.getScheduleByGroupName(user.getGroup()); // берем расписание из основной бд
        original.setGroupId(String.valueOf(userId)); // вместо айди группы пишем айди пользователя (так хранится в бд кастомных расписаний)
        original.setGroupName(user.getGroup()); // группу оставляем той же
        customStorage.saveSchedule(original); // сохраняем новое расписание в бд кастомных расписаний

        user.setHasCustomSchedule(true); // меняем флаг у пользователя
        userStorage.updateUser(user); // обновляем пользователя в бд
    }



    public void close() {
        commonStorage.close();
        customStorage.close();
    }
}