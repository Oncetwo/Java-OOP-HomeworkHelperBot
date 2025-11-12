package bot.commands;

import bot.session.EditSessionManager;
import bot.session.Session;
import bot.fsm.DialogState;
import bot.schedule.Lesson;
import bot.schedule.Schedule;
import bot.schedule.ScheduleManager;
import bot.user.User;
import bot.user.UserStorage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class EditScheduleCommand implements Command {

    private final UserStorage userStorage;
    private final ScheduleManager scheduleManager;

    public EditScheduleCommand(UserStorage userStorage, ScheduleManager scheduleManager) {
        this.userStorage = userStorage;
        this.scheduleManager = scheduleManager;
    }

    @Override
    public String getName() {
        return "/editSchedule";
    }

    @Override
    public String getInformation() {
        return "Изменить расписание на конкретный день (добавить или удалить пару)";
    }

    @Override
    public String realization(String[] args) {
        return "Используйте: /editSchedule <день недели>, например: /editSchedule monday";
    }

    /**
     * Начало редактирования: принимает /editSchedule <day>
     * Устанавливает сессию, сохраняет состояние EDIT_CHOOSE_ACTION и отправляет клавиатуру с кнопками Добавить/Удалить.
     */
    public SendMessage processChange(long chatId, String[] args) {
        User user = userStorage.getUser(chatId);
        if (user == null) {
            return createMessage(chatId, "❌❌❌ Сначала зарегистрируйтесь командой /start");
        }

        if (args == null || args.length < 2 || args[1].trim().isEmpty()) {
            return createMessage(chatId, "❌❌❌ Укажите день недели, например: /editSchedule monday");
        }

        String dayInput = args[1].trim();
        String dayLower = dayInput.toLowerCase();

        // Сбрасываем флаг ожидания кнопки, чтобы StartCommand не перехватывал нажатия.
        user.setWaitingForButton(false);
        userStorage.updateUser(user);

        // Создаём/обновляем сессию редактирования
        Session session = EditSessionManager.getSession(chatId);
        session.setDay(dayLower); // временно сохраняем lower; позже заменим на найденный ключ (если нужен)

        // Если у пользователя ещё нет кастомного расписания — копируем общее
        if (!user.getHasCustomSchedule()) {
            scheduleManager.copyCommonToCustom(chatId);
            // флаг меняется внутри copyCommonToCustom
        }

        Schedule schedule = scheduleManager.getScheduleForUser(chatId);
        if (schedule == null) {
            return createMessage(chatId, "❌ Ошибка: расписание не найдено. Попробуйте позже.");
        }

        // Попытка найти уроки и определить реальный ключ дня (matchedDay)
        List<Lesson> lessons = schedule.getLessonsForDay(dayLower);
        String matchedDay = null;
        if (lessons != null && !lessons.isEmpty()) {
            matchedDay = dayLower;
        } else {
            String cap = dayLower.substring(0, 1).toUpperCase() + dayLower.substring(1).toLowerCase();
            lessons = schedule.getLessonsForDay(cap);
            if (lessons != null && !lessons.isEmpty()) {
                matchedDay = cap;
            } else {
                lessons = schedule.getLessonsForDay(dayLower.toUpperCase());
                if (lessons != null && !lessons.isEmpty()) {
                    matchedDay = dayLower.toUpperCase();
                }
            }
        }

        // Если нашли пары под каким-то ключом — сохраняем этот ключ в сессии, чтобы дальнейшие операции использовали его.
        if (matchedDay != null) {
            session.setDay(matchedDay);
        } else {
            // оставляем lower если ничего не найдено — новые пары будут добавлены под этим ключом
            session.setDay(dayLower);
        }

        StringBuilder text = new StringBuilder("🎓 Расписание на " + session.getDay() + ":\n\n");
        if (lessons == null || lessons.isEmpty()) {
            text.append("Пары отсутствуют.\n\n");
        } else {
            for (int i = 0; i < lessons.size(); i++) {
                Lesson les = lessons.get(i);
                text.append(i + 1).append(". ")
                        .append(les.getSubject())
                        .append(" (").append(les.getStartTime())
                        .append(" - ").append(les.getEndTime())
                        .append(", ").append(les.getClassroom()).append(")\n");
            }
            text.append("\n");
        }
        text.append("Выберите действие:");

        // Устанавливаем состояние диалога на выбор действия
        user.setState(DialogState.EDIT_CHOOSE_ACTION);
        userStorage.updateUser(user);

        List<String> options = List.of("Добавить", "Удалить");
        return createMessageWithDynamicButtons(chatId, text.toString(), options);
    }

    /**
     * Обработка шагов редактирования — вызывается FSM, когда состояние пользователя относится к редактированию.
     */
    public SendMessage processEdit(long chatId, String rawMessageText) {
        String messageText = rawMessageText == null ? "" : rawMessageText.trim();
        // Диагностический лог
        System.out.println("EditScheduleCommand.processEdit: chatId=" + chatId + " message='" + messageText + "'");

        User user = userStorage.getUser(chatId);
        if (user == null) {
            return createMessage(chatId, "❌ Ошибка: пользователь не найден. Введите /start для регистрации.");
        }

        Session session = EditSessionManager.getSession(chatId);
        if (session == null || session.getDay() == null) {
            // Если сессия потеряна — попросим начать заново
            user.setState(DialogState.REGISTERED);
            userStorage.updateUser(user);
            return createMessage(chatId, "❌ Сессия редактирования утрачена. Введите /editSchedule <день> чтобы начать заново.");
        }

        String day = session.getDay();
        Schedule schedule = scheduleManager.getScheduleForUser(chatId);
        if (schedule == null) {
            return createMessage(chatId, "❌ Ошибка: расписание не найдено. Попробуйте позже.");
        }

        switch (user.getState()) {
            case EDIT_CHOOSE_ACTION:
                if (messageText.equalsIgnoreCase("Добавить")) {
                    user.setState(DialogState.ASK_SUBJECT);
                    userStorage.updateUser(user);
                    return createMessage(chatId, "Введите название предмета:");
                } else if (messageText.equalsIgnoreCase("Удалить")) {
                    user.setState(DialogState.ASK_LESSON_INDEX);
                    userStorage.updateUser(user);
                    return createMessage(chatId, "Введите номер пары для удаления:");
                } else {
                    return createMessage(chatId, "❌❌❌ Пожалуйста, выберите действие с помощью кнопки: Добавить или Удалить");
                }

            case ASK_SUBJECT:
                if (messageText.isEmpty()) {
                    return createMessage(chatId, "❌ Название предмета не может быть пустым. Введите название предмета:");
                }
                session.setSubject(messageText);
                user.setState(DialogState.ASK_ROOM);
                userStorage.updateUser(user);
                return createMessage(chatId, "Введите номер аудитории:");

            case ASK_ROOM:
                if (messageText.isEmpty()) {
                    return createMessage(chatId, "❌ Номер аудитории не может быть пустым. Введите аудиторию:");
                }
                session.setRoom(messageText);
                user.setState(DialogState.ASK_TIME_BEGIN);
                userStorage.updateUser(user);
                return createMessage(chatId, "Введите время начала (например, 09:00):");

            case ASK_TIME_BEGIN:
                if (messageText.isEmpty()) {
                    return createMessage(chatId, "❌ Время начала не может быть пустым. Введите время начала (например, 09:00):");
                }
                session.setTimeBegin(messageText);
                user.setState(DialogState.ASK_TIME_END);
                userStorage.updateUser(user);
                return createMessage(chatId, "Введите время окончания (например, 10:30):");

            case ASK_TIME_END:
                if (messageText.isEmpty()) {
                    return createMessage(chatId, "❌ Время окончания не может быть пустым. Введите время окончания (например, 10:30):");
                }
                session.setTimeEnd(messageText);

                // Парсим время и сохраняем новую пару
                try {
                    LocalTime begin = LocalTime.parse(session.getTimeBegin());
                    LocalTime end = LocalTime.parse(session.getTimeEnd());
                    Lesson newLesson = new Lesson(session.getSubject(), begin, end, session.getRoom());

                    // Используем session.getDay() как ключ (уже нормализован в processChange)
                    schedule.addLesson(session.getDay(), newLesson);
                    scheduleManager.saveCustomSchedule(chatId, schedule);

                    user.setState(DialogState.REGISTERED);
                    userStorage.updateUser(user);
                    EditSessionManager.clearSession(chatId);

                    return createMessage(chatId, "✅ Пара успешно добавлена!");
                } catch (DateTimeParseException e) {
                    // Оставляем состояние ASK_TIME_END, просим ввести корректно
                    user.setState(DialogState.ASK_TIME_END);
                    userStorage.updateUser(user);
                    return createMessage(chatId, "❌ Формат времени некорректен. Введите время в формате HH:mm, например 09:00:");
                } catch (Exception e) {
                    e.printStackTrace();
                    return createMessage(chatId, "❌ Ошибка при сохранении пары. Попробуйте ещё раз.");
                }

            case ASK_LESSON_INDEX:
                int index;
                try {
                    index = Integer.parseInt(messageText) - 1;
                } catch (NumberFormatException e) {
                    return createMessage(chatId, "❌ Введите номер пары числом (например, 1):");
                }

                // Попытка получить уроки по session.getDay(), и если это пусто — пробуем известные варианты регистра
                List<Lesson> lessons = schedule.getLessonsForDay(day);
                if (lessons == null || lessons.isEmpty()) {
                    // try capitalized
                    String cap = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
                    lessons = schedule.getLessonsForDay(cap);
                    if (lessons != null && !lessons.isEmpty()) {
                        // сохраняем в сессии реальный ключ, чтобы последующие операции использовали его
                        session.setDay(cap);
                        day = cap;
                    } else {
                        // try upper
                        lessons = schedule.getLessonsForDay(day.toUpperCase());
                        if (lessons != null && !lessons.isEmpty()) {
                            session.setDay(day.toUpperCase());
                            day = day.toUpperCase();
                        }
                    }
                }

                if (lessons == null || lessons.isEmpty()) {
                    // Нечего удалять
                    user.setState(DialogState.REGISTERED);
                    userStorage.updateUser(user);
                    EditSessionManager.clearSession(chatId);
                    return createMessage(chatId, "❌ Пары отсутствуют для удаления.");
                }

                if (index < 0 || index >= lessons.size()) {
                    return createMessage(chatId, "❌ Пары с таким номером нет. Введите корректный номер:");
                }

                lessons.remove(index);

                try {
                    scheduleManager.saveCustomSchedule(chatId, schedule);

                    user.setState(DialogState.REGISTERED);
                    userStorage.updateUser(user);
                    EditSessionManager.clearSession(chatId);

                    return createMessage(chatId, "✅ Пара успешно удалена!");
                } catch (Exception e) {
                    e.printStackTrace();
                    return createMessage(chatId, "❌ Ошибка при удалении пары. Попробуйте ещё раз.");
                }

            default:
                // Если попали сюда — попросим начать заново
                user.setState(DialogState.REGISTERED);
                userStorage.updateUser(user);
                EditSessionManager.clearSession(chatId);
                return createMessage(chatId, "❌❌❌ Неожиданное состояние. Введите /editSchedule <день> чтобы начать заново.");
        }
    }

    private SendMessage createMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        return message;
    }

    private SendMessage createMessageWithDynamicButtons(long chatId, String text, List<String> options) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow currentRow = new KeyboardRow();

        for (int i = 0; i < options.size(); i++) {
            currentRow.add(new KeyboardButton(options.get(i)));
            if ((i + 1) % 2 == 0 || i == options.size() - 1) {
                keyboard.add(currentRow);
                currentRow = new KeyboardRow();
            }
        }

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);
        return message;
    }
}