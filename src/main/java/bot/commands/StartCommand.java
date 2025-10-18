package bot.commands;

import bot.user.User;
import bot.user.RegistrationState;
import bot.user.UserStorageInterface;

/**
 * Команда /start - обрабатывает процесс регистрации пользователя.
 * Реализует диалоговую ветку: запрос имени → запрос группы → завершение регистрации.
 */
public class StartCommand implements CommandInterface {
    
    // Хранилище пользователей для сохранения данных
    private UserStorageInterface userStorage;
    
    /**
     * Конструктор команды.
     * @param userStorage хранилище пользователей для работы с данными
     */
    public StartCommand(UserStorageInterface userStorage) {
        this.userStorage = userStorage;
    }
    
    // ==================== РЕАЛИЗАЦИЯ ИНТЕРФЕЙСА CommandInterface ====================

    /**
     * Возвращает имя команды.
     * @return "/start"
     */
    @Override
    public String getName() {
        return "/start";
    }
    
    /**
     * Возвращает описание команды для справки.
     * @return описание команды
     */
    @Override
    public String getInformation() {
        return "— начать регистрацию в боте";
    }
    
    /**
     * Основной метод выполнения команды.
     * ВАЖНО: Для команды /start этот метод не используется напрямую,
     * так как регистрация требует отдельной логики с отслеживанием состояния.
     */
    @Override
    public String realization(String[] args) {
        return "Для регистрации используйте /start в основном чате с ботом";
    }
    
    // ==================== ОСНОВНАЯ ЛОГИКА РЕГИСТРАЦИИ ====================

    /**
     * Обрабатывает процесс регистрации пользователя.
     * Этот метод вызывается из основного класса бота.
     * @param chatId уникальный ID чата пользователя
     * @param messageText текст сообщения от пользователя
     * @return ответ бота пользователю
     */
    public String handleRegistration(long chatId, String messageText) {
        // Получаем пользователя из хранилища (или null, если пользователь новый)
        User user = userStorage.getUser(chatId);
        
        // Если пользователь новый или не начал регистрацию
        if (user == null) {
            return startNewRegistration(chatId);
        }
        
        // Обрабатываем сообщение в зависимости от текущего состояния пользователя
        switch (user.getState()) {
            case ASK_NAME:
                return processNameInput(user, messageText);
                
            case ASK_GROUP:
                return processGroupInput(user, messageText);
                
            case REGISTERED:
                return "Вы уже зарегистрированы! ✅\n\n" +
                       "Ваши данные:\n" +
                       "• Имя: " + user.getName() + "\n" +
                       "• Группа: " + user.getGroup() + "\n\n" +
                       "Используйте /help для просмотра доступных команд.";
                
            default:
                // Если состояние неизвестно, начинаем регистрацию заново
                return startNewRegistration(chatId);
        }
    }
    
    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ РЕГИСТРАЦИИ ====================

    /**
     * Начинает процесс регистрации для нового пользователя.
     * @param chatId ID чата нового пользователя
     * @return приветственное сообщение
     */
    private String startNewRegistration(long chatId) {
        // Создаем нового пользователя с начальным состоянием ASK_NAME
        User newUser = new User(chatId);
        userStorage.saveUser(newUser);
        
        return "👋 Привет! Я бот для уведомлений о домашних заданиях.\n\n" +
               "Давай познакомимся! Как тебя зовут?";
    }
    
    /**
     * Обрабатывает ввод имени пользователя.
     * @param user объект пользователя
     * @param name введенное имя
     * @return ответ с запросом группы
     */
    private String processNameInput(User user, String name) {
        // Проверяем, что имя не пустое
        if (name == null || name.trim().isEmpty()) {
            return "Пожалуйста, введите своё имя:";
        }
        
        // Сохраняем имя и переходим к следующему этапу
        user.setName(name.trim());
        user.setState(RegistrationState.ASK_GROUP);
        userStorage.saveUser(user);
        
        return "Приятно познакомиться, " + user.getName() + "! ✨\n\n" +
               "Теперь введи свою учебную группу:\n" +
               "(например, МЕН-241001)";
    }
    
    /**
     * Обрабатывает ввод учебной группы.
     * @param user объект пользователя
     * @param group введенная группа
     * @return сообщение о завершении регистрации
     */
    private String processGroupInput(User user, String group) {
        // Проверяем, что группа не пустая
        if (group == null || group.trim().isEmpty()) {
            return "Пожалуйста, введите свою группу:";
        }
        
        // Сохраняем группу (приводим к верхнему регистру) и завершаем регистрацию
        user.setGroup(group.trim().toUpperCase());
        user.setState(RegistrationState.REGISTERED);
        userStorage.saveUser(user);
        
        return "🎉 Регистрация завершена!\n\n" +
               "Твои данные:\n" +
               "• Имя: " + user.getName() + "\n" +
               "• Группа: " + user.getGroup() + "\n\n" +
               "Теперь ты можешь использовать все возможности бота!\n" +
               "Напиши /help для просмотра доступных команд.";
    }
    
    // ==================== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Проверяет, завершил ли пользователь регистрацию.
     * @param chatId ID чата пользователя
     * @return true если пользователь зарегистрирован, false если нет
     */
    public boolean isUserRegistered(long chatId) {
        User user = userStorage.getUser(chatId);
        return user != null && user.getState() == RegistrationState.REGISTERED;
    }
    
    /**
     * Получает информацию о пользователе для отладки.
     * @param chatId ID чата пользователя
     * @return строка с информацией о пользователе
     */
    public String getUserInfo(long chatId) {
        User user = userStorage.getUser(chatId);
        if (user == null) {
            return "Пользователь не найден";
        }
        return "Имя: " + user.getName() + "\nГруппа: " + user.getGroup() + 
               "\nСостояние: " + user.getState();
    }
}