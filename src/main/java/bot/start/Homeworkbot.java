package bot.start;

import bot.commands.*; // импортирует все классы из пакета bot.commands
import bot.user.SQLiteUserStorage;
import bot.user.UserStorageInterface;

import org.telegram.telegrambots.bots.TelegramLongPollingBot; // класс, который реализует опрос тг сервера
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;
import java.util.TreeMap; // сортированный словарь

public class Homeworkbot extends TelegramLongPollingBot { // наследуется от базового класса Telegram бота, 

    private final Map<String, CommandInterface> commands = new TreeMap<String, CommandInterface>(); // динамический массив
    
    private final UserStorageInterface userStorage; // объявили ссылку на объект, который реализует интерфейс класса хранилища
    private final StartCommand startCommand; // объявили ссылку на объект класса старткоманд
 
    private final String envToken = System.getenv("BOT_TOKEN"); // читается переменная окружения

    public Homeworkbot() { // конструктор (добавляем наши команды в спискок команд)
        userStorage = new SQLiteUserStorage(); // инициализация хранилища пользователей
        userStorage.initialize();
        startCommand = new StartCommand(userStorage);
        
        commands.put("/start", startCommand);
        commands.put("/about", new AboutCommand());
        commands.put("/authors",new AuthorsCommand());
        commands.put("/help",new HelpCommand(commands));
    }

    @Override
    public void onUpdateReceived(Update update) { // объект update - это все, что пришло от тг
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim(); // trim убирает пробелы с конца и начала строки
            String[] parts = text.split("\\s+", 2); // регулярное выражение - 1 или более пробельных символов
            String commandName = parts[0].toLowerCase(); // toLowerCase это чтобы регистр не мешал
            long chatId = update.getMessage().getChatId(); // возвращает идентификатор чата (поле chatId в объекте - Message)

            try {
                if (text.startsWith("/")) { // Проверяем, является ли сообщение командой
                    CommandInterface cmd = commands.get(commandName); // ищем значение по ключу в мапе
                    if (cmd != null) {
                       
                        if (cmd instanceof StartCommand) { // сравниваем тип (если cmd типа StartCommand)
                            SendMessage response = startCommand.processStart(chatId); // возвращает сообщение + кнопки
                            execute(response); // метод для отправки сложного сообщения (с кнопками)
                            
                        } else { // обработка остальных команд
                            String response = cmd.realization(parts);
                            sendText(chatId, response);     
                        }
                    } 
                    else {
                        sendText(chatId, "неизвестная команда, введите /help для просмотра команд");
                    }
                } else {
                    if (startCommand.isWaitingForButtonResponse(chatId)) { //  проверяет, показывали ли мы этому пользователю кнопки
                        SendMessage response = startCommand.processButtonResponse(chatId, text); // обработка ответа кнопок
                        execute(response);
                    } else if (startCommand.isUserInRegistration(chatId)) { // ввел имя, но еще не ввел группу (в процессе регистрации)
                        SendMessage response = startCommand.processRegistration(chatId, text); // обработка шага регистрации
                        execute(response);
                    } else {
                        sendText(chatId, "Я понимаю только команды. Введите /help для списка команд.");
                    }
                }
            } catch (Exception e) {
            	System.out.println("Ошибка при обработке сообщения: " + e.getMessage());

                sendText(chatId, "❌ Произошла ошибка. Попробуйте еще раз.");
            }
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage msg = new SendMessage(); // создаем объект класса SendMessage
        // setChatId указывает куда отправить 
        msg.setChatId(String.valueOf(chatId)); // превращаем Id в число 
        msg.setText(text); // метод класса SendMessage указывает какой текст отправить
        try {
            execute(msg); // попытка отправить сообщение
        } 
        catch (TelegramApiException exception) {
          exception.printStackTrace(); // вывод в консоль отчет об ошибке
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