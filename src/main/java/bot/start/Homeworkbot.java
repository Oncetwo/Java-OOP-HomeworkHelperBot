package bot.start;

import bot.commands.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

public class Homeworkbot extends TelegramLongPollingBot { // наследуется от базового класса Telegram бота, 
	//который использует long polling (периодический опрос сервера)
	

    private final List<CommandInterface> commands = new ArrayList<>();
 
    private final String envToken = System.getenv("BOT_TOKEN"); // хранение токена через переменную окружения

    public Homeworkbot() { // конструктор (добавляем наши команды в спискок команд)
        commands.add(new AboutCommand());
        commands.add(new AuthorsCommand());
        commands.add(new HelpCommand(commands));
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim();
            String[] parts = text.split("\\s+", 2);
            String commandName = parts[0];
            long chatId = update.getMessage().getChatId();

            for (CommandInterface cmd : commands) {
                if (cmd.getName().equalsIgnoreCase(commandName)) {
                    String response = cmd.realization(parts);
                    sendText(chatId, response);
                    return;
                }
            }
            sendText(chatId, "Неизвестная команда. Введите /help");
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
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
