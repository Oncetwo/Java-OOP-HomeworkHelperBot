package bot.commands;

import bot.schedule.Schedule;
import bot.schedule.SQLiteScheduleStorage;
import bot.user.User;
import bot.user.UserStorage;

import java.time.DayOfWeek;
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
        return "Показать расписание на неделю или на конкретный день (/schedule Monday)";
    }

    @Override
    public String realization(String[] args) {
        return "Чтобы получить расписание, используйте /schedule или /schedule Monday";
    }


    public String realizationWithChatId(long chatId, String[] args) {
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

            
            if (args.length > 1) {
                String dayArg = args[1].trim();

                DayOfWeek day = null;
                for (DayOfWeek d : DayOfWeek.values()) {
                    if (d.name().equalsIgnoreCase(dayArg)) { 
                        day = d;
                        break;
                    }
                }

                if (day == null) {
                    storage.close();
                    return "Некорректный день недели. Используйте один из: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday.";
                }

                String scheduleForDay = formatScheduleForDay(sched, day, user.getGroup());
                storage.close();
                return scheduleForDay;
            }

            String out = formatScheduleForUser(sched); // иначе — всё расписание
            storage.close();
            return out;

        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка при получении расписания: " + e.getMessage();
        }
    }


    private String formatScheduleForDay(Schedule sched, DayOfWeek day, String group) {
        StringBuilder sb = new StringBuilder();

        String key = day.toString(); // в Schedule ключи в виде "MONDAY"
        
        sb.append("Расписание для группы: ");

        String groupName = sched.getGroupName();
        if (groupName != null) {
            sb.append(groupName);
        }

        sb.append("\n\n");

        if (sched.getWeeklySchedule() == null || 
            sched.getWeeklySchedule().get(key) == null || 
            sched.getWeeklySchedule().get(key).isEmpty()) {
            sb.append("  — в этот день пар нет\n\n");

        } else {
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

        return sb.toString();
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

            if (sched.getWeeklySchedule() == null || 
            	sched.getWeeklySchedule().get(key) == null ||
            	sched.getWeeklySchedule().get(key).isEmpty()) {
                sb.append("  — в этот день пар нет\n\n");

            } else {
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
