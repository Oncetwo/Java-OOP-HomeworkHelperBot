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

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

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
        return "Добавить домашнее задание по предмету";
    }

    @Override
    public String realization(String[] args) {
        return "Использование: /addhw *описание домашнего задания*.";
    }

    // запуск интерактивного потока
    public SendMessage start(long chatId) {
        User user = userStorage.getUser(chatId);
        if (user == null)
            return new SendMessage(String.valueOf(chatId),
                    "❌ Профиль не найден. Введите /start.");

        pending.remove(chatId);
        pending.put(chatId, new Draft());

        user.setState(DialogState.ASK_HW_SUBJECT);
        userStorage.updateUser(user);

        Schedule schedule = scheduleManager.getScheduleForUser(chatId);
        if (schedule == null) {
            return new SendMessage(String.valueOf(chatId),
                    "Шаг 1/4 — введите предмет (например: Математика).");
        }

        // собираем список предметов
        Set<String> subs = new LinkedHashSet<>();
        for (List<Lesson> dayLessons : schedule.getWeeklySchedule().values()) {
            if (dayLessons != null) {
                for (Lesson lesson : dayLessons) {
                    if (lesson != null && lesson.getSubject() != null) {
                        subs.add(lesson.getSubject().trim());
                    }
                }
            }
        }

        if (subs.isEmpty())
            return new SendMessage(String.valueOf(chatId),
                    "Шаг 1/4 — введите предмет (в расписании предметы не найдены).");

        List<String> buttons = new ArrayList<>(subs);

        return createMessageWithDynamicButtons(
                chatId,
                "Шаг 1/4 — выберите предмет или введите вручную:",
                buttons
        );
    }

    public SendMessage handleStateMessage(long chatId, String messageText) {
        if (messageText == null) messageText = "";
        String txt = messageText.trim();

        User user = userStorage.getUser(chatId);
        if (user == null) return createMessage(chatId, "❌ Профиль не найден. Введите /start.");
        Draft draft = pending.get(chatId);
        if (draft == null) {
            draft = new Draft();
            pending.put(chatId, draft);
        }
        //Предмет
        if (user.getState() == DialogState.ASK_HW_SUBJECT) {

            if (txt.isEmpty()) {
                return createMessage(chatId,"Введите предмет.");
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
                    return createMessage(chatId,"❌ Предмет не найден в расписании. Введите корректный предмет.");
                }
                else {
                    draft.subject = txt;
                    user.setState(DialogState.ASK_HW_TIME);
                    userStorage.updateUser(user);
                    List<String> days = List.of(
                            "MONDAY", "TUESDAY", "WEDNESDAY",
                            "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
                    );

                    return createMessageWithDynamicButtons(
                            chatId,
                            "Шаг 2/4 — выберите день недели или введите дату YYYY-MM-DD:",
                            days
                    );

                }
            }

        }
        // Время
        if (user.getState() == DialogState.ASK_HW_TIME) {
        	
        	List<String> days = List.of(
                    "MONDAY", "TUESDAY", "WEDNESDAY",
                    "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
            );
        	
        	if (txt.isEmpty()) {
                return createMessageWithDynamicButtons(
                        chatId,
                        "Шаг 2/4 — выберите день недели или введите дату YYYY-MM-DD:",
                        days
                );
            }

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
                	
                	 return createMessageWithDynamicButtons(
                             chatId,
                             "Неверный формат. Введите дату YYYY-MM-DD или выберите день недели:",
                             days
                     );
                }
            }

            draft.dueDate = date;

            // проверка: если расписание есть — убедиться, что предмет есть на этот день
            Schedule s = scheduleManager.getScheduleForUser(chatId);
            if (s != null && draft.subject != null) {
                String dayKey = date.getDayOfWeek().name();
                List<Lesson> lessons = getLessonsIgnoreCaseFromSchedule(s, dayKey);
                boolean ok = false;
                if (lessons != null) {
                    for (Lesson l : lessons) {
                        if (l != null && l.getSubject() != null && l.getSubject().equalsIgnoreCase(draft.subject.trim())) {
                            ok = true; break;
                        }
                    }
                }
                if (!ok) {
                    return createMessage(chatId,"❌ На указанную дату предмет не найден в расписании. Введите другую дату.");
                }
            }

            user.setState(DialogState.ASK_HW_DESCRIPTION);
            userStorage.updateUser(user);
            return createMessage(chatId,"Шаг 3/4 — введите текст задания (или /skip для пропуска описания).");
        }
        // Ожидаем описание
        if (user.getState() == DialogState.ASK_HW_DESCRIPTION) {
            if (txt.equalsIgnoreCase("/skip")) txt = "";
            draft.description = txt;

            // Переводим на шаг вопроса о напоминании
            user.setState(DialogState.ASK_HW_REMIND);
            userStorage.updateUser(user);
            return createMessage(chatId,"Шаг 4/4 — За сколько дней до дедлайна напомнить? Введите целое число (например: 1). Введите /skip для значения по умолчанию (1).");
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
                        return createMessage(chatId,"❌ Укажите целое число от 0 до 365 (0 — в день дедлайна), либо введите /skip.");
                    }
                } catch (NumberFormatException e) {
                    return createMessage(chatId, "❌ Неверный формат. Введите целое число (например 1) или /skip.");
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
                return createMessage(chatId,"❌ Ошибка при сохранении. Попробуйте позже.");
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

            return createMessage(chatId,"✅ Домашнее задание добавлено: " + (draft.subject == null ? "-" : draft.subject)
                    + " — " + (draft.description == null || draft.description.isEmpty() ? "-" : draft.description)
                    + " (до " + (draft.dueDate == null ? "-" : draft.dueDate.toString()) + "). Напомню за " + draft.remindBeforeDays + " дн.");
        }

        // По умолчанию — попросим начать заново
        user.setState(DialogState.ASK_HW_SUBJECT);
        userStorage.updateUser(user);
        return createMessage(chatId,"Начнём заново. Введите предмет.");
    }

    private static class Draft {
        String subject;
        String description;
        LocalDate dueDate;
        int remindBeforeDays = 1;
    }
    
    
    private SendMessage createMessageWithDynamicButtons(long chatId, String text, List<String> options) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup(); // ReplyKeyboardMarkup - класс для создания кастомной клавиатуры (в tg api)
        keyboardMarkup.setResizeKeyboard(true); // размер кнопок подстраивается под устройство
        keyboardMarkup.setOneTimeKeyboard(true); // Скрываем клавиатуру после нажатия

        List<KeyboardRow> keyboard = new ArrayList<>(); // Создаем список для строк кнопок

        KeyboardRow currentRow = new KeyboardRow(); // KeyboardRow - класс для представление 1 строки кнопок
        for (int i = 0; i < options.size(); i++) { // проходимся по всем элементам, которые должны быть кнопками
            currentRow.add(new KeyboardButton(options.get(i))); // для каждого элемента создается кнопка и добавляется в строку

            if ((i + 1) % 2 == 0 || i == options.size() - 1) { // первое это условие, что каждые 2 кнопки новая строка, а второе это если осталась 1 кнопка
                keyboard.add(currentRow); // добавляем строку в клаивиатуру
                currentRow = new KeyboardRow(); // делаем новую пустую строку
            }
        }

        keyboardMarkup.setKeyboard(keyboard); // keyboardMarkup метод класса ReplyKeyboardMarkup - устанавливает структуру кнопок (их местоположение)
        message.setReplyMarkup(keyboardMarkup);
        return message;
    }
    
    
    private SendMessage createMessage(long chatId, String text) { // создание сообщения
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // переводим Id в число
        message.setText(text);
        return message;
    }
    
    
 // >>> НОВОЕ: helper для нечувствительного к регистру поиска уроков в Schedule
    private List<Lesson> getLessonsIgnoreCaseFromSchedule(Schedule s, String dayKey) {
        if (s == null || s.getWeeklySchedule() == null) return Collections.emptyList();

        Map<String, List<Lesson>> map = s.getWeeklySchedule();

        // прямой поиск (на случай, если ключ совпадает точно)
        if (map.containsKey(dayKey) && map.get(dayKey) != null) {
            return map.get(dayKey);
        }

        // поиск нечувствительно к регистру — вернём первый найденный совпадающий ключ
        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(dayKey)) {
                return map.get(k) == null ? Collections.emptyList() : map.get(k);
            }
        }

        return Collections.emptyList();
    }

}



