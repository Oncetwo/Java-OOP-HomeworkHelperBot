package bot.start;

import bot.commands.*;
import bot.user.*;
import bot.fsm.*;
import bot.schedule.ScheduleManager;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;
import java.util.TreeMap; // сортированный словарь

public class Homeworkbot extends TelegramLongPollingBot {

    private final Map<String, Command> commands = new TreeMap<>();

    private final UserStorage userStorage;
    private final StartCommand startCommand;
    private final EditScheduleCommand editScheduleCommand; 
    private final DialogStateMachine stateMachine;

    private final String envToken = System.getenv("BOT_TOKEN");

    public Homeworkbot() {
        userStorage = new SQLiteUserStorage();
        userStorage.initialize();
        startCommand = new StartCommand(userStorage);
        editScheduleCommand = new EditScheduleCommand(userStorage, new ScheduleManager(userStorage));

        
        commands.put("/start", startCommand);
        commands.put("/about", new AboutCommand());
        commands.put("/authors", new AuthorsCommand());
        commands.put("/help", new HelpCommand(commands));
        commands.put("/schedule", new ScheduleCommand(userStorage)); 
        commands.put("/editschedule", editScheduleCommand); 

        stateMachine = new DialogStateMachine(userStorage, startCommand, editScheduleCommand);
    }

    @Override
    public void onUpdateReceived(Update update) {  // объект update - это всё, что пришло от ТГ
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim(); // trim убирает пробелы с конца и начала строки
            long chatId = update.getMessage().getChatId(); // возвращает идентификатор чата
            String[] parts = text.split("\\s+", 2); // регулярка: 1 или более пробелов
            String commandName = parts[0].toLowerCase(); // toLowerCase чтобы регистр не мешал

            try {
                if (text.startsWith("/")) {  // Проверяем, является ли сообщение командой
                    Command cmd = commands.get(commandName); // ищем команду в мапе
                    if (cmd != null) {
                        if (cmd instanceof StartCommand) {
                            execute(startCommand.processStart(chatId));  // возвращает сообщение + кнопки
                        } else if (cmd instanceof ScheduleCommand) {
                            String response = ((ScheduleCommand) cmd).realizationWithChatId(chatId, parts);
                            sendText(chatId, response);
                        } else if (cmd instanceof EditScheduleCommand) { 
                            execute(((EditScheduleCommand) cmd).processChange(chatId, parts));
                        } else {
                            sendText(chatId, cmd.realization(parts));
                        }
                    } else {
                        sendText(chatId, "Неизвестная команда. Введите /help для списка команд.");
                    }
                } else {
                    // обычный ввод (FSM)
                    SendMessage response = stateMachine.handleInput(chatId, text);
                    execute(response);
                }

            } catch (Exception e) {
                System.out.println("Ошибка при обработке сообщения: " + e.getMessage());
                sendText(chatId, "❌ Произошла ошибка. Попробуйте ещё раз.");
            }
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "HomeworkHelperUrfu_bot";
    }

    @Override
    public String getBotToken() {
        return envToken;
    }
}
