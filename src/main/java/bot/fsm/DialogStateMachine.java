package bot.fsm;

import bot.commands.AddHomeworkCommand;
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
    private final AddHomeworkCommand addHomeworkCommand;


    // конструктор 
    public DialogStateMachine(UserStorage userStorage, StartCommand startCommand,
                              EditScheduleCommand editScheduleCommand, InviteHandler inviteHandler, AddHomeworkCommand addHomeworkCommand) {
        this.userStorage = userStorage;
        this.startCommand = startCommand;
        this.editScheduleCommand = editScheduleCommand;
        this.inviteHandler = inviteHandler;
        this.addHomeworkCommand = addHomeworkCommand;
    }
    

    public SendMessage handleInput(long chatId, String messageText) {
        // --- обработка deep-link (invite) для любого пользователя (нового или уже зарегистрированного) ---
        if (messageText != null && messageText.startsWith("/start ")) {
            SendMessage reply = inviteHandler.tryProcessInvite(chatId, messageText);
            if (reply != null) {
                return reply; // инвайт обработан — выходим
            }
        }

        User user = userStorage.getUser(chatId);

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

            case ASK_HW_SUBJECT:
            case ASK_HW_TIME:
            case ASK_HW_DESCRIPTION:
            case ASK_HW_REMIND: {
                // Доп. защита: не позволяем незарегистрированным продолжать (на случай рассинхронизации)
                if (user.getState() != DialogState.REGISTERED
                        && !(user.getState() == DialogState.ASK_HW_SUBJECT
                        || user.getState() == DialogState.ASK_HW_TIME
                        || user.getState() == DialogState.ASK_HW_DESCRIPTION
                        || user.getState() == DialogState.ASK_HW_REMIND)) {
                    return sendSimple(chatId, "❌ Для добавления домашнего задания вы должны быть зарегистрированы. Введите /start.");
                }
                String reply = addHomeworkCommand.handleStateMessage(chatId, messageText);
                return new SendMessage(String.valueOf(chatId), reply);
            }
            case REGISTERED:
            default:
                return sendSimple(chatId, "Неизвестная команда. Введите /help для просмотра доступных команд");
        }
    }

    private SendMessage sendSimple(long chatId, String text) {
        return new SendMessage(String.valueOf(chatId), text);
    }
}
