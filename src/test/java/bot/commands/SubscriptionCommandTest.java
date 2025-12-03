package bot.commands;

import bot.user.User;
import bot.user.UserStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class SubscriptionCommandTest {

    private UserStorage mockUserStorage;
    private SubscriptionCommand cmd;

    @BeforeEach
    public void setup() {
        mockUserStorage = mock(UserStorage.class);
        cmd = new SubscriptionCommand(mockUserStorage);
    }

    @Test
    public void realizationWithChatId_userNotRegistered_returnsError() {
        long chatId = 2000L;
        when(mockUserStorage.getUser(chatId)).thenReturn(null);

        String out = cmd.realizationWithChatId(chatId, new String[]{"/subscription", "status"});
        assertNotNull(out);
        assertTrue(out.contains("❌ Вы не зарегистрированы"));
    }

    @Test
    public void realizationWithChatId_noArgs_returnsUsage() {
        long chatId = 2001L;
        User user = new User(chatId);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        String out = cmd.realizationWithChatId(chatId, new String[]{"/subscription"});
        assertNotNull(out);
        assertTrue(out.contains("/subscription on"));
    }

    @Test
    public void realizationWithChatId_on_enablesSubscription() {
        long chatId = 2002L;
        User user = new User(chatId);
        user.setSubscriptionEnabled(false);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        String out = cmd.realizationWithChatId(chatId, new String[]{"/subscription", "on"});
        assertNotNull(out);
        assertTrue(out.contains("✅ Ежедневные уведомления включены"));
        verify(mockUserStorage, times(1)).updateUser(any(User.class));
    }

    @Test
    public void realizationWithChatId_off_disablesSubscription() {
        long chatId = 2003L;
        User user = new User(chatId);
        user.setSubscriptionEnabled(true);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        String out = cmd.realizationWithChatId(chatId, new String[]{"/subscription", "off"});
        assertNotNull(out);
        assertTrue(out.contains("🔕 Ежедневные уведомления выключены"));
        verify(mockUserStorage, times(1)).updateUser(any(User.class));
    }

    @Test
    public void realizationWithChatId_status_reportsCurrent() {
        long chatId = 2004L;
        User user = new User(chatId);
        user.setSubscriptionEnabled(true);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        String out = cmd.realizationWithChatId(chatId, new String[]{"/subscription", "status"});
        assertNotNull(out);
        assertTrue(out.contains("Статус подписки: ВКЛЮЧЕНА") || out.contains("ВКЛЮЧЕНА"));
    }
}
