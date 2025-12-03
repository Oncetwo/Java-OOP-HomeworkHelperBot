package bot.commands;

import bot.user.User;
import bot.user.UserStorage;
import bot.fsm.DialogState;
import bot.schedule.Schedule;
import bot.schedule.ScheduleFetcher;
import bot.schedule.ScheduleManager;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class InviteHandler {

    private final UserStorage userStorage;
    private final ObjectMapper mapper = new ObjectMapper();

    public InviteHandler(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public SendMessage tryProcessInvite(long chatId, String fullText) {
        if (fullText == null) return null;

        String[] parts = fullText.split("\\s+");
        if (parts.length < 2) return null;

        String param = parts[1];
        if (!param.startsWith("invite_")) return null;

        try {
            String encoded = param.substring("invite_".length());

            // Base64 из URLDecoded
            String base64 = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);

            JsonNode obj = mapper.readTree(json);

            long inviterId = obj.has("inviter") ? obj.get("inviter").asLong(-1) : -1;

            if (inviterId == -1) {
                return msg(chatId, "❌ Некорректные данные в приглашении.");
            }

            // ищем пригласившего
            User inviter = userStorage.getUser(inviterId);
            if (inviter == null) {
                return msg(chatId, "❌ Приглашающий пользователь не найден.");
            }

            // создаём или обновляем пользователя-цель
            User user = userStorage.getUser(chatId);
            boolean existed = (user != null);
            if (!existed) {
                user = new User(chatId);
            }

            user.setGroup(inviter.getGroup());
            user.setUniversity(inviter.getUniversity());
            user.setDepartment(inviter.getDepartment());
            user.setCourse(inviter.getCourse());
            user.setWaitingForButton(false);

            // если пользователь уже зарегистрирован — обновим и попытаемся подгрузить расписание
            if (existed && user.getState() == DialogState.REGISTERED) {
                userStorage.updateUser(user);

                try {
                    // Попытка загрузить расписание для подставленной группы (используем fetcher как раньше)
                    ScheduleFetcher fetcher = new ScheduleFetcher();
                    Schedule schedule = fetcher.fetchForUser(user);

                    ScheduleManager sm = new ScheduleManager(userStorage);
                    if (schedule != null) {
                        // Сохраняем общее расписание (как раньше)
                        sm.saveCommonSchedule(schedule);

                        // Если у пользователя был кастом — сбросим, чтобы новое общее вступило в силу
                        if (sm.customScheduleExists(chatId)) {
                            sm.resetToOriginalSchedule(chatId);
                        }
                        sm.close();

                        return msg(chatId,
                                "Вы перешли по приглашению! 🎉\n\n" +
                                        "Ваша группа и сопутствующие данные обновлены автоматически.\n" +
                                        "Расписание было успешно загружено и заменено на расписание приглашённой группы.\n\n" +
                                        "Группа: " + user.getGroup() + "\n" +
                                        "Институт: " + user.getUniversity() + "\n" +
                                        "Департамент: " + user.getDepartment() + "\n" +
                                        "Курс: " + user.getCourse());
                    } else {
                        sm.close();
                        return msg(chatId,
                                "Вы перешли по приглашению! 🎉\n\n" +
                                        "Данные группы обновлены, но не удалось загрузить расписание для этой группы.\n" +
                                        "Группа: " + user.getGroup() + "\n" +
                                        "Институт: " + user.getUniversity() + "\n" +
                                        "Департамент: " + user.getDepartment() + "\n" +
                                        "Курс: " + user.getCourse() + "\n\n" +
                                        "Попробуйте позже или проверьте корректность данных у пригласившего.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return msg(chatId, "❌ Ошибка при попытке загрузить расписание после перехода по приглашению.");
                }
            }

            // поведение для новых / незарегистрированных пользователей — как было
            user.setState(DialogState.ASK_NAME_INVITE);

            if (existed) {
                userStorage.updateUser(user);
            } else {
                userStorage.saveUser(user);
            }

            return msg(chatId,
                    "Вы перешли по приглашению! 🎉\n\n" +
                            "Я - умный помощник для студентов, который:\r\n"
                            + "📅 Автоматически следит за расписанием\r\n"
                            + "📚 Напоминает о домашних заданиях\r\n"
                            + "⏰ Присылает уведомления в удобное время\n\n" +
                            "Данные группы были подставлены автоматически:\n" +
                            "👥 Группа: " + user.getGroup() + "\n" +
                            "🏛️ Институт: " + user.getUniversity() + "\n" +
                            "📋 Департамент: " + user.getDepartment() + "\n" +
                            "🎓 Курс: " + user.getCourse() + "\n\n" +
                            "Для завершения регистрации введите ваше имя:");

        } catch (Exception e) {
            e.printStackTrace();
            return msg(chatId, "❌ Ошибка при обработке приглашения.");
        }
    }

    private SendMessage msg(long chatId, String text) {
        return new SendMessage(String.valueOf(chatId), text);
    }
}
