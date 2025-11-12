package bot.commands;

import bot.schedule.Schedule;
import bot.schedule.ScheduleManager; 
import bot.user.User;
import bot.user.UserStorage;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

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

            ScheduleManager manager = new ScheduleManager(userStorage); // расписание берем через менеджре, на случай, если есть кастомное
            Schedule sched = manager.getScheduleForUser(chatId);
            manager.close();

            if (sched == null) {
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
                    return "Некорректный день недели. Используйте один из: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday.";
                }

                String scheduleForDay = formatScheduleForDay(sched, day, user.getGroup());
                return scheduleForDay;
            }

            String out = formatScheduleForUser(sched); // иначе — всё расписание
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

        List<bot.schedule.Lesson> lessons = getLessonsIgnoreCase(sched, key); // получение списка занятий нечувствительно к регистру ключа дня

        if (lessons == null || lessons.isEmpty()) {
            sb.append("  — в этот день пар нет\n\n");

        } else {
            lessons.forEach(lesson -> {

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

            List<bot.schedule.Lesson> lessons = getLessonsIgnoreCase(sched, key); // получение списка занятий нечувствительно к регистру ключа дня

            if (lessons == null || lessons.isEmpty()) {
                sb.append("  — в этот день пар нет\n\n");

            } else {
                lessons.forEach(lesson -> {

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


    private List<bot.schedule.Lesson> getLessonsIgnoreCase(Schedule sched, String dayKey) { // озвращает список занятий для ключа dayKey, игнорируя регистр ключей
        if (sched == null || sched.getWeeklySchedule() == null) return List.of();

        Map<String, List<bot.schedule.Lesson>> map = sched.getWeeklySchedule();

        // Пробуем прямой поиск
        if (map.containsKey(dayKey) && map.get(dayKey) != null) {
            return map.get(dayKey);
        }

        // Ищем нечувствительно к регистру
        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(dayKey)) {
                return map.get(k);
            }
        }

        return List.of();
    }
}