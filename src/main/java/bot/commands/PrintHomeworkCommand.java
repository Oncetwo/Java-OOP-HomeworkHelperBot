package bot.commands;

import bot.homework.SQLiteHomeworkStorage;
import bot.homework.HomeworkItem;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

public class PrintHomeworkCommand implements Command {

    private final SQLiteHomeworkStorage storage;

    public PrintHomeworkCommand() {
        this.storage = new SQLiteHomeworkStorage();
        this.storage.initialize();
    }

    @Override
    public String getName() {
        return "/homework";
    }

    @Override
    public String getInformation() {
        return "Показать домашние задания:\n"
        		+ "/homework — все задания\n"
        		+ "/homework Monday — задания с дедлайном в понедельник\n"
        		+ "/homework 2025-11-20 — задания с дедлайном на конкретную дату\n"
        		+ "/homework *название предмета* — задания по конректному предмету";
    }

    @Override
    public String realization(String[] args) {
        return "Использование:\n" +
                "/homework — все задания\n" +
                "/homework Monday — задания с дедлайном в понедельник\n" +
                "/homework 2025-11-20 — задания с дедлайном на конкретную дату\n" +
                "/homework *название предмета* — задания по конректному предмету";
    }

    public String realizationWithChatId(long chatId, String[] args) {
        try {
            List<HomeworkItem> all = storage.getHomeworkByUser(chatId);
            if (args != null && args.length > 1 && args[1] != null && !args[1].trim().isEmpty()) {
                String dayOrSubject = args[1].trim();
                for (DayOfWeek d : DayOfWeek.values()) { //если был введён день (мондэй тьюсдей и т.д.)
                    if (d.name().equalsIgnoreCase(dayOrSubject)) {
                        List<HomeworkItem> filtered = all.stream() // собираем список для вывода по определённому дню
                                .filter(h -> h.getDueDate() != null && h.getDueDate().getDayOfWeek() == d)
                                .collect(Collectors.toList());// поток преобразуем в список
                        return formatHomeworkList(filtered, "ДЗ на " + d.name());
                    }
                }
                try {
                    LocalDate date = LocalDate.parse(dayOrSubject); // ну а тут если конкретная дата в нужном формате
                    List<HomeworkItem> filtered = all.stream()
                            .filter(h -> h.getDueDate() != null && h.getDueDate().equals(date))
                            .collect(Collectors.toList());
                    return formatHomeworkList(filtered, "ДЗ на " + date.toString());
                } catch (DateTimeParseException ignored) {
                    // не дата — интерпретировать как предмет и показать по предмету
                    List<HomeworkItem> filtered = storage.getHomeworkBySubject(chatId, dayOrSubject);
                    return formatHomeworkList(filtered, "ДЗ по предмету: " + dayOrSubject);
                }
            }

            return formatHomeworkList(all, "Все домашние задания");

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Ошибка при получении домашних заданий.";
        }
    }

    private String formatHomeworkList(List<HomeworkItem> list, String header) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(":\n\n");
        if (list == null || list.isEmpty()) {
            sb.append(" — домашних заданий нет.");
            return sb.toString();
        }
        for (HomeworkItem item : list) {
            sb.append("ID: ").append(item.getId()).append(" | ")
                    .append(item.getSubject()).append(" — ").append(item.getDescription())
                    .append(" (до ").append(item.getDueDate()).append(") ")
                    .append(item.isCompleted() ? "✅" : "⏳")
                    .append("\n")
                    .append("\n");
        }
        return sb.toString();
    }
}
