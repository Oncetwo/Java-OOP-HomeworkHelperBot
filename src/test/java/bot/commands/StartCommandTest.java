package bot.commands;

import bot.user.User;
import bot.user.RegistrationState;
import bot.user.SQLiteUserStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.*;

class StartCommandTest {
    private SQLiteUserStorage userStorage;
    private StartCommand startCommand;
    private long chatId;

    @BeforeEach
    void setUp() {
        userStorage = new SQLiteUserStorage();
        userStorage.initialize();
        startCommand = new StartCommand(userStorage);
        // Очищаем пользователя перед каждым тестом
        if (userStorage.userExists(chatId)) {
            userStorage.deleteUser(chatId);
        }
    }

    @Test
    void testProcessStart_NewUser() {
        // пользователь отсутствует
        SendMessage message = startCommand.processStart(chatId);

        assertTrue(message.getText().contains("Добро пожаловать! Для начала работы с ботом необходимо зарегистрироваться.")
                && message.getText().contains("Пожалуйста, введите ваше имя"));
        // проверяем, что пользователь появился в базе
        User user = userStorage.getUser(chatId);
        assertNotNull(user);
        assertEquals(RegistrationState.ASK_NAME, user.getState());
    }

    @Test
    void testProcessStart_AskNameState() {
        // пользователь на этапе ввода имени
        User user = new User(chatId, null, null, RegistrationState.ASK_NAME);
        userStorage.saveUser(user);

        SendMessage message = startCommand.processStart(chatId);

        assertTrue(message.getText().contains("Пожалуйста, введите ваше имя"));
    }

    @Test
    void testProcessStart_AskGroupState() {
        // пользователь на этапе ввода группы
        User user = new User(chatId, "Иван", null, RegistrationState.ASK_GROUP);
        userStorage.saveUser(user);

        SendMessage message = startCommand.processStart(chatId);

        assertTrue(message.getText().contains("Пожалуйста, введите вашу группу"));
    }

    @Test
    void testProcessStart_RegisteredUser() {
        // создаём зарегистрированного пользователя
        User user = new User(chatId, "Иван", "МЕН-241001", RegistrationState.REGISTERED);
        userStorage.saveUser(user);

        SendMessage message = startCommand.processStart(chatId);

        assertTrue(message.getText().contains("Вы уже зарегистрированы!"));
        assertTrue(message.getText().contains("Имя: Иван"));
        assertTrue(message.getText().contains("Группа: МЕН-241001"));
    }

    @Test
    void testProcessStart_RegisteredUser_Keyboard() {
        // Зарегистрированный пользователь
        User user = new User(chatId, "Иван", "МЕН-241001", RegistrationState.REGISTERED);
        userStorage.saveUser(user);

        SendMessage message = startCommand.processStart(chatId);

        assertTrue(message.getText().contains("Вы уже зарегистрированы!"));
        // Проверяем, что в сообщении есть кастомная клавиатура
        assertNotNull(message.getReplyMarkup());

        assertTrue(message.getReplyMarkup() instanceof ReplyKeyboardMarkup);

        ReplyKeyboardMarkup replyKeyboard = (ReplyKeyboardMarkup) message.getReplyMarkup();
        var rows = replyKeyboard.getKeyboard(); //возвращает список строк клавиатуры
        assertFalse(rows.isEmpty());
        boolean hasYes = rows.stream()
                .flatMap(row -> row.stream()) //из всех строк получаем один общий поток всех кнопок
                .anyMatch(button -> "ДА".equals(button.getText())); // ищем среди всех кнопок хотя бы одну, у которой текст "ДА"
        boolean hasNo = rows.stream()
                .flatMap(row -> row.stream())
                .anyMatch(button -> "НЕТ".equals(button.getText()));
        assertTrue(hasYes && hasNo);
    }

    @Test
    void testProcessButtonResponse_yes() {
        User user = new User(chatId, "Иван", "МЕН-241001", RegistrationState.REGISTERED);
        user.setWaitingForButton(true);
        userStorage.saveUser(user);

        SendMessage message = startCommand.processButtonResponse(chatId, "ДА");

        User updatedUser = userStorage.getUser(chatId);
        assertEquals(RegistrationState.ASK_NAME, updatedUser.getState());
        assertFalse(updatedUser.getWaitingForButton());
        assertTrue(message.getText().contains("Начинаем обновление данных!"));
    }

    @Test
    void testProcessButtonResponse_no() {
        User user = new User(chatId, "Иван", "МЕН-241001", RegistrationState.REGISTERED);
        user.setWaitingForButton(true);
        userStorage.saveUser(user);

        SendMessage message = startCommand.processButtonResponse(chatId, "НЕТ");

        User updatedUser = userStorage.getUser(chatId);
        assertEquals(RegistrationState.REGISTERED, updatedUser.getState());
        assertFalse(updatedUser.getWaitingForButton());
        assertTrue(message.getText().contains("Отлично! Данные сохранены."));
    }

    @Test
    void testProcessButtonResponse_repeat() {
        // Пользователь не ждет ответа на кнопку
        User user = new User(chatId, "Иван", "МЕН-241001", RegistrationState.REGISTERED);
        user.setWaitingForButton(false); // важно!
        userStorage.saveUser(user);

        SendMessage message = startCommand.processButtonResponse(chatId, "ДА");

        assertTrue(message.getText().contains("Команда уже обработана"));
    }
}