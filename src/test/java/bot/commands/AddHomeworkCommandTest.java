package bot.commands;

import bot.fsm.DialogState;
import bot.homework.HomeworkLinkStorage;
import bot.homework.SQLiteHomeworkStorage;
import bot.schedule.Lesson;
import bot.schedule.Schedule;
import bot.schedule.ScheduleManager;
import bot.user.User;
import bot.user.UserStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AddHomeworkCommandTest {

    private UserStorage mockUserStorage;
    private SQLiteHomeworkStorage mockStorage;
    private ScheduleManager mockScheduleManager;
    private HomeworkLinkStorage mockLinkStorage;
    private AddHomeworkCommand cmd;

    @BeforeEach
    public void setup() throws Exception {
        mockUserStorage = mock(UserStorage.class);
        mockStorage = mock(SQLiteHomeworkStorage.class);
        mockScheduleManager = mock(ScheduleManager.class);
        mockLinkStorage = mock(HomeworkLinkStorage.class);

        cmd = new AddHomeworkCommand(mockUserStorage);

        Field fStorage = AddHomeworkCommand.class.getDeclaredField("storage");
        Field fSched = AddHomeworkCommand.class.getDeclaredField("scheduleManager");
        Field fLink = AddHomeworkCommand.class.getDeclaredField("linkStorage");

        fStorage.setAccessible(true);
        fSched.setAccessible(true);
        fLink.setAccessible(true);

        // Попытаться снять модификатор final если поле "modifiers" доступно
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(fStorage, fStorage.getModifiers() & ~Modifier.FINAL);
            modifiersField.setInt(fSched, fSched.getModifiers() & ~Modifier.FINAL);
            modifiersField.setInt(fLink, fLink.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {
            // на современных JVM поле modifiers отсутствует — ничего не делаем
        }

        // установим моки
        fStorage.set(cmd, mockStorage);
        fSched.set(cmd, mockScheduleManager);
        fLink.set(cmd, mockLinkStorage);
    }

    @Test
    public void start_userNotFound_returnsError() {
        long chatId = 500L;
        when(mockUserStorage.getUser(chatId)).thenReturn(null);

        String r = cmd.start(chatId);
        assertNotNull(r);
        assertTrue(r.toLowerCase().contains("❌ профиль не найден. введите /start."));
    }

    @Test
    public void fullInteractiveFlow_addsHomework() throws Exception {
        long chatId = 501L; // добавляем пользователя в базу и далем у него расписание
        User user = new User(chatId);
        user.setState(DialogState.ASK_HW_SUBJECT);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        LocalDate date = LocalDate.now().plusDays(1);
        String dayKey = date.getDayOfWeek().name();

        Schedule schedule = new Schedule("g", "group");
        Lesson lesson = new Lesson("Math", LocalTime.of(9,0), LocalTime.of(10,0), "101");
        schedule.addLesson(dayKey, lesson);

        when(mockScheduleManager.getScheduleForUser(chatId)).thenReturn(schedule);

        // предмет
        String resp1 = cmd.handleStateMessage(chatId, "Math");
        assertNotNull(resp1);
        assertTrue(resp1.toLowerCase().contains("шаг 2/4 — введите дату"));

        // дата
        String resp2 = cmd.handleStateMessage(chatId, date.toString());
        assertNotNull(resp2);
        assertTrue(resp2.toLowerCase().contains("шаг 3/4 — введите текст задания (или /skip для пропуска описания)."));

        // описание
        String resp3 = cmd.handleStateMessage(chatId, "Solve exercises 1-5");
        assertNotNull(resp3);
        assertTrue(resp3.toLowerCase().contains("шаг 4/4 — за сколько дней до дедлайна напомнить?"));

        // за сколько напомнить
        String resp4 = cmd.handleStateMessage(chatId, "1");
        assertNotNull(resp4);
        assertTrue(resp4.toLowerCase().contains("✅ домашнее задание добавлено:"));

        verify(mockStorage, atLeastOnce()).addHomework(eq(chatId), eq("Math"), anyString(), eq(date), anyInt()); // проверки на то, что в ходе теста были созданы экземпляры с нужными параметрами
        verify(mockLinkStorage, atLeastOnce()).linkLatestHomeworkByUserSubjectDate(eq(chatId), eq("Math"), eq(date), eq(dayKey), anyInt());
    }
}
