package bot.scheduler;

import bot.homework.HomeworkItem;
import bot.homework.SQLiteHomeworkStorage;
import bot.schedule.Lesson;
import bot.schedule.Schedule;
import bot.schedule.ScheduleManager;
import bot.start.Homeworkbot;
import bot.user.User;
import bot.user.SQLiteUserStorage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;


/**
 * DailyNotifier — класс, который ежедневно собирает и отправляет пользователям
 * вечерние уведомления о предметах следующего дня и домашних заданиях.
 *
 * Как он работает (вкратце):
 *  - При старте (startAll()) берёт всех зарегистрированных пользователей из SQLiteUserStorage.
 *  - Для каждого пользователя планирует задачу отправки:
 *      - вычисляет время окончания последней пары на текущий день (если есть) и ставит отправку на +1.5 часа,
 *      - или (если пар нет) ставит отправку на фиксированное время (20:00) текущего дня.
 *  - В момент исполнения задачи формируется сообщение:
 *      - список предметов на следующий день (через ScheduleManager),
 *      - активные домашние задания, связанные с предметами следующего дня
 *        (SQLiteHomeworkStorage.getActiveHomeworkBySubjects),
 *      - задания с кастомным дедлайном на следующий день (getHomeworkWithCustomDeadline),
 *      - сообщение отправляется через Homeworkbot.execute(SendMessage).
 *
 */
public class DailyNotifier {

    private final Homeworkbot bot; // экземпляр бота — для отправки сообщений
    private final SQLiteUserStorage userStorage;
    private final SQLiteHomeworkStorage hwStorage;
    private final ScheduleManager scheduleManager;
    private final ScheduledExecutorService scheduler; // планировщик задач (Позволяет запускать задачи с задержкой)
    private final ZoneId zone; // временная зона для вычислений (совместно с ScheduleFetcher)


    private final long retryDelaySeconds = 60; // Повторная попытка отправки при ошибке (секунды)


    public DailyNotifier(Homeworkbot bot,
                         SQLiteUserStorage userStorage,
                         SQLiteHomeworkStorage hwStorage) {
        this.bot = bot;
        this.userStorage = userStorage;
        this.hwStorage = hwStorage;
        this.scheduleManager = new ScheduleManager(userStorage);
        this.scheduler = Executors.newScheduledThreadPool(4); // пул для параллельной отправки
        this.zone = ZoneId.of("Asia/Yekaterinburg");
    }


    public void startAll() { // Запускает планирование для всех зарегистрированных пользователей
        List<User> users;
        try {
            users = userStorage.getRegisteredUsers(); // возвращает только state = REGISTERED
        } catch (Exception e) {
            System.out.println("DailyNotifier: не удалось получить пользователей: " + e.getMessage());
            return;
        }

        for (User u : users) {
            scheduleForUser(u);
        }
    }


    public void scheduleForUser(User user) { // Планирует отправку уведомления для конкретного пользователя.
        LocalDate today = LocalDate.now(zone);

        // Вычисляем время окончания последней пары сегодня (если есть)
        Optional<LocalDateTime> lastEnd = getLastLessonEndForUserOn(user, today);
        // Optional<T> — это контейнер, который либо содержит значение типа T, либо пуст

        LocalDateTime sendAt;
        if (lastEnd.isPresent()) { // если optional не пустой
            sendAt = lastEnd.get().plusMinutes(60); // +1 час
        } else {
            sendAt = LocalDateTime.of(today, LocalTime.of(15, 00)); // фиксированное 15:00
        }

        if (sendAt.isBefore(LocalDateTime.now(zone))) {
            sendAt = sendAt.plusDays(1);
        }

        long delayMillis = Duration.between(LocalDateTime.now(zone), sendAt).toMillis(); // берем разницу во времени (сейчас - когда должны  отправить) в милисек

        scheduler.schedule(() -> runSendForUser(user), delayMillis, TimeUnit.MILLISECONDS); // лямбда фунция, которая выполняет runSendForUser через определенную задержку
    }


