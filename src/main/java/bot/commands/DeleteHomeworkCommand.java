package bot.commands;

import bot.homework.SQLiteHomeworkStorage;
import bot.homework.HomeworkItem;
import bot.homework.HomeworkLinkStorage;

import java.util.List;
import java.util.stream.Collectors;

public class DeleteHomeworkCommand implements Command {

    private final SQLiteHomeworkStorage storage;
    private final HomeworkLinkStorage linkStorage;

    public DeleteHomeworkCommand() {
        this.storage = new SQLiteHomeworkStorage();
        this.storage.initialize();
        this.linkStorage = new HomeworkLinkStorage();
    }

    @Override
    public String getName() {
        return "/deletehw";
    }

    @Override
    public String getInformation() {
        return "Удалить домашнее задание по ID.\n" +
                "Использование:\n" +
                "/deletehw — показать список ваших ДЗ с ID\n" +
                "/deletehw <id> — удалить задание с указанным ID";
    }

    @Override
    public String realization(String[] args) {
        return getInformation();
    }

    public String realizationWithChatId(long chatId, String[] args) {
        try {
            List<HomeworkItem> all = storage.getHomeworkByUser(chatId);

            // Если нет аргумента — вернём список с ID, чтобы пользователь увидел, что удалить
            if (args == null || args.length < 2 || args[1] == null || args[1].trim().isEmpty()) {
                if (all.isEmpty()) return "У вас нет домашнего задания.";
                String list = all.stream()
                        .map(h -> String.format("[%d] %s — %s (до %s)%s",
                                h.getId(),
                                h.getSubject() == null ? "-" : h.getSubject(),
                                h.getDescription() == null || h.getDescription().isEmpty() ? "-" : h.getDescription(),
                                h.getDueDate() == null ? "-" : h.getDueDate().toString(),
                                h.getRemindBeforeDays() > 0 ? " 🔔 за " + h.getRemindBeforeDays() + " дн." : ""))
                        .collect(Collectors.joining("\n"));
                return "Ваши задания:\n" + list + "\n\nЧтобы удалить — отправьте /deletehw <id>";
            }

            // Пытаемся распарсить ID
            String idStr = args[1].trim();
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                return "❌ Неверный ID. Укажите целое число. Пример: /deletehw 123";
            }

            // Проверим, принадлежит ли это задание пользователю
            boolean found = all.stream().anyMatch(h -> h.getId() == id);
            if (!found) {
                return "❌ Задание с таким ID не найдено у вас. Выполните /deletehw чтобы увидеть список с ID.";
            }

            // Удаляем связь в таблице homework_link
            try {
                linkStorage.unlinkHomework(id);
            } catch (Exception ignore) {
                try { ignore.printStackTrace(); } catch (Exception ex) {}
            }

            // Удаляем само задание
            storage.deleteHomework(id);

            return "✅ Домашнее задание с ID " + id + " удалено.";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Ошибка при попытке удалить задание. Попробуйте позже.";
        }
    }
}
