package bot.fsm;

import bot.commands.StartCommand;
import bot.commands.EditScheduleCommand; // подключаем нашу команду
import bot.user.*;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class DialogStateMachine {

    private final UserStorage userStorage; // final -после присвоения в конструкторе значение больше нельзя изменить
    private final StartCommand startCommand;
    private final EditScheduleCommand editScheduleCommand; // добавляем поле для EditScheduleCommand

    public DialogStateMachine(UserStorage userStorage, StartCommand startCommand, EditScheduleCommand editScheduleCommand) {
        this.userStorage = userStorage;
        this.startCommand = startCommand;
        this.editScheduleCommand = editScheduleCommand;
    }

    public SendMessage handleInput(long chatId, String messageText) { // обработка сообщений пользователя
        User user = userStorage.getUser(chatId);

        if (user == null) { // если пользователь новый — создаем его
            user = new User(chatId);
            userStorage.saveUser(user);
        }

        DialogState state = user.getState(); // текущее состояние пользователя

        if (messageText.equalsIgnoreCase("/start")) { // это перезапустит регистрацию, если пользователь в ней напишет /start
            return startCommand.processStart(chatId);
        }

        if (user.getWaitingForButton() && messageText != null) { // пользователь ждет ответа на кнопки
            return startCommand.processButtonResponse(chatId, messageText);
        }

        if (startCommand.isUserInRegistration(chatId)) { // пользователь в процессе регистрации
            return startCommand.processRegistration(chatId, messageText);
        }

        if (messageText.toLowerCase().startsWith("/editschedule")) {
            String[] args = messageText.split("\\s+"); // разделяем команду на аргументы
            return editScheduleCommand.processChange(chatId, args); // вызываем processChange
        }

        switch (state) { // после регистрации — обрабатываем основные команды
            case EDIT_CHOOSE_ACTION:
            case ASK_SUBJECT:
            case ASK_ROOM:
            case ASK_TIME_BEGIN:
            case ASK_TIME_END:
            case ASK_LESSON_INDEX:
                return editScheduleCommand.processEdit(chatId, messageText);

            case REGISTERED:
            default:
                return sendSimple(chatId, "Неизвестная команда. Введите /help для просмотра доступных команд");
        }
    }

    private SendMessage sendSimple(long chatId, String text) { // метод для отправки сообщений
        return new SendMessage(String.valueOf(chatId), text);
    }
}
