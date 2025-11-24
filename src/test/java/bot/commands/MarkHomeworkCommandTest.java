package bot.commands;

import bot.homework.HomeworkItem;
import bot.homework.SQLiteHomeworkStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MarkHomeworkCommandTest {

    private SQLiteHomeworkStorage mockStorage;
    private MarkHomeworkCommand markCmd;
    private MarkHomeworkCommand unmarkCmd;

    @BeforeEach
    public void setup() throws Exception { // создаём мок и команды и подменяем storage
        mockStorage = mock(SQLiteHomeworkStorage.class);
        markCmd = new MarkHomeworkCommand(true);
        unmarkCmd = new MarkHomeworkCommand(false);

        Field f1 = MarkHomeworkCommand.class.getDeclaredField("storage"); // получаем приватное поле storage
        f1.setAccessible(true); // позволяем менять приватное поле
        try { // блок для того, чтобы обмануть jvm (сказать, что поле больше не final)
            Field modifiersField = Field.class.getDeclaredField("modifiers"); // получаем приватное поле modifiers
            modifiersField.setAccessible(true);
            modifiersField.setInt(f1, f1.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {
        }
        f1.set(markCmd, mockStorage); // заменяем на мокнутый storage
        f1.set(unmarkCmd, mockStorage); // заменяем на мокнутый storage
    }

    @Test
    public void realizationWithChatId_invalidArg_showsUsage() {
        String result = markCmd.realizationWithChatId(400L, new String[]{"/markhw"});
        assertTrue(result.toLowerCase().contains("использование:"));
    }

    @Test
    public void realizationWithChatId_nonNumericId_showsError() {
        String r = markCmd.realizationWithChatId(400L, new String[]{"/markhw", "abc"});
        assertTrue(r.toLowerCase().contains("неверный id"));
    }

    @Test
    public void realizationWithChatId_markExisting_callsStorage() {
        long chatId = 401L;
        HomeworkItem it = new HomeworkItem(77, chatId, "X", "Y", LocalDate.now(), false, 1);
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(Collections.singletonList(it));

        String r = markCmd.realizationWithChatId(chatId, new String[]{"/markhw", "77"});
        assertNotNull(r);
        assertTrue(r.toLowerCase().contains("✅ задание отмечено как выполненное."));
        verify(mockStorage, times(1)).markAsCompleted(77, true);
    }

    @Test
    public void realizationWithChatId_unmarkExisting_callsStorage() {
        long chatId = 402L;
        HomeworkItem it = new HomeworkItem(88, chatId, "X", "Y", LocalDate.now(), true, 1);
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(Collections.singletonList(it));

        String r = unmarkCmd.realizationWithChatId(chatId, new String[]{"/unmarkhw", "88"});
        assertNotNull(r);
        assertTrue(r.toLowerCase().contains("✅ пометка выполнения снята."));
        verify(mockStorage, times(1)).markAsCompleted(88, false);
    }
}
