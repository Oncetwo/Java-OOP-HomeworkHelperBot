package bot.fsm;

import bot.commands.StartCommand;
import bot.commands.EditScheduleCommand;
import bot.commands.InviteHandler; 
import bot.user.User;
import bot.user.UserStorage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class DialogStateMachine {

    private final UserStorage userStorage;
    private final StartCommand startCommand;
    private final EditScheduleCommand editScheduleCommand;
    private final InviteHandler inviteHandler; 

    // конструктор 
    public DialogStateMachine(UserStorage userStorage, StartCommand startCommand,
                              EditScheduleCommand editScheduleCommand, InviteHandler inviteHandler) {
        this.userStorage = userStorage;
        this.startCommand = startCommand;
        this.editScheduleCommand = editScheduleCommand;
        this.inviteHandler = inviteHandler;
    }
    

    public SendMessage handleInput(long chatId, String messageText) {
        User user = userStorage.getUser(chatId);

        if (user == null) { // если пользователь новый — создаем его
            user = new User(chatId);
            userStorage.saveUser(user);
            
            if (messageText != null && messageText.startsWith("/start ")) {
                SendMessage reply = inviteHandler.tryProcessInvite(chatId, messageText);
                if (reply != null) {
                    return reply; // найден deep-link - выходим
                }
            }
            
        }

        // Команда /start всегда обрабатываем в приоритете
        if (messageText != null && messageText.equalsIgnoreCase("/start")) {
            return startCommand.processStart(chatId);
        }


        if (user.getWaitingForButton() && messageText != null) {
            return startCommand.processButtonResponse(chatId, messageText);
        }
        
        
        if (user.getState() == bot.fsm.DialogState.ASK_NAME_INVITE) {
            return startCommand.processRegistration(chatId, messageText);
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
