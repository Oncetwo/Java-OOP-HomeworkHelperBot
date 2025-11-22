package bot.commands;

import bot.homework.SQLiteHomeworkStorage;
import bot.homework.HomeworkItem;
import java.util.List;

public class MarkHomeworkCommand implements Command {

    private final SQLiteHomeworkStorage storage;
    private final boolean markAsDone;

    public MarkHomeworkCommand(boolean markAsDone) {
        this.storage = new SQLiteHomeworkStorage();
        this.storage.initialize();
        this.markAsDone = markAsDone;
    }

    @Override
    public String getName() {
        return markAsDone ? "/markhw" : "/unmarkhw";
    }

    @Override
    public String getInformation() {
        return markAsDone ? "Отметить домашнее задание как выполненное: /markhw <id>"
                : "Снять отметку выполнения: /unmarkhw <id>";
    }

    @Override
    public String realization(String[] args) {
        return "Использование: " + (markAsDone ? "/markhw <id>" : "/unmarkhw <id>");
    }

    public String realizationWithChatId(long chatId, String[] args) { //передаём id чата и команду, полученную от пользователя, с аргументами
        if (args == null || args.length < 2 || args[1].trim().isEmpty()) {
            return realization(args);
        }
        String idText = args[1].trim(); // удаляем пробельные символы
        long id;
        try {
            id = Long.parseLong(idText);
        }
        catch (NumberFormatException e) {
            return "❌ Неверный ID. Использование: " + (markAsDone ? "/markhw <id>" : "/unmarkhw <id>");
        }

        try {
            List<HomeworkItem> items = storage.getHomeworkByUser(chatId);
            boolean found = items.stream().anyMatch(h -> h.getId() == id); //есть ли дз у пользователя
            if (!found) {
                return "❌ Задание с таким ID не найдено у вас.";
            }

            storage.markAsCompleted(id, markAsDone);
            return markAsDone ? "✅ Задание отмечено как выполненное." : "✅ Пометка выполнения снята.";
        }
        catch (Exception e) {
            e.printStackTrace();
            return "❌ Ошибка при изменении статуса задания.";
        }
    }
}
