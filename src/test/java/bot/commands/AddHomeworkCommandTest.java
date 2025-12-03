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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

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

        // используем конструктор команды, который принимает UserStorage
        cmd = new AddHomeworkCommand(mockUserStorage);

        // подмена приватных полей: storage, scheduleManager, linkStorage
        Field fStorage = AddHomeworkCommand.class.getDeclaredField("storage");
        Field fSched = AddHomeworkCommand.class.getDeclaredField("scheduleManager");
        Field fLink = AddHomeworkCommand.class.getDeclaredField("linkStorage");

        fStorage.setAccessible(true);
        fSched.setAccessible(true);
        fLink.setAccessible(true);

        // попытаться снять final
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(fStorage, fStorage.getModifiers() & ~Modifier.FINAL);
            modifiers.setInt(fSched, fSched.getModifiers() & ~Modifier.FINAL);
            modifiers.setInt(fLink, fLink.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {}

        fStorage.set(cmd, mockStorage);
        fSched.set(cmd, mockScheduleManager);
        fLink.set(cmd, mockLinkStorage);
    }

    @Test
    public void start_userNotFound_returnsErrorMessage() {
        long chatId = 900L;
        when(mockUserStorage.getUser(chatId)).thenReturn(null);

        SendMessage resp = cmd.start(chatId);
        assertNotNull(resp);
        assertTrue(resp.getText().contains("❌") && resp.getText().toLowerCase().contains("профиль"),
                "Ожидали сообщение про отсутствие профиля: " + resp.getText());
        assertEquals(String.valueOf(chatId), resp.getChatId());
    }

    @Test
    public void start_withSchedule_offersSubjectButtons() {
        long chatId = 901L;
        User user = new User(chatId);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        Schedule schedule = new Schedule("g", "group");
        Lesson lesson = new Lesson("Math", LocalTime.of(9, 0), LocalTime.of(10, 30), "101");
        schedule.addLesson("MONDAY", lesson);

        SendMessage resp = cmd.start(chatId);
        assertNotNull(resp);
        assertTrue(resp.getText().toLowerCase().contains("шаг 1/4"));
    }

    @Test
    public void handleStateMessage_subjectNotFound_returnsError() {
        long chatId = 902L;
        User user = new User(chatId);
        user.setState(DialogState.ASK_HW_SUBJECT);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        // schedule с другим предметом — команда должна вернуть сообщение об ошибке
        Schedule schedule = new Schedule("g", "group");
        schedule.addLesson("MONDAY", new Lesson("Physics", LocalTime.of(9,0), LocalTime.of(10,0), ""));
        when(mockScheduleManager.getScheduleForUser(chatId)).thenReturn(schedule);


        SendMessage resp = cmd.handleStateMessage(chatId, "Math");
        assertNotNull(resp);
        assertTrue(resp.getText().toLowerCase().contains("предмет не найден"),
                "Ожидали сообщение 'предмет не найден', получили: " + resp.getText());
    }

    /**
     * Негативный сценарий: ввод некорректного числа для remind-days.
     * Подход: проходим состояния subject -> date -> description, чтобы команда сама создала Draft,
     * затем отправляем некорректное значение remind.
     */
    @Test
    public void handleStateMessage_remindInvalidNumber_returnsError() {
        long chatId = 903L;
        User user = new User(chatId);
        // ставим начальное состояние — команда начнёт обрабатывать ввод предмета
        user.setState(DialogState.ASK_HW_SUBJECT);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        // 1) Subject (команда создаст Draft и переведёт юзера в ASK_HW_DATE)
        SendMessage s1 = cmd.handleStateMessage(chatId, "Math");
        assertNotNull(s1);
        assertTrue(user.getState() == DialogState.ASK_HW_TIME || s1.getText().toLowerCase().contains("шаг 2"),
                "Ожидали шаг 2/4 после предмета");

        // 2) Date (вводим явную дату в формате YYYY-MM-DD)
        String dateStr = LocalDate.now().plusDays(3).toString();
        SendMessage s2 = cmd.handleStateMessage(chatId, dateStr);
        assertNotNull(s2);
        assertTrue(user.getState() == DialogState.ASK_HW_DESCRIPTION || s2.getText().toLowerCase().contains("шаг 3"),
                "Ожидали шаг 3/4 после даты");

        // 3) Description (переведёт в ASK_HW_REMIND)
        SendMessage s3 = cmd.handleStateMessage(chatId, "Desc");
        assertNotNull(s3);
        assertTrue(user.getState() == DialogState.ASK_HW_REMIND || s3.getText().toLowerCase().contains("шаг 4"),
                "Ожидали шаг 4/4 после описания");

        // 4) Некорректный remind
        SendMessage resp = cmd.handleStateMessage(chatId, "-5");
        assertNotNull(resp);
        assertTrue(resp.getText().toLowerCase().contains("целое число") || resp.getText().toLowerCase().contains("укажите"),
                "Ожидали сообщение про требование целого числа. Получено: " + resp.getText());
    }

    /**
     * Позитивный сценарий: корректный remind -> вызвать storage.addHomework
     */
    @Test
    public void handleStateMessage_remindValid_callsStorageAndCompletes() {
        long chatId = 904L;
        User user = new User(chatId);
        user.setState(DialogState.ASK_HW_SUBJECT);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        // 1) Subject
        SendMessage s1 = cmd.handleStateMessage(chatId, "Math");
        assertNotNull(s1);

        // 2) Date
        String dateStr = LocalDate.now().plusDays(2).toString();
        SendMessage s2 = cmd.handleStateMessage(chatId, dateStr);
        assertNotNull(s2);

        // 3) Description
        SendMessage s3 = cmd.handleStateMessage(chatId, "Solve problems");
        assertNotNull(s3);

        // Prepare storage stub: addHomework не выбросит исключение
        doNothing().when(mockStorage).addHomework(eq(chatId), anyString(), anyString(), any(LocalDate.class), anyInt());

        // 4) Правильный remind
        SendMessage resp = cmd.handleStateMessage(chatId, "2");
        assertNotNull(resp);
        assertTrue(resp.getText().toLowerCase().contains("домашнее задание") && resp.getText().contains("✅"),
                "Ожидали подтверждение добавления домашнего задания. Получено: " + resp.getText());

        // Проверяем, что storage.addHomework вызвано
        verify(mockStorage, times(1)).addHomework(eq(chatId), eq("Math"), eq("Solve problems"), any(LocalDate.class), eq(2));
    }
}
