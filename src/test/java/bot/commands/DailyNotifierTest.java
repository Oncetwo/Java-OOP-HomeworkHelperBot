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
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DailyNotifierTest {

    // Утилита: установить приватное/final поле через reflection (попытка снять final при необходимости)
    private static void setFieldForce(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);

            // попытка обычной установки
            try {
                f.set(target, value);
                return;
            } catch (IllegalAccessException ignored) {}

            // если поле final — попробуем снять final-модификатор (в старых JVM работает)
            try {
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException ignored) {
                // в новых JVM может не работать — всё ещё пробуем set()
            }

            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось установить поле '" + fieldName + "' через reflection: " + e.getMessage(), e);
        }
    }

    // Простая вспомогательная функция — создаёт Schedule с одной парой, завершающейся через ~1 минуту (от now)
    private Schedule makeScheduleWithLessonEndingSoon() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalTime lastEnd = LocalTime.now(zone).plusMinutes(1);
        Lesson lesson = new Lesson("TEST_SUBJ", LocalTime.of(1, 0), lastEnd, "R101");
        Schedule s = new Schedule("g", "group");
        s.addLesson(today.getDayOfWeek().name(), lesson);
        return s;
    }

    @Test
    public void positiveScenario_notifier_sends_message_when_task_runs() throws Exception {
        // --- моки и окружение ---
        Homeworkbot mockBot = mock(Homeworkbot.class);
        SQLiteUserStorage mockUserStorage = mock(SQLiteUserStorage.class);
        SQLiteHomeworkStorage mockHw = mock(SQLiteHomeworkStorage.class);
        ScheduleManager mockScheduleManager = mock(ScheduleManager.class);

        // подставим одного пользователя (подписан)
        User user = new User(123L);
        user.setSubscriptionEnabled(true);
        when(mockUserStorage.getRegisteredUsers()).thenReturn(List.of(user));
        when(mockScheduleManager.getScheduleForUser(user.getChatId())).thenReturn(makeScheduleWithLessonEndingSoon());

        // создаём реальный DailyNotifier (он создаст свой scheduler, но мы его заменим)
        DailyNotifier notifier = new DailyNotifier(mockBot, mockUserStorage, mockHw);
        // подмена scheduleManager (чтобы не дергать реальный)
        setFieldForce(notifier, "scheduleManager", mockScheduleManager);

        // Перехват Runnable'а: мокаем ScheduledExecutorService и сохраняем Runnable/параметры
        AtomicReference<Runnable> captured = new AtomicReference<>();
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);

        when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> {
                    captured.set(inv.getArgument(0));
                    return mock(ScheduledFuture.class);
                });

        // Подставляем наш mock scheduler вместо приватного final поля
        setFieldForce(notifier, "scheduler", mockScheduler);

        // Запускаем планирование
        notifier.startAll();

        // Должен быть захваченный runnable
        assertNotNull(captured.get(), "Ожидали, что DailyNotifier запланирует задачу");

        // Симулируем наступление времени — вручную запускаем Runnable
        captured.get().run();

        // Проверяем — отправка сообщения и очистка старых домашних заданий должны быть вызваны
        verify(mockBot, atLeastOnce()).execute(any(SendMessage.class));
        verify(mockHw, atLeastOnce()).deleteOldHomework(any(LocalDate.class));
    }

    @Test
    public void negativeScenario_user_unsubscribed_no_message_sent() throws Exception {
        // --- моки и окружение ---
        Homeworkbot mockBot = mock(Homeworkbot.class);
        SQLiteUserStorage mockUserStorage = mock(SQLiteUserStorage.class);
        SQLiteHomeworkStorage mockHw = mock(SQLiteHomeworkStorage.class);
        ScheduleManager mockScheduleManager = mock(ScheduleManager.class);

        // пользователь отключил подписку
        User user = new User(777L);
        user.setSubscriptionEnabled(false);
        when(mockUserStorage.getRegisteredUsers()).thenReturn(List.of(user));
        when(mockScheduleManager.getScheduleForUser(user.getChatId())).thenReturn(makeScheduleWithLessonEndingSoon());

        DailyNotifier notifier = new DailyNotifier(mockBot, mockUserStorage, mockHw);
        setFieldForce(notifier, "scheduleManager", mockScheduleManager);

        AtomicReference<Runnable> captured = new AtomicReference<>();
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> {
                    captured.set(inv.getArgument(0));
                    return mock(ScheduledFuture.class);
                });

        setFieldForce(notifier, "scheduler", mockScheduler);

        notifier.startAll();

        assertNotNull(captured.get(), "Ожидали, что DailyNotifier запланирует задачу");

        // запускаем задачу — т.к. пользователь отписан, бот НЕ должен отправлять сообщений и НЕ должен вызывать deleteOldHomework
        captured.get().run();

        verifyNoInteractions(mockBot);
        verify(mockHw, never()).deleteOldHomework(any(LocalDate.class));
    }

    @Test
    public void staleTime_user_unsubscribed_before_run_no_message() throws Exception {
        // Сценарий: при планировании пользователь был подписан, но до выполнения задачи (прошло время) он отписался.
        // После этого задача выполняется — и бот не должен отправлять ничего.

        Homeworkbot mockBot = mock(Homeworkbot.class);
        SQLiteUserStorage mockUserStorage = mock(SQLiteUserStorage.class);
        SQLiteHomeworkStorage mockHw = mock(SQLiteHomeworkStorage.class);
        ScheduleManager mockScheduleManager = mock(ScheduleManager.class);

        // пользователь был подписан при планировании
        User user = new User(555L);
        user.setSubscriptionEnabled(true); // подписан при планировании
        when(mockUserStorage.getRegisteredUsers()).thenReturn(List.of(user));
        when(mockScheduleManager.getScheduleForUser(user.getChatId())).thenReturn(makeScheduleWithLessonEndingSoon());

        DailyNotifier notifier = new DailyNotifier(mockBot, mockUserStorage, mockHw);
        setFieldForce(notifier, "scheduleManager", mockScheduleManager);

        AtomicReference<Runnable> captured = new AtomicReference<>();
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> {
                    captured.set(inv.getArgument(0));
                    return mock(ScheduledFuture.class);
                });

        setFieldForce(notifier, "scheduler", mockScheduler);

        // Запланировать (пользователь был подписан)
        notifier.startAll();

        assertNotNull(captured.get(), "Ожидали, что DailyNotifier запланирует задачу");

        // --- Имитируем, что прошло время, и пользователь отписался перед выполнением ---
        user.setSubscriptionEnabled(false);

        // Выполняем Runnable (это момент, когда планировщик запустил задачу "поздно")
        captured.get().run();

        // Проверяем: бот не отправил сообщений, очистка не выполнялась
        verifyNoInteractions(mockBot);
        verify(mockHw, never()).deleteOldHomework(any(LocalDate.class));
    }


}
