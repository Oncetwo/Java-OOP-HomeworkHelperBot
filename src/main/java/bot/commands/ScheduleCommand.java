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

            SQLiteScheduleStorage ss = new SQLiteScheduleStorage("schedules.db");
            ss.initialize();
            Schedule sched = ss.getScheduleByGroupName(user.getGroup());
            if (sched == null) {
                ss.close();
                return "Расписание для вашей группы не найдено. Оно либо ещё не было загружено, " +
                       "либо группа не сопоставлена. Попробуйте повторно зарегистрироваться или подождать загрузки.";
            }

            String out = formatScheduleForUser(sched);
            ss.close();
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка при получении расписания: " + e.getMessage();
        }
    }

    private String formatScheduleForUser(Schedule sched) {
        StringBuilder sb = new StringBuilder();
        sb.append("Расписание для группы: ").append(sched.getGroupName() == null ? "" : sched.getGroupName()).append("\n\n");
        List<DayOfWeek> days = Arrays.asList(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        );

        for (DayOfWeek d : days) {
            String key = d.toString(); // в Schedule используются ключи в виде "MONDAY"
            sb.append(d.toString()).append(":\n");
            if (sched.getWeeklySchedule() == null || sched.getWeeklySchedule().get(key) == null ||
                    sched.getWeeklySchedule().get(key).isEmpty()) {
                sb.append("  — пар нет\n\n");
            } else {
                sched.getWeeklySchedule().get(key).forEach(lesson -> {
                    String st = lesson.getStartTime() == null ? "" : lesson.getStartTime().toString();
                    String et = lesson.getEndTime() == null ? "" : lesson.getEndTime().toString();
                    sb.append("  ").append(st).append(" - ").append(et).append(" | ").append(lesson.getSubject() == null ? "" : lesson.getSubject());
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
