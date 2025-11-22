package bot.commands;

import bot.homework.SQLiteHomeworkStorage;
import bot.homework.HomeworkLinkStorage;
import bot.schedule.Schedule;
import bot.schedule.ScheduleManager;
import bot.schedule.Lesson;
import bot.user.User;
import bot.user.UserStorage;
import bot.fsm.DialogState;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AddHomeworkCommand implements Command {

    private final SQLiteHomeworkStorage storage;
    private final UserStorage userStorage;
    private final ScheduleManager scheduleManager;
    private final HomeworkLinkStorage linkStorage;

    private final Map<Long, Draft> pending = new ConcurrentHashMap<>();

    public AddHomeworkCommand(UserStorage userStorage) {
        this.userStorage = userStorage;
        this.storage = new SQLiteHomeworkStorage();
        this.storage.initialize();
        this.scheduleManager = new ScheduleManager(userStorage);
        this.linkStorage = new HomeworkLinkStorage();
    }

    @Override
    public String getName() {
        return "/addhw";
    }

    @Override
    public String getInformation() {
        return "Добавить домашнее задание: /addhw";
    }

    @Override
    public String realization(String[] args) {
        return "Использование: /addhw *описание домашнего задания*.";
    }

    // запуск интерактивного потока
    public String start(long chatId) {
        User user = userStorage.getUser(chatId);
        if (user == null) return "❌ Профиль не найден. Введите /start.";

        pending.remove(chatId);
        pending.put(chatId, new Draft());

        user.setState(DialogState.ASK_HW_SUBJECT);
        userStorage.updateUser(user);

        Schedule schedule = scheduleManager.getScheduleForUser(chatId);
        if (schedule == null) {
            return "Шаг 1/4 — введите предмет (например: Математика).";
        }
        // показать краткий список предметов
        Set<String> subs = new LinkedHashSet<>();
        for (List<Lesson> dayLessons : schedule.getWeeklySchedule().values()) {
            if (dayLessons != null) {
                for (Lesson lesson : dayLessons) {
                    if (lesson != null && lesson.getSubject() != null) {
                        String subject = lesson.getSubject().trim();
                        subs.add(subject);
                    }
                }
            }
        }
        if (subs.isEmpty()) {
            return "Шаг 1/4 — введите предмет (в расписании предметы не найдены).";
        }
        StringBuilder sb = new StringBuilder("Шаг 1/4 — введите предмет:\n");
        int counter = 1;
        for (String subject : subs) {
            sb.append(counter)
                    .append(". ")
                    .append(subject)
                    .append("\n");
            counter++;
        }

        sb.append("\nВведите название предмета.");
        return sb.toString();
    }

    public String handleStateMessage(long chatId, String messageText) {
        if (messageText == null) messageText = "";
        String txt = messageText.trim();

        User user = userStorage.getUser(chatId);
        if (user == null) return "❌ Профиль не найден. Введите /start.";
        Draft draft = pending.get(chatId);
        if (draft == null) {
            draft = new Draft();
            pending.put(chatId, draft);
        }
        //Предмет
        if (user.getState() == DialogState.ASK_HW_SUBJECT) {

            if (txt.isEmpty()) {
                return "Введите предмет.";
            }

            Schedule schedule = scheduleManager.getScheduleForUser(chatId);
            if (schedule != null) {
                final String subjectInput = txt;
                boolean found = schedule.getWeeklySchedule().values().stream() // есть ли такой предмет у пользователя
                        .filter(Objects::nonNull) // убираем дни с null
                        .flatMap(Collection::stream) // преобразуем в Stream<Lesson>
                        .filter(Objects::nonNull)
                        .anyMatch(l -> l.getSubject() != null &&
                                            l.getSubject().equalsIgnoreCase(subjectInput)); // ищем совпадения

                if (!found) {
                    return "❌ Предмет не найден в расписании. Введите корректный предмет.";
                }
                else {
                    draft.subject = txt;
                    user.setState(DialogState.ASK_HW_TIME);
                    userStorage.updateUser(user);
                    return "Шаг 2/4 — введите дату YYYY-MM-DD или MONDAY.";
                }
            }

        }
        // Время
        if (user.getState() == DialogState.ASK_HW_TIME) {
            if (txt.isEmpty()) return "Введите дату YYYY-MM-DD или день недели (MONDAY..SUNDAY).";

            LocalDate date;
            try {
                date = LocalDate.parse(txt);
            }
            catch (DateTimeParseException e) { // если был введён день недели типа мондэйй
                // день недели
                try {
                    DayOfWeek dow = DayOfWeek.valueOf(txt.toUpperCase());
                    LocalDate today = LocalDate.now();
                    int delta = (dow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
                    if (delta == 0) delta = 7;
                    date = today.plusDays(delta);
                } catch (Exception ex) {
                    return "Неверный формат. Введите YYYY-MM-DD или MONDAY..SUNDAY.";
                }
            }

            draft.dueDate = date;

            // проверка: если расписание есть — убедиться, что предмет есть на этот день
            Schedule s = scheduleManager.getScheduleForUser(chatId);
            if (s != null && draft.subject != null) {
                String dayKey = date.getDayOfWeek().name();
                List<Lesson> lessons = s.getWeeklySchedule().get(dayKey);
                boolean ok = false;
                if (lessons != null) {
                    for (Lesson l : lessons) {
                        if (l != null && l.getSubject() != null && l.getSubject().equalsIgnoreCase(draft.subject.trim())) {
                            ok = true; break;
                        }
                    }
                }
                if (!ok) {
                    return "❌ На указанную дату предмет не найден в расписании. Введите другую дату.";
                }
            }

            user.setState(DialogState.ASK_HW_DESCRIPTION);
            userStorage.updateUser(user);
            return "Шаг 3/4 — введите текст задания (или /skip для пропуска описания).";
        }
        // Ожидаем описание
        if (user.getState() == DialogState.ASK_HW_DESCRIPTION) {
            if (txt.equalsIgnoreCase("/skip")) txt = "";
            draft.description = txt;

            // Переводим на шаг вопроса о напоминании
            user.setState(DialogState.ASK_HW_REMIND);
            userStorage.updateUser(user);
            return "Шаг 4/4 — За сколько дней до дедлайна напомнить? Введите целое число (например: 1). Введите /skip для значения по умолчанию (1).";
        }

        // Ожидаем remind-days
        if (user.getState() == DialogState.ASK_HW_REMIND) {
            final int DEFAULT = 1;
            int remindDays = DEFAULT;
            if (txt.equalsIgnoreCase("/skip") || txt.isEmpty()) {
                remindDays = DEFAULT;
            } else {
                try {
                    remindDays = Integer.parseInt(txt);
                    if (remindDays < 0 || remindDays > 365) {
                        return "❌ Укажите целое число от 0 до 365 (0 — в день дедлайна), либо введите /skip.";
                    }
                } catch (NumberFormatException e) {
                    return "❌ Неверный формат. Введите целое число (например 1) или /skip.";
                }
            }

            draft.remindBeforeDays = remindDays;

            // Сохраняем задание в БД с remindBeforeDays
            try {
                storage.addHomework(
                        chatId,
                        draft.subject == null ? "-" : draft.subject,
                        draft.description == null ? "" : draft.description,
                        draft.dueDate == null ? LocalDate.now() : draft.dueDate,
                        draft.remindBeforeDays
                );
            } catch (Exception e) {
                e.printStackTrace();
                return "❌ Ошибка при сохранении. Попробуйте позже.";
            }

            // Попытка привязки к паре/уроку
            try {
                Schedule sched = scheduleManager.getScheduleForUser(chatId);
                if (sched != null && draft.subject != null && draft.dueDate != null) {
                    String dayKey = draft.dueDate.getDayOfWeek().name();
                    List<Lesson> lessons = sched.getWeeklySchedule().get(dayKey);
                    if (lessons != null) {
                        for (int i = 0; i < lessons.size(); i++) {
                            Lesson l = lessons.get(i);
                            if (l != null && l.getSubject() != null &&
                                    l.getSubject().equalsIgnoreCase(draft.subject)) {
                                linkStorage.linkLatestHomeworkByUserSubjectDate(chatId, draft.subject, draft.dueDate, dayKey, i);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            pending.remove(chatId);
            user.setState(DialogState.REGISTERED);
            userStorage.updateUser(user);

            return "✅ Домашнее задание добавлено: " + (draft.subject == null ? "-" : draft.subject)
                    + " — " + (draft.description == null || draft.description.isEmpty() ? "-" : draft.description)
                    + " (до " + (draft.dueDate == null ? "-" : draft.dueDate.toString()) + "). Напомню за " + draft.remindBeforeDays + " дн.";
        }

        // По умолчанию — попросим начать заново
        user.setState(DialogState.ASK_HW_SUBJECT);
        userStorage.updateUser(user);
        return "Начнём заново. Введите предмет.";
    }

    private static class Draft {
        String subject;
        String description;
        LocalDate dueDate;
        int remindBeforeDays = 1;
    }
}
