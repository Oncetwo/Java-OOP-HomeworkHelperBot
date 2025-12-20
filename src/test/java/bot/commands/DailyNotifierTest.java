package bot.commands;

import bot.homework.SQLiteHomeworkStorage;
import bot.schedule.Lesson;
import bot.schedule.Schedule;
import bot.schedule.ScheduleManager;
import bot.start.Homeworkbot;
import bot.user.SQLiteUserStorage;
import bot.user.User;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import bot.scheduler.DailyNotifier;

import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DailyNotifierTest {

    public interface TimeSource {
        long milliseconds();
        void sleep(long millis) throws InterruptedException;
    }

    private static class ScheduledTask { // контейнер для одной запланированной задачи
        final Runnable run;
        final long scheduledAtMs;
        volatile boolean executed = false;

        ScheduledTask(Runnable r, long scheduledAtMs) {
            this.run = r;
            this.scheduledAtMs = scheduledAtMs;
        }
    }

    private static class TestTimeSourceImpl implements TimeSource { // реализация источника времени
        private final AtomicLong now;
        private final List<ScheduledTask> shared;

        TestTimeSourceImpl(long startMs, List<ScheduledTask> shared) {
            this.now = new AtomicLong(startMs);
            this.shared = shared;
        }

        @Override public long milliseconds() { return now.get(); }

        @Override
        public void sleep(long millis) { // проверяет все запланированные задачи
            long newTime = now.addAndGet(millis);
            synchronized (shared) {
                for (ScheduledTask task : shared) {
                    if (!task.executed && task.scheduledAtMs <= newTime) {
                        try { task.run.run(); } catch (Throwable ignored) {}
                        task.executed = true;
                    }
                }
            }
        }
    }

    private static class TestScheduledExecutor{
        private final List<ScheduledTask> shared;
        private final TimeSource ts;

        TestScheduledExecutor(List<ScheduledTask> shared, TimeSource ts) {
            this.shared = shared;
            this.ts = ts;
        }
    }

    private static boolean setFieldIfPresent(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Field findTimeSourceField(Object target) {
        for (Field f : target.getClass().getDeclaredFields()) {
            Class<?> t = f.getType();
            if (!t.isInterface()) continue;
            try {
                t.getMethod("milliseconds");
                t.getMethod("sleep", long.class);
                return f;
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Test
    public void positive() throws Exception {
        Homeworkbot mockBot = mock(Homeworkbot.class);
        SQLiteUserStorage mockUserStorage = mock(SQLiteUserStorage.class);
        SQLiteHomeworkStorage mockHw = mock(SQLiteHomeworkStorage.class);
        ScheduleManager mockScheduleManager = mock(ScheduleManager.class);

        // создаём notifier как в проекте
        DailyNotifier notifier = new DailyNotifier(mockBot, mockUserStorage, mockHw);
        setFieldIfPresent(notifier, "scheduleManager", mockScheduleManager);

        // используем ту же таймзону, что в DailyNotifier (жёстко задана в классе)
        ZoneId zone = ZoneId.of("Asia/Yekaterinburg");

        // пользователь
        User u = new User(777L);
        when(mockUserStorage.getRegisteredUsers()).thenReturn(List.of(u));
        LocalTime nowLocal = LocalTime.now(zone);
        LocalTime lastEndTime = nowLocal.minusMinutes(60);
        Lesson lesson = new Lesson("Mathematics", lastEndTime.minusHours(1), lastEndTime, "101");
        LocalDate today = LocalDate.now(zone);
        Schedule schedule = new Schedule("g", "group");
        schedule.addLesson(today.getDayOfWeek().name(), lesson);
        when(mockScheduleManager.getScheduleForUser(u.getChatId())).thenReturn(schedule);

        Field tsField = findTimeSourceField(notifier);

        List<ScheduledTask> shared = Collections.synchronizedList(new ArrayList<>());

        // ожидаемая отправка
        long expectedSendAt = LocalDateTime.of(today, lastEndTime).plusMinutes(60).atZone(zone).toInstant().toEpochMilli();

        // стартуем за 30 минут до expectedSendAt (чтобы попасть в допустимое окно)
        long startMs = expectedSendAt - TimeUnit.MINUTES.toMillis(30);
        TestTimeSourceImpl ts = new TestTimeSourceImpl(startMs, shared);

        tsField.setAccessible(true);
        tsField.set(notifier, ts);

        TestScheduledExecutor executor = new TestScheduledExecutor(shared, ts);
        setFieldIfPresent(notifier, "scheduler", executor);

        notifier.startAll();

        assertFalse(shared.isEmpty(), "Ожидали, что задача будет запланирована");

        ScheduledTask task = shared.get(0);
        long nowMs = ts.milliseconds();

        // перемотаем до выполнения
        long delta = task.scheduledAtMs - nowMs + 50;
        ts.sleep(delta);

        assertTrue(task.executed);
        verify(mockHw, atLeastOnce()).deleteOldHomework(any(LocalDate.class));
        verify(mockBot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    public void negative() throws Exception {
        Homeworkbot mockBot = mock(Homeworkbot.class);
        SQLiteUserStorage mockUserStorage = mock(SQLiteUserStorage.class);
        SQLiteHomeworkStorage mockHw = mock(SQLiteHomeworkStorage.class);
        ScheduleManager mockScheduleManager = mock(ScheduleManager.class);

        DailyNotifier notifier = new DailyNotifier(mockBot, mockUserStorage, mockHw);
        setFieldIfPresent(notifier, "scheduleManager", mockScheduleManager);

        ZoneId zone = ZoneId.of("Asia/Yekaterinburg");
        LocalDate today = LocalDate.now(zone);

        // пользователь с подпиской выключенной
        User u = new User(888L);
        u.setSubscriptionEnabled(false);
        when(mockUserStorage.getRegisteredUsers()).thenReturn(List.of(u));

        // как и в позитивном тесте — делаем lastEnd так, чтобы sendAt ≈ now(zone)
        LocalTime nowLocal = LocalTime.now(zone);
        LocalTime lastEndTime = nowLocal.minusMinutes(60);
        Lesson lesson = new Lesson("Physics", lastEndTime.minusHours(1), lastEndTime, "202");
        Schedule schedule = new Schedule("g", "group");
        schedule.addLesson(today.getDayOfWeek().name(), lesson);
        when(mockScheduleManager.getScheduleForUser(u.getChatId())).thenReturn(schedule);

        Field tsField = findTimeSourceField(notifier);

        List<ScheduledTask> shared = Collections.synchronizedList(new ArrayList<>());

        long expectedSendAt = LocalDateTime.of(today, lastEndTime).plusMinutes(60).atZone(zone).toInstant().toEpochMilli();
        long startMs = expectedSendAt - TimeUnit.MINUTES.toMillis(30);
        TestTimeSourceImpl ts = new TestTimeSourceImpl(startMs, shared);

        tsField.setAccessible(true);
        tsField.set(notifier, ts);

        TestScheduledExecutor executor = new TestScheduledExecutor(shared, ts);
        setFieldIfPresent(notifier, "scheduler", executor);

        notifier.startAll();

        assertFalse(shared.isEmpty());

        ScheduledTask task = shared.get(0);
        long nowMs = ts.milliseconds();
        long delta = task.scheduledAtMs - nowMs + 50;
        ts.sleep(delta);

        assertTrue(task.executed);
        verify(mockBot, never()).execute(any(SendMessage.class));
    }
}
