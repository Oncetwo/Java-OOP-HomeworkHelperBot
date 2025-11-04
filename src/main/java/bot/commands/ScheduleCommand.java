package bot.commands;

import bot.schedule.Schedule;
import bot.schedule.SQLiteScheduleStorage;
import bot.user.User;
import bot.user.UserStorage;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

public class ScheduleCommand implements Command {

    private final UserStorage userStorage;

    public ScheduleCommand(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    @Override
    public String getName() {
        return "/schedule";
    }

    @Override
    public String getInformation() {
        return "Показать расписание на неделю";
    }

    @Override
    public String realization(String[] args) {
        return "Чтобы получить расписание, используйте /schedule";
    }

    public String getScheduleForChat(long chatId) {
        try {
            User user = userStorage.getUser(chatId);
            if (user == null) {
                return "Вы не зарегистрированы. Введите /start, чтобы зарегистрироваться.";
            }

            SQLiteScheduleStorage storage = new SQLiteScheduleStorage("schedules.db");
            storage.initialize();
            
            Schedule sched = storage.getScheduleByGroupName(user.getGroup());
            if (sched == null) {
            	storage.close();
                return "Расписание для вашей группы не найдено. Оно либо ещё не было загружено, " +
                       "либо группа не сопоставлена. Попробуйте повторно зарегистрироваться или подождать загрузки.";
            }

            String out = formatScheduleForUser(sched);
            storage.close();
            return out;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка при получении расписания: " + e.getMessage();
        }
    }

    private String formatScheduleForUser(Schedule sched) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Расписание для группы: ");

        String groupName = sched.getGroupName();
        if (groupName != null) {
            sb.append(groupName);
        }

        sb.append("\n\n");
        
        List<DayOfWeek> days = List.of(
        	    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        	    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        	);

        for (DayOfWeek day : days) {
            String key = day.toString(); // в Schedule используются ключи в виде "MONDAY"
            sb.append(day.toString()).append(":\n"); // заголовок дня
            
            if (sched.getWeeklySchedule() == null || sched.getWeeklySchedule().get(key) == null || sched.getWeeklySchedule().get(key).isEmpty()) {
                sb.append("  — в этот день пар нет\n\n");
                
            } 
            else {
                sched.getWeeklySchedule().get(key).forEach(lesson -> {
                	
                	String start;
                	if (lesson.getStartTime() == null) {
                		start = "";
                	} else {
                		start = lesson.getStartTime().toString();
                	}

                	String end;
                	if (lesson.getEndTime() == null) {
                		end = "";
                	} else {
                		end = lesson.getEndTime().toString();
                	}

                	String subject;
                	if (lesson.getSubject() == null) {
                	    subject = "";
                	} else {
                	    subject = lesson.getSubject();
                	}
                    sb.append("  ")
                      .append(start)
                      .append(" - ")
                      .append(end)
                      .append(" | ")
                      .append(subject);

                    if (lesson.getClassroom() != null && !lesson.getClassroom().isEmpty()) {
                        sb.append(" (").append(lesson.getClassroom()).append(")");
                    }

                    sb.append("\n");
                });
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
