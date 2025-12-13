package bot.commands;

import bot.scheduler.DailyNotifier;
import bot.homework.SQLiteHomeworkStorage;
import bot.schedule.Lesson;
import bot.schedule.Schedule;
import bot.schedule.ScheduleManager;
import bot.start.Homeworkbot;
import bot.user.SQLiteUserStorage;
import bot.user.User;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DailyNotifierTest {

    // ----------------------------------------------
    // Минимальные структуры для TimeSource-логики
    // ----------------------------------------------

    public interface TimeSource {
        long milliseconds();
        long nanoseconds();
        void sleep(long millis) throws InterruptedException;
    }

    private static class ScheduledTask {
        final Runnable r;
        final long scheduledAtMs;
        volatile boolean executed = false;

        ScheduledTask(Runnable r, long scheduledAtMs) {
            this.r = r;
            this.scheduledAtMs = scheduledAtMs;
        }
    }

    private static class TestTimeSourceImpl implements TimeSource {
        private final AtomicLong now;
        private final List<ScheduledTask> shared;

        TestTimeSourceImpl(long startMs, List<ScheduledTask> shared) {
            this.now = new AtomicLong(startMs);
            this.shared = shared;
        }

        @Override public long milliseconds() { return now.get(); }
        @Override public long nanoseconds() { return now.get() * 1_000_000L; }

        @Override
        public void sleep(long millis) {
            long newTime = now.addAndGet(millis);

            synchronized (shared) {
                for (ScheduledTask t : shared) {
                    if (!t.executed && t.scheduledAtMs <= newTime) {
                        try { t.r.run(); } catch (Throwable ignored) {}
                        t.executed = true;
                    }
                }
            }
        }
    }

    private static class TestScheduledExecutor implements ScheduledExecutorService {
        private final List<ScheduledTask> shared;
        private final TimeSource ts;

        TestScheduledExecutor(List<ScheduledTask> shared, TimeSource ts) {
            this.shared = shared;
            this.ts = ts;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            long at = ts.milliseconds() + unit.toMillis(delay);
            shared.add(new ScheduledTask(command, at));
            return mock(ScheduledFuture.class);
        }

        // Заглушки
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> c, long d, TimeUnit u) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long i, long p, TimeUnit u) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable r, long i, long d, TimeUnit u) { throw new UnsupportedOperationException(); }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit u) { return true; }
        @Override public <T> Future<T> submit(Callable<T> task) { throw new UnsupportedOperationException(); }
        @Override public <T> Future<T> submit(Runnable task, T result) { throw new UnsupportedOperationException(); }
        @Override public Future<?> submit(Runnable task) { throw new UnsupportedOperationException(); }
        @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public void execute(Runnable command) { command.run(); }
    }

    // ----------------------------------------------
    // Утилиты
    // ----------------------------------------------

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

    // ----------------------------------------------
    // Сам тест
    // ----------------------------------------------

    @Test
    public void notifier_executes_with_or_without_TimeSource() throws Exception {

        Homeworkbot mockBot = mock(Homeworkbot.class);
        SQLiteUserStorage mockUserStorage = mock(SQLiteUserStorage.class);
        SQLiteHomeworkStorage mockHw = mock(SQLiteHomeworkStorage.class);
        ScheduleManager mockScheduleManager = mock(ScheduleManager.class);

        DailyNotifier notifier = new DailyNotifier(mockBot, mockUserStorage, mockHw);

        setFieldIfPresent(notifier, "scheduleManager", mockScheduleManager);

        // Расписание
        User u = new User(555L);
        when(mockUserStorage.getRegisteredUsers()).thenReturn(List.of(u));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        LocalTime lastEnd = LocalTime.now(zone).plusMinutes(1);
        Lesson lesson = new Lesson("S", LocalTime.of(1,0), lastEnd, "A");
        Schedule schedule = new Schedule("g", "group");
        schedule.addLesson(today.getDayOfWeek().name(), lesson);

        when(mockScheduleManager.getScheduleForUser(u.getChatId())).thenReturn(schedule);

        // --- Проверяем, есть ли timeSource ---
        Field tsField = findTimeSourceField(notifier);

        if (tsField != null) {
            // ---------------------- режим TIME SOURCE ------------------------

            List<ScheduledTask> shared = Collections.synchronizedList(new ArrayList<>());
            TestTimeSourceImpl ts = new TestTimeSourceImpl(System.currentTimeMillis(), shared);

            tsField.setAccessible(true);

            // если тип совпадает — ставим напрямую
            if (tsField.getType().isAssignableFrom(TestTimeSourceImpl.class)) {
                tsField.set(notifier, ts);
            } else {
                // иначе создаём proxy под тип поля
                Class<?> iface = tsField.getType();
                InvocationHandler handler = (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "milliseconds": return ts.milliseconds();
                        case "nanoseconds": return ts.nanoseconds();
                        case "sleep": ts.sleep((Long) args[0]); return null;
                    }
                    return null;
                };
                Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface}, handler);
                tsField.set(notifier, proxy);
            }

            TestScheduledExecutor executor = new TestScheduledExecutor(shared, ts);
            setFieldIfPresent(notifier, "scheduler", executor);

            notifier.startAll();

            assertFalse(shared.isEmpty(), "Ожидали, что задача будет запланирована");

            ScheduledTask task = shared.get(0);
            long now = ts.milliseconds();
            long scheduledAt = task.scheduledAtMs;

            long delta = scheduledAt - now + 50;
            ts.sleep(delta);

            assertTrue(task.executed, "Задача должна выполниться после перемотки времени");

            verify(mockHw, atLeastOnce()).deleteOldHomework(any(LocalDate.class));
            verify(mockBot, atLeastOnce()).execute(any(SendMessage.class));

        } else {
            // ---------------------- FALLBACK режим ---------------------------

            ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);

            AtomicReference<Runnable> captured = new AtomicReference<>();

            when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(inv -> {
                        captured.set(inv.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            setFieldIfPresent(notifier, "scheduler", mockScheduler);

            notifier.startAll();

            assertNotNull(captured.get(), "Задача должна быть запланирована");

            captured.get().run();

            verify(mockHw, atLeastOnce()).deleteOldHomework(any(LocalDate.class));
            verify(mockBot, atLeastOnce()).execute(any(SendMessage.class));
        }
    }
}
