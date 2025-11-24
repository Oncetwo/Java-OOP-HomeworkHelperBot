package bot.commands;

import bot.homework.HomeworkItem;
import bot.homework.SQLiteHomeworkStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PrintHomeworkCommandTest {

    private SQLiteHomeworkStorage mockStorage;
    private PrintHomeworkCommand cmd;

    @BeforeEach
    public void setup() throws Exception { // аналогично с марк команд делаем так, чтобы приватное поле подменилось
        mockStorage = mock(SQLiteHomeworkStorage.class);
        cmd = new PrintHomeworkCommand();

        Field f = PrintHomeworkCommand.class.getDeclaredField("storage");
        f.setAccessible(true);

        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {
        }

        f.set(cmd, mockStorage);
    }

    @Test
    public void realizationWithChatId_noArgs_returnsAll() { // без аргументов
        long chatId = 300L;
        HomeworkItem a = new HomeworkItem(1, chatId, "Math", "Desc", LocalDate.of(2025,11,20), false, 1);
        HomeworkItem b = new HomeworkItem(2, chatId, "Phys", "Desc2", LocalDate.of(2025,11,21), true, 1);
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(Arrays.asList(a,b));

        String out = cmd.realizationWithChatId(chatId, new String[]{"/homework"});
        assertNotNull(out);
        assertTrue(out.toLowerCase().contains("все домашние") && out.contains("ID: 1") && out.contains("ID: 2"));
    }

    @Test
    public void realizationWithChatId_filterByDay_returnsFiltered() { // на день
        long chatId = 301L;
        HomeworkItem a = new HomeworkItem(1, chatId, "Math", "Desc", LocalDate.of(2025,11,24), false, 1);
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(Arrays.asList(a));

        String out = cmd.realizationWithChatId(chatId, new String[]{"/homework", "MONDAY"});
        assertNotNull(out);
        assertTrue(out.toLowerCase().contains("дз") || out.contains("ID: 1"));
    }

    @Test
    public void realizationWithChatId_filterBySubject_usesStorageMethod() { // через предмет
        long chatId = 302L;
        HomeworkItem a = new HomeworkItem(10, chatId, "Chemistry", "Lab", LocalDate.of(2025,12,1), false, 1);
        when(mockStorage.getHomeworkBySubject(chatId, "Chemistry")).thenReturn(Collections.singletonList(a));

        String out = cmd.realizationWithChatId(chatId, new String[]{"/homework", "Chemistry"});
        assertNotNull(out);
        assertTrue(out.toLowerCase().contains("дз по предмету") || out.contains("Chemistry"));
    }
}
