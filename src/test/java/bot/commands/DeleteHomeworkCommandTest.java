package bot.commands;

import bot.homework.HomeworkItem;
import bot.homework.HomeworkLinkStorage;
import bot.homework.SQLiteHomeworkStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DeleteHomeworkCommandTest {

    private SQLiteHomeworkStorage mockStorage;
    private HomeworkLinkStorage mockLink;
    private DeleteHomeworkCommand cmd;

    @BeforeEach
    public void setup() throws Exception {
        mockStorage = mock(SQLiteHomeworkStorage.class);
        mockLink = mock(HomeworkLinkStorage.class);

        cmd = new DeleteHomeworkCommand();

        // подменяем приватные поля
        Field fStorage = DeleteHomeworkCommand.class.getDeclaredField("storage");
        Field fLink = DeleteHomeworkCommand.class.getDeclaredField("linkStorage");
        fStorage.setAccessible(true);
        fLink.setAccessible(true);

        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(fStorage, fStorage.getModifiers() & ~Modifier.FINAL);
            modifiers.setInt(fLink, fLink.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {}

        fStorage.set(cmd, mockStorage);
        fLink.set(cmd, mockLink);
    }

    @Test
    public void realizationWithChatId_noArgs_noHomework_showsEmptyMessage() {
        long chatId = 1200L;
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(Collections.emptyList());

        String out = cmd.realizationWithChatId(chatId, new String[]{"/deletehw"});
        assertNotNull(out);
        assertTrue(out.toLowerCase().contains("нет домашнего задания"));
    }

    @Test
    public void realizationWithChatId_invalidIdFormat_returnsError() {
        long chatId = 1201L;
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(Collections.emptyList());

        String out = cmd.realizationWithChatId(chatId, new String[]{"/deletehw", "abc"});
        assertNotNull(out);
        assertTrue(out.contains("❌ Неверный ID"));
    }

    @Test
    public void realizationWithChatId_idNotFound_returnsNotFoundMessage() {
        long chatId = 1202L;
        HomeworkItem existing = new HomeworkItem(10, chatId, "Math", "D", LocalDate.now(), false, 1);
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(List.of(existing));

        String out = cmd.realizationWithChatId(chatId, new String[]{"/deletehw", "99"});
        assertNotNull(out);
        assertTrue(out.contains("❌ Задание с таким ID не найдено у вас"));
    }

    @Test
    public void realizationWithChatId_deleteExisting_callsStorageAndLink() {
        long chatId = 1203L;
        HomeworkItem existing = new HomeworkItem(55, chatId, "Math", "D", LocalDate.now(), false, 1);
        when(mockStorage.getHomeworkByUser(chatId)).thenReturn(List.of(existing));

        String out = cmd.realizationWithChatId(chatId, new String[]{"/deletehw", "55"});
        assertNotNull(out);
        assertTrue(out.contains("✅ Домашнее задание с ID 55 удалено.") || out.toLowerCase().contains("удалено"));

        // verify unlink and delete calls
        verify(mockLink, times(1)).unlinkHomework(55);
        verify(mockStorage, times(1)).deleteHomework(55);
    }
}