    /**
     * Внутренняя логика — формирует и отправляет сообщение пользователю.
     * В случае ошибки повторяет отправку через retryDelaySeconds.
     * После попытки отправки перепланирует рассылку для этого пользователя через 24 часа.
     */
    private void runSendForUser(User user) {

        try {

            if (!user.getSubscriptionEnabled()) {
                System.out.println("Пользователь " + user.getChatId() + " отключил рассылку - пропускаем");
                return;
            }

            try { // перед рассылкой удаляем старые дз
                hwStorage.deleteOldHomework(LocalDate.now(zone));
            } catch (Exception e) {
                System.out.println("Ошибка при очистке старых ДЗ: " + e.getMessage());
            }

            LocalDate today = LocalDate.now(zone);
            LocalDate nextDay = today.plusDays(1);

            // Получаем расписание пользователя (может быть null)
            Schedule schedule = null;
            try {
                schedule = scheduleManager.getScheduleForUser(user.getChatId());
            } catch (Exception e) {
                System.out.println("DailyNotifier: не удалось получить расписание для пользователя " + user.getChatId() + ": " + e.getMessage());
            } // логируем, но все равно продолжаем


            // Получаем пары на следующий день (с учётом регистра ключей)
            List<Lesson> lessonsNextDay;
            if (schedule != null) {
                lessonsNextDay = getLessonsIgnoreCase(schedule, nextDay.getDayOfWeek().name());
            } else {
                lessonsNextDay = Collections.emptyList();
            }


            // Собираем список названий предметов для запроса домашних заданий
            List<String> subjectNames = new ArrayList<>();
            for (Lesson l : lessonsNextDay) {
                if (l != null && l.getSubject() != null && !l.getSubject().trim().isEmpty()) {
                    subjectNames.add(l.getSubject().trim());
                }
            }


            // 1) Домашние задания, связанные с предметами следующего дня
            List<HomeworkItem> hwForNextDay = Collections.emptyList();
            try {
                hwForNextDay = hwStorage.getActiveHomeworkBySubjects(user.getChatId(), subjectNames);
            } catch (Exception e) {
                System.out.println("DailyNotifier: ошибка получения hwForNextDay for user " + user.getChatId() + ": " + e.getMessage());
            }

            // 2) Задания с кастомным дедлайном на nextDay (исключая предметы, которые уже учтены)
            List<HomeworkItem> hwCustom = Collections.emptyList();
            try {
                hwCustom = hwStorage.getHomeworkWithCustomDeadline(user.getChatId(), subjectNames, nextDay);
            } catch (Exception e) {
                System.out.println("DailyNotifier: ошибка получения hwCustom for user " + user.getChatId() + ": " + e.getMessage());
            }

            // Формируем текст сообщения
            String message = buildMessage(user, nextDay, lessonsNextDay, hwForNextDay, hwCustom);

            // Отправляем сообщение через бот
            SendMessage sm = new SendMessage(String.valueOf(user.getChatId()), message);
            try {
                bot.execute(sm);
            } catch (TelegramApiException e) {
                System.out.println("DailyNotifier: ошибка отправки сообщения пользователю " + user.getChatId() + ": " + e.getMessage());
                // Повторим попытку позже
                scheduler.schedule(() -> runSendForUser(user), retryDelaySeconds, TimeUnit.SECONDS);
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scheduler.schedule(() -> scheduleForUser(user), 1, TimeUnit.MINUTES); // планируем отправку на следующий день
        }
    }



    private String buildMessage(User user,
                                LocalDate date,
                                List<Lesson> lessonsNextDay,
                                List<HomeworkItem> hwForNextDay,
                                List<HomeworkItem> hwCustom) {

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy"); // форматировщик дат из Java Time API
        StringBuilder sb = new StringBuilder();
        sb.append("Привет");
        if (user.getName() != null && !user.getName().isEmpty()) {
            sb.append(", ").append(user.getName());
        }
        sb.append("!\n\n");
        sb.append("План на ").append(date.format(df)).append(":\n\n");

        if (lessonsNextDay == null || lessonsNextDay.isEmpty()) {
            sb.append("— Завтра нет пар, можно отдохнуть!.\n\n");
        } else {
            sb.append("Предметы:\n");
            for (Lesson l : lessonsNextDay) {
                sb.append("- ").append(l.getSubject() == null ? "-" : l.getSubject());
                if (l.getStartTime() != null && l.getEndTime() != null) {
                    sb.append(" (").append(l.getStartTime()).append(" - ").append(l.getEndTime()).append(")");
                }
                if (l.getClassroom() != null && !l.getClassroom().isEmpty()) {
                    sb.append(" ").append(l.getClassroom());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("Домашние задания на завтра:\n");
        if (hwForNextDay == null || hwForNextDay.isEmpty()) {
            sb.append("— нет\n\n");
        } else {
            for (HomeworkItem h : hwForNextDay) {
                sb.append(formatHomeworkItem(h)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Задания с кастомным дедлайном на ").append(date.format(df)).append(":\n");
        if (hwCustom == null || hwCustom.isEmpty()) {
            sb.append("— нет\n");
        } else {
            for (HomeworkItem h : hwCustom) {
                sb.append(formatHomeworkItem(h)).append("\n");
            }
        }

        sb.append("\nДля управления задачами используйте /homework или добавьте новое /addhw.");
        return sb.toString();
    }


    private String formatHomeworkItem(HomeworkItem h) { // форматтер для одного домашнего задания
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(h.getId()).append(" | ");
        sb.append(h.getSubject() == null ? "-" : h.getSubject()).append(" — ");
        sb.append(h.getDescription() == null || h.getDescription().isEmpty() ? "-" : h.getDescription());
        sb.append(" (до ").append(h.getDueDate()).append(") ");
        sb.append(h.isCompleted() ? "✅" : "⏳");
        return sb.toString();
    }

    // Находит время окончания последней пары пользователя на указанную дату
    private Optional<LocalDateTime> getLastLessonEndForUserOn(User user, LocalDate date) {
        try {
            Schedule schedule = scheduleManager.getScheduleForUser(user.getChatId());
            if (schedule == null) {
                return Optional.empty();
            }

            List<Lesson> lessons = getLessonsIgnoreCase(schedule, date.getDayOfWeek().name());
            LocalTime latest = null;
            for (Lesson l : lessons) {
                if (l != null && l.getEndTime() != null) {
                    if (latest == null || l.getEndTime().isAfter(latest)) {
                        // isAfter(latest) возвращает true если у текущей пары время окончания позже чем latest
                        latest = l.getEndTime();
                    }
                }
            }
            if (latest == null) {
                return Optional.empty();
            }
            return Optional.of(LocalDateTime.of(date, latest));
        } catch (Exception e) {
            // если что-то пошло не так - возвращаем empty
            return Optional.empty();
        }
    }


    private List<Lesson> getLessonsIgnoreCase(Schedule sched, String dayKey) { // Возвращает список уроков из Schedule по ключу дня
        if (sched == null || sched.getWeeklySchedule() == null) {
            return Collections.emptyList();
        }

        Map<String, List<Lesson>> map = sched.getWeeklySchedule();

        if (map.containsKey(dayKey) && map.get(dayKey) != null) {
            return map.get(dayKey);
        }

        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(dayKey)) {
                return map.get(k);
            }
        }
        return Collections.emptyList();
    }


    public void stop() { // Остановить планировщик и закрыть ресурсы
        try {
            scheduler.shutdownNow();
        } finally {
            try {
                scheduleManager.close();
            } catch (Exception ignored) {}
            try {
                hwStorage.close();
            } catch (Exception ignored) {}
        }
    }
}