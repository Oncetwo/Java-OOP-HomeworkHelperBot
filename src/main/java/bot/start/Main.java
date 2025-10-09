package bot.start;

import org.telegram.telegrambots.meta.TelegramBotsApi; // класс для регистрации бота и запуска соединения с Telegram.
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession; // cпособ соединения с сервером Telegram через опрос

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new Homeworkbot());
            System.out.println("Бот запущен!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
