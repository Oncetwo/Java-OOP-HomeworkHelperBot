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
import java.util.ArrayList;
import java.util.List;

public class EditScheduleCommand implements Command {

    private final UserStorage userStorage;
    private final ScheduleManager scheduleManager;

    public EditScheduleCommand(UserStorage userStorage, ScheduleManager scheduleManager) { // констурктор 
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

 
    public SendMessage processChange(long chatId, String[] args) { // основной метод (сообщение +кнопки)
        User user = userStorage.getUser(chatId);
        
        if (user == null) {
            return createMessage(chatId, "❌❌❌ Сначала зарегистрируйтесь командой /start");
        }

        if (args.length < 2) { // если не введен день
            return createMessage(chatId, "❌❌❌ Укажите день недели, например: /editSchedule monday");
        }

        String day = args[1].trim().toLowerCase(); // без пробелов и в нижем регистре - день 

        Session session = EditSessionManager.getSession(chatId); // Создаём сессию и сохраняем выбранный день
        session.setDay(day);

        
        if (!user.getHasCustomSchedule()) { // Если нет кастомного расписания, копируем из общего
            scheduleManager.copyCommonToCustom(chatId);
        }

        Schedule schedule = scheduleManager.getScheduleForUser(chatId); // берем расписание (точно кастомное)
        List<Lesson> lessons = schedule.getLessonsForDay(day);

        // Формируем текст текущего расписания
        StringBuilder text = new StringBuilder("🎓 Расписание на " + day + ":\n\n");
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

   
        user.setState(DialogState.EDIT_CHOOSE_ACTION); // меняем состояние на состояние выбора
        userStorage.updateUser(user);

        List<String> options = List.of("Добавить", "Удалить");
        return createMessageWithDynamicButtons(chatId, text.toString(), options); // Отправляем кнопки
    }


    public SendMessage processEdit(long chatId, String messageText) { // диалог
        User user = userStorage.getUser(chatId);
        Session session = EditSessionManager.getSession(chatId);
        Schedule schedule = scheduleManager.getScheduleForUser(chatId);
        String day = session.getDay();
        
        switch (user.getState()) {

            case EDIT_CHOOSE_ACTION: // Пользователь выбирает "Добавить" или "Удалить"
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

            case ASK_SUBJECT: // Ввод предмета для новой пары
                session.setSubject(messageText.trim());
                user.setState(DialogState.ASK_ROOM);
                userStorage.updateUser(user);
                return createMessage(chatId, "Введите номер аудитории:");

            case ASK_ROOM: // Ввод аудитории для новой пары
                session.setRoom(messageText.trim());
                user.setState(DialogState.ASK_TIME_BEGIN);
                userStorage.updateUser(user);
                return createMessage(chatId, "Введите время начала (например, 09:00):");

            case ASK_TIME_BEGIN: // Ввод времени начала новой пары
                session.setTimeBegin(messageText.trim());
                user.setState(DialogState.ASK_TIME_END);
                userStorage.updateUser(user);
                return createMessage(chatId, "Введите время окончания (например, 10:30):");

            case ASK_TIME_END: // Ввод времени окончания новой пары и сохранение
                session.setTimeEnd(messageText.trim());

                Lesson newLesson = new Lesson(
                        session.getSubject(),
                        LocalTime.parse(session.getTimeBegin()),
                        LocalTime.parse(session.getTimeEnd()),
                        session.getRoom()
                );

                schedule.addLesson(day, newLesson); // Добавляем пару в расписание

                scheduleManager.saveCustomSchedule(chatId, schedule); // Сохраняем кастомное расписание

                user.setState(DialogState.REGISTERED);
                userStorage.updateUser(user);
                
                EditSessionManager.clearSession(chatId); // закрываем сессию

                return createMessage(chatId, "✅ Пара успешно добавлена!");

            case ASK_LESSON_INDEX: // Ввод номера пары для удаления
                int index;
                try {
                    index = Integer.parseInt(messageText.trim()) - 1;
                } catch (NumberFormatException e) {
                    return createMessage(chatId, "❌❌❌ Введите номер пары числом!");
                }

                List<Lesson> lessons = schedule.getLessonsForDay(day);
                
                if (lessons == null || index < 0 || index >= lessons.size()) {
                    return createMessage(chatId, "❌❌❌ Пары с таким номером нет!");
                }

                lessons.remove(index); // Удаляем выбранную пару

                scheduleManager.saveCustomSchedule(chatId, schedule); // Сохраняем кастомное расписание

                user.setState(DialogState.REGISTERED);
                userStorage.updateUser(user);
                EditSessionManager.clearSession(chatId); // закрываем сессию

                return createMessage(chatId, "✅ Пара успешно удалена!");

            default:
                return createMessage(chatId, "❌❌❌ Неожиданное состояние. Введите /editSchedule <день>");
        }
    }


    
    private SendMessage createMessage(long chatId, String text) { // создание сообщения
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        return message;
    }


    private SendMessage createMessageWithDynamicButtons(long chatId, String text, List<String> options) { // создание сообщения с кнопками
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
