package bot.start;

import bot.commands.*;
import bot.user.*;
import bot.fsm.*;

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
    private final DialogStateMachine stateMachine;

    private final String envToken = System.getenv("BOT_TOKEN");

    public Homeworkbot() {
        userStorage = new SQLiteUserStorage();
        userStorage.initialize();
        startCommand = new StartCommand(userStorage);

        commands.put("/start", startCommand);
        commands.put("/about", new AboutCommand());
        commands.put("/authors", new AuthorsCommand());
        commands.put("/help", new HelpCommand(commands));
        commands.put("/schedule", new bot.commands.ScheduleCommand(userStorage));

        stateMachine = new DialogStateMachine(userStorage, startCommand);
    }

    @Override
    public void onUpdateReceived(Update update) {  // объект update - это все, что пришло от тг
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim(); // trim убирает пробелы с конца и начала строки
            long chatId = update.getMessage().getChatId(); // возвращает идентификатор чата (поле chatId в объекте - Message)
            String[] parts = text.split("\\s+", 2); // регулярное выражение - 1 или более пробельных символов
            String commandName = parts[0].toLowerCase(); // toLowerCase это чтобы регистр не мешал

            try {
                if (text.startsWith("/")) {  // Проверяем, является ли сообщение командой
                    
                    // Special command: /schedule - show user's saved schedule
                    if (commandName.equals("/schedule")) {
                        try {
                            bot.user.User user = userStorage.getUser(chatId);
                            if (user == null) {
                                sendText(chatId, "Вы не зарегистрированы. Введите /start чтобы зарегистрироваться.");
                                return;
                            }
                            bot.schedule.SQLiteScheduleStorage ss = new bot.schedule.SQLiteScheduleStorage("schedules.db");
                            ss.initialize();
                            bot.schedule.Schedule sched = ss.getScheduleByGroupName(user.getGroup());
                            if (sched == null) {
                                sendText(chatId, "Расписание для вашей группы не найдено. Вы можете загрузить его командой /start (повторная регистрация) или подождать пока оно будет загружено.");
                                ss.close();
                                return;
                            } else {
                                String out = formatScheduleForUser(sched);
                                sendText(chatId, out);
                                ss.close();
                                return;
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            sendText(chatId, "Ошибка при получении расписания: " + ex.getMessage());
                            return;
                        }
                    }
Command cmd = commands.get(commandName); // ищем значение по ключу в мапе
                    if (cmd != null) {
                        if (cmd instanceof StartCommand) { // сравниваем тип (если cmd типа StartCommand)
                            execute(startCommand.processStart(chatId));  // возвращает сообщение + кнопки
                            // execute метод для отправки сложного сообщения (с кнопками)
                        } else if (cmd instanceof bot.commands.ScheduleCommand) {
                            bot.commands.ScheduleCommand sc = (bot.commands.ScheduleCommand) cmd;
                            String out = sc.getScheduleForChat(chatId);
                            sendText(chatId, out);
                        } else { // обработка остальных команд
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

    

    // Helper: format Schedule to readable text
    private String formatScheduleForUser(bot.schedule.Schedule sched) {
        StringBuilder sb = new StringBuilder();
        sb.append("Расписание для группы: ").append(sched.getGroupName() == null ? "" : sched.getGroupName()).append("\n\n");
        // Order days Monday..Sunday
        java.util.List<java.time.DayOfWeek> days = java.util.Arrays.asList(
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY,
                java.time.DayOfWeek.SUNDAY
        );
        for (java.time.DayOfWeek d : days) {
            String key = d.toString(); // WEEKDAY names are stored as uppercase
            java.util.List<bot.schedule.Lesson> lessons = sched.getWeeklySchedule().get(key);
            sb.append(d.toString()).append(":\n");
            if (lessons == null || lessons.isEmpty()) {
                sb.append("  — пар нет\n");
            } else {
                for (bot.schedule.Lesson L : lessons) {
                    String st = L.getStartTime() == null ? "" : L.getStartTime().toString();
                    String et = L.getEndTime() == null ? "" : L.getEndTime().toString();
                    sb.append("  ").append(st).append(" - ").append(et).append(" | ").append(L.getSubject() == null ? "" : L.getSubject());
                    if (L.getClassroom() != null && !L.getClassroom().isEmpty()) sb.append(" (").append(L.getClassroom()).append(")");
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
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
