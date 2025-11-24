package bot.commands;

import bot.user.User;
import bot.user.UserStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ShareGroupCommandTest {

    private UserStorage mockUserStorage;
    private ShareGroupCommand cmd;

    @BeforeEach
    public void setup() {
        mockUserStorage = mock(UserStorage.class);
        cmd = new ShareGroupCommand(mockUserStorage, "TestBot", 1);
    }

    @Test
    public void start_notRegistered_returnsError() {
        long chatId = 200L;
        when(mockUserStorage.getUser(chatId)).thenReturn(null);
        String r = cmd.start(chatId);
        assertTrue(r.contains("❌ Вы не зарегистрированы. Введите /start чтобы зарегистрироваться."));
    }

    @Test
    public void start_noGroup_returnsError() {
        long chatId = 201L;
        User u = new User(chatId);
        u.setGroup(null);
        when(mockUserStorage.getUser(chatId)).thenReturn(u);
        String r = cmd.start(chatId);
        assertTrue(r.contains("❌ У вас не указана группа в профиле."));
    }

    @Test
    public void start_valid_returnsLinkAndStartParam() {
        long chatId = 202L;
        User u = new User(chatId);
        u.setGroup("МЕН-241001");
        when(mockUserStorage.getUser(chatId)).thenReturn(u);

        String r = cmd.start(chatId);
        assertTrue(r.contains("https://t.me/TestBot"));
        assertTrue(r.contains("✅ Ссылка-приглашение создана:"));
        assertTrue(r.contains("/start invite_"));
    }
}
