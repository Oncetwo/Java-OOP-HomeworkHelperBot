package bot.fsm;

import bot.commands.StartCommand;
import bot.commands.EditScheduleCommand;
import bot.user.User;
import bot.user.UserStorage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class DialogStateMachine {

    private final UserStorage userStorage;
    private final StartCommand startCommand;
    private final EditScheduleCommand editScheduleCommand;

    public DialogStateMachine(UserStorage userStorage, StartCommand startCommand, EditScheduleCommand editScheduleCommand) {
        this.userStorage = userStorage;
        this.startCommand = startCommand;
        this.editScheduleCommand = editScheduleCommand;
    }

    public SendMessage handleInput(long chatId, String messageText) {
        User user = userStorage.getUser(chatId);

        if (user == null) { // если пользователь новый — создаем его
            user = new User(chatId);
            userStorage.saveUser(user);
        }


        // Команда /start всегда обрабатываем в приоритете
        if (messageText != null && messageText.equalsIgnoreCase("/start")) {
            return startCommand.processStart(chatId);
        }

        
        if (user.getWaitingForButton() && messageText != null) {
            return startCommand.processButtonResponse(chatId, messageText);
        }

        // Если пользователь в процессе регистрации (state != REGISTERED) — направляем все сообщения в StartCommand.processRegistration
        if (startCommand.isUserInRegistration(chatId)) {
            return startCommand.processRegistration(chatId, messageText);
        }

        // Команда /editschedule (начало редактирования)
        if (messageText != null && messageText.toLowerCase().startsWith("/editschedule")) {
            String[] args = messageText.split("\\s+");
            return editScheduleCommand.processChange(chatId, args);
        }

        // Если пользователь находится в состоянии редактирования — направляем в EditScheduleCommand.processEdit
        switch (user.getState()) {
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

    private SendMessage sendSimple(long chatId, String text) {
        return new SendMessage(String.valueOf(chatId), text);
    }
}