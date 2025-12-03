package bot.commands;

import bot.fsm.DialogState;
import bot.user.User;
import bot.user.UserStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StartCommandTest {

    private UserStorage mockUserStorage;
    private StartCommand startCommand;
    private final long CHAT_ID = 12345L;

    @BeforeEach
    public void setup() {

        mockUserStorage = mock(UserStorage.class);
        startCommand = new StartCommand(mockUserStorage);
    }

    @Test
    public void testProcessStart_newUser_createsUserAndAsksName() {

        when(mockUserStorage.getUser(CHAT_ID)).thenReturn(null);
        // когда StartCommand спросит userStorage.getUser(CHAT_ID) — вернётся null (новый пользователь)

        SendMessage resp = startCommand.processStart(CHAT_ID);
        // Выполняем метод — получаем SendMessage (ответ бота)

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        // ArgumentCaptor позволяет "поймать" объект User, который был передан в saveUser

        verify(mockUserStorage, times(1)).saveUser(captor.capture());
        // проверяем, что saveUser вызван ровно 1 раз, и сохраняем переданный объект в captor

        User saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(CHAT_ID, saved.getChatId());
        // убеждаемся, что сохранённый пользователь не null и chatId совпадает с тестовым

        assertNotNull(resp);
        // ответ не должен быть null

        assertTrue(resp.getText().contains("введите ваше имя"));
        // ожидаем, что текст сообщения бот просит ввести имя — точная проверка допустима здесь
    }

    @Test
    public void testProcessStart_alreadyRegistered_showsProfileAndButtons() {

        User registered = new User(CHAT_ID, "Иван", "МЕН-241001", "ИнЭУ", "ШГУП", "2", DialogState.REGISTERED);
        // создаём объект User, который уже зарегистрирован (DialogState.REGISTERED)
        when(mockUserStorage.getUser(CHAT_ID)).thenReturn(registered);

        SendMessage resp = startCommand.processStart(CHAT_ID);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mockUserStorage, times(1)).updateUser(captor.capture());
        // проверяем, что updateUser вызывался (StartCommand должен пометить user как waitingForButton)

        User updated = captor.getValue();
        assertTrue(updated.getWaitingForButton());
        // проверяем, что флаг ожидания кнопки установлен

        assertNotNull(resp);
        assertTrue(resp.getText().contains("Вы уже зарегистрированы"));
        // проверяем, что текст содержит информирование о регистрации

        assertNotNull(resp.getReplyMarkup());
        assertTrue(resp.getReplyMarkup() instanceof ReplyKeyboardMarkup);
        // проверяем, что в ответе присутствует клавиатура (ReplyKeyboardMarkup)
    }

    @Test
    public void testProcessButtonResponse_yes_updatesStateToAskName() {

        User registered = new User(CHAT_ID, "Иван", "M", "U", "D", "1", DialogState.REGISTERED);
        registered.setWaitingForButton(true);
        // выставляем флаг ожидания кнопки, чтобы команда обрабатывала ввод кнопки
        when(mockUserStorage.getUser(CHAT_ID)).thenReturn(registered);

        SendMessage resp = startCommand.processButtonResponse(CHAT_ID, "ДА");
        // вызываем обработчик кнопки с текстом "ДА"

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mockUserStorage, times(1)).updateUser(captor.capture());
        // проверяем, что updateUser вызван — состояние пользователя должно измениться

        User updated = captor.getValue();
        assertEquals(DialogState.ASK_NAME, updated.getState());
        // убеждаемся, что состояние действительно ASK_NAME

        assertNotNull(resp);
        assertTrue(resp.getText().contains("Пожалуйста, введите ваше новое имя"));
        // проверяем текст ответа — бот попросил ввести новое имя
    }

    @Test
    public void testProcessButtonResponse_no_keepsDataAndInformsUser() {

        User registered = new User(CHAT_ID, "Иван", "M", "U", "D", "1", DialogState.REGISTERED);
        registered.setWaitingForButton(true);
        when(mockUserStorage.getUser(CHAT_ID)).thenReturn(registered);

        SendMessage resp = startCommand.processButtonResponse(CHAT_ID, "НЕТ");

        verify(mockUserStorage, times(1)).updateUser(any(User.class));
        // updateUser должен был вызваться (возможно, чтобы обнулить флаг waitingForButton)
        assertNotNull(resp);
        assertTrue(resp.getText().contains("Отлично! Данные сохранены"));
        // проверяем, что бот подтвердил сохранение данных
    }

    @Test
    public void testFullRegistrationFlow_name_group_university_department_course() {
        // Тест имитирует полный поток регистрации, меняя состояние User и поведение мок-репозитория.
        long chatId = 2222L;

        // 1) Сначала пользователя нет — процесс старта должен создать его и попросить имя
        when(mockUserStorage.getUser(chatId)).thenReturn(null);
        SendMessage startResp = startCommand.processStart(chatId);
        verify(mockUserStorage, times(1)).saveUser(any(User.class));
        assertTrue(startResp.getText().contains("введите ваше имя"));

        // 2) Пользователь в состоянии ASK_NAME — вводим имя и проверяем переход в ASK_GROUP
        User u1 = new User(chatId);
        u1.setState(DialogState.ASK_NAME);
        when(mockUserStorage.getUser(chatId)).thenReturn(u1);
        SendMessage respAfterName = startCommand.processRegistration(chatId, "Пётр");

        // Проверяем, что updateUser вызван хотя бы раз; захватываем последний обновлённый User
        ArgumentCaptor<User> cap1 = ArgumentCaptor.forClass(User.class);
        verify(mockUserStorage, atLeastOnce()).updateUser(cap1.capture());
        User afterName = cap1.getValue();

        assertEquals(DialogState.ASK_GROUP, afterName.getState());
        // убеждаемся, что состояние стало ASK_GROUP

        // --- Вариант A: гибкая проверка ответа: либо текст содержит "групп", либо есть клавиатура ---
        assertNotNull(respAfterName);
        String text = respAfterName.getText();
        boolean textContainsGroup = text != null && text.toLowerCase().contains("групп");
        boolean hasKeyboard = respAfterName.getReplyMarkup() != null;
        assertTrue(textContainsGroup || hasKeyboard);


        // 3) Дальше — симуляция ввода группы: бот предлагает выбор университета (клавиатура)
        User u2 = new User(chatId);
        u2.setState(DialogState.ASK_GROUP);
        when(mockUserStorage.getUser(chatId)).thenReturn(u2);
        SendMessage respAfterGroup = startCommand.processRegistration(chatId, "МЕН-241001");
        assertNotNull(respAfterGroup.getReplyMarkup()); // ожидаем кнопки институтов

        // 4) Ввод университета (в данном тесте принимаем, что "ИнЭУ" присутствует в конфигурации)
        User u3 = new User(chatId);
        u3.setState(DialogState.ASK_UNIVERSITY);
        when(mockUserStorage.getUser(chatId)).thenReturn(u3);
        SendMessage respAfterUniv = startCommand.processRegistration(chatId, "ИнЭУ");
        assertNotNull(respAfterUniv.getReplyMarkup()); // ожидаем кнопки департаментов

        // 5) Выбор департамента -> должен быть переход к выбору курса
        User u4 = new User(chatId);
        u4.setState(DialogState.ASK_DEPARTMENT);
        when(mockUserStorage.getUser(chatId)).thenReturn(u4);
        SendMessage respAfterDept = startCommand.processRegistration(chatId, "ШГУП");
        assertNotNull(respAfterDept.getReplyMarkup()); // ожидаем кнопки курсов

        // 6) Ввод курса -> регистрация завершена
        User u5 = new User(chatId);
        u5.setState(DialogState.ASK_COURSE);
        u5.setName("Пётр");
        u5.setGroup("МЕН-241001");
        u5.setUniversity("ИнЭУ");
        u5.setDepartment("ШГУП");
        when(mockUserStorage.getUser(chatId)).thenReturn(u5);
        SendMessage finalResp = startCommand.processRegistration(chatId, "2");

        verify(mockUserStorage, atLeastOnce()).updateUser(any(User.class));
        assertNotNull(finalResp);
        assertTrue(finalResp.getText().contains("Регистрация завершена") || finalResp.getText().contains("Вы успешно зарегистрировались"));
        // убеждаемся, что бот сообщает об успешном завершении регистрации
    }

    @Test
    public void testValidation_emptyName_returnsErrorMessage() {
        long chatId = 3333L;
        User user = new User(chatId);
        user.setState(DialogState.ASK_NAME);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        SendMessage resp = startCommand.processRegistration(chatId, "   ");
        assertNotNull(resp);
        assertTrue(resp.getText().contains("Имя не может быть пустым"));
    }

    @Test
    public void testValidation_emptyGroup_returnsErrorMessage() {
        long chatId = 4444L;
        User user = new User(chatId);
        user.setState(DialogState.ASK_GROUP);
        when(mockUserStorage.getUser(chatId)).thenReturn(user);

        SendMessage resp = startCommand.processRegistration(chatId, "");
        assertNotNull(resp);
        assertTrue(resp.getText().contains("Группа не может быть пустой"));
    }
}