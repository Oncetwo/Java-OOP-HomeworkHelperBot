package bot.commands;

import bot.user.User;
import bot.user.UserStorage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

public class ShareGroupCommand implements Command {

    private final UserStorage userStorage;
    private final String botUsername;
    private final int expiryDays; // срок действия ссылки в днях

    public ShareGroupCommand(UserStorage userStorage, String botUsername, int expiryDays) {
        this.userStorage = userStorage;
        this.botUsername = botUsername;
        this.expiryDays = expiryDays;
    }

    @Override
    public String getName() { return "/sharegroup"; }

    @Override
    public String getInformation() { return "Создать deep-link приглашение (поделиться своей группой)"; }

    @Override
    public String realization(String[] args) {
        return "Использование: /sharegroup — создать приглашение (действует " + expiryDays + " дней)";
    }

    // Вызывается из Homeworkbot при получении /sharegroup
    public String start(long chatId) {
        User user = userStorage.getUser(chatId);
        if (user == null) return "❌ Вы не зарегистрированы. Введите /start чтобы зарегистрироваться.";
        String group = user.getGroup();
        if (group == null || group.trim().isEmpty()) return "❌ У вас не указана группа в профиле.";

        try {
            long inviter = chatId;
            // Формируем JSON — только inviter.
            String json = String.format("{\"inviter\":%d}", inviter);

            // Base64 в URLEncode
            String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String urlSafe = URLEncoder.encode(base64, StandardCharsets.UTF_8);

            String startParam = "invite_" + urlSafe;
            String link = "https://t.me/" + botUsername + "?start=" + startParam;

            return "✅ Ссылка-приглашение создана:\n" +
                    link +
                    "\n\nЕсли при переходе по ссылке приглашение не активируется автоматически, " +
                    "скопируйте и отправьте боту сообщение:\n" +
                    "/start " + startParam;
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Ошибка при создании приглашения.";
        }
    }

    // JSON-экранировка для значений (кавычки и обратный слеш)
    private String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
