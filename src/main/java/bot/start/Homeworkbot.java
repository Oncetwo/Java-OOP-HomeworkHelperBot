package bot.start;

import bot.commands.*;
import bot.user.*;
import bot.fsm.*;
import bot.schedule.ScheduleManager;
import bot.homework.*;
import bot.scheduler.*;

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
    private final ShareGroupCommand shareGroupCommand;

    private final String envToken = System.getenv("BOT_TOKEN");
    
    private DailyNotifier notifier;                    
    private SQLiteHomeworkStorage hwStorageForNotifier; 

    public Homeworkbot() {
        userStorage = new SQLiteUserStorage();
        userStorage.initialize();
        startCommand = new StartCommand(userStorage);
        editScheduleCommand = new EditScheduleCommand(userStorage, new ScheduleManager(userStorage));
        shareGroupCommand = new ShareGroupCommand(userStorage, getBotUsername(), 1); // срок действия 1 день
        AddHomeworkCommand addHomeworkCommand = new AddHomeworkCommand(userStorage);
        
        commands.put("/start", startCommand);
        commands.put("/about", new AboutCommand());
        commands.put("/authors", new AuthorsCommand());
        commands.put("/help", new HelpCommand(commands));
        commands.put("/schedule", new ScheduleCommand(userStorage)); 
        commands.put("/editschedule", editScheduleCommand); 
        commands.put("/sharegroup", shareGroupCommand);
        commands.put("/addhw", addHomeworkCommand);
        commands.put("/homework", new PrintHomeworkCommand());
        commands.put("/markhw", new MarkHomeworkCommand(true));
        commands.put("/unmarkhw", new MarkHomeworkCommand(false));
        commands.put("/subscription", new SubscriptionCommand(userStorage));
        
        InviteHandler inviteHandler = new InviteHandler(userStorage); // создаем invitehandler

        stateMachine = new DialogStateMachine(userStorage, startCommand, editScheduleCommand, inviteHandler, addHomeworkCommand);
        
        initNotifier();

    }
    
    
    public void initNotifier() {
        SQLiteHomeworkStorage hw = new SQLiteHomeworkStorage(); // 1) Инициализируем hw storage
        hw.initialize();

        // 2) Проверяем, что userStorage — это SQLiteUserStorage, прежде чем кастить
        if (!(userStorage instanceof SQLiteUserStorage)) {
            System.out.println("UserStorage isn't SQLiteUserStorage — notifier requires SQLiteUserStorage. Not starting notifier.");
            try { hw.close(); } catch (Exception ignored) {} // Закрываем hw, так как notifier не будет использоваться
            return;
        }

        // 3) Создаём и запускаем DailyNotifier 
        try {
            DailyNotifier localNotifier = new DailyNotifier(this, (SQLiteUserStorage) userStorage, hw); 
            localNotifier.startAll(); // планируем рассылки
            this.notifier = localNotifier;
            this.hwStorageForNotifier = hw; // сохраняем ссылку на hw в полне класса, чтобы потом закрыть

            // 4) Регистрируем shutdown hook ( поток, который JVM автоматически запустит при нормальном завершении JVM)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown hook: останавливаем DailyNotifier...");
                try {
                    if (notifier != null) notifier.stop();
                } catch (Exception ignored) {}
                try {
                    if (hwStorageForNotifier != null) {
                    	hwStorageForNotifier.close();
                    }
                } catch (Exception ignored) {}
            }));

        } catch (Exception e) {
            System.out.println("Ошибка при старте DailyNotifier: " + e.getMessage());
            e.printStackTrace();
            try { hw.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onUpdateReceived(Update update) {  // объект update - это всё, что пришло от ТГ
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim(); // trim убирает пробелы с конца и начала строки
            long chatId = update.getMessage().getChatId(); // возвращает идентификатор чата
            
            if (text != null && text.startsWith("/start invite_")) {
                try {
                    SendMessage response = stateMachine.handleInput(chatId, text);
                    if (response != null) {
                        execute(response);
                        return; // Завершаем обработку, так как инвайт обработан
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка при обработке инвайта: " + e.getMessage());
                    sendText(chatId, "❌ Ошибка при обработке приглашения.");
                    return;
                }
            }

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

                        } else if (cmd instanceof ShareGroupCommand) {
                            String link = shareGroupCommand.start(chatId);
                            sendText(chatId, link);

                        } else if (cmd instanceof AddHomeworkCommand) {
                            AddHomeworkCommand ahref = (AddHomeworkCommand) cmd;
                            SendMessage response = ahref.start(chatId);
                            execute(response); 

                        } else if (cmd instanceof PrintHomeworkCommand) {
                            String response = ((PrintHomeworkCommand) cmd).realizationWithChatId(chatId, parts);
                            sendText(chatId, response);

                        } else if (cmd instanceof MarkHomeworkCommand) {
                            String response = ((MarkHomeworkCommand) cmd).realizationWithChatId(chatId, parts);
                            sendText(chatId, response);
                            
                        } else if (cmd instanceof SubscriptionCommand) {
                            String response = ((SubscriptionCommand) cmd).realizationWithChatId(chatId, parts);
                            sendText(chatId, response);  

                        } else {
                            sendText(chatId, cmd.realization(parts));
                        }

                    } else {
                        User user = userStorage.getUser(chatId);

                        if (user != null && user.getState() != DialogState.REGISTERED) {
                            // передаём команду в FSM — там она обработается как /skip
                            SendMessage resp = stateMachine.handleInput(chatId, text);
                            if (resp != null) {
                                execute(resp);
                                return;
                            }
                        }

                        // если не в диалоге — выводим стандартное сообщение
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