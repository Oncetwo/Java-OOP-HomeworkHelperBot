package bot.fsm;

import bot.commands.StartCommand;
import bot.user.*;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class DialogStateMachine {

    private final UserStorage userStorage; // final -после присвоения в конструкторе значение больше нельзя изменить
    private final StartCommand startCommand;

    public DialogStateMachine(UserStorage userStorage, StartCommand startCommand) {
        this.userStorage = userStorage;
        this.startCommand = startCommand;
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

        switch (state) { // после регистрации — обрабатываем основные команды
            case REGISTERED:
            	
            	
            	
            	

            default:
                return sendSimple(chatId, "Неизвестная команда. Введите /help для просмотра доступных команд");
        }
    }



    private SendMessage sendSimple(long chatId, String text) { // метод для отправки сообщений
        return new SendMessage(String.valueOf(chatId), text);
    }
}
