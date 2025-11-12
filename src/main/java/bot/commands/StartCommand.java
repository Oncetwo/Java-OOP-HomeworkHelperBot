package bot.commands;

import bot.schedule.*;
import bot.user.*;
import bot.fsm.DialogState;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*; //чтобы использовать Map, List 

public class StartCommand implements Command {
    private final UserStorage userStorage; // объявляем ссылку на объект, который реализует интерфейс хранилища

    private static final Map<String, List<String>> INSTITUTE_DEPARTMENTS = new HashMap<>();

    static {
        INSTITUTE_DEPARTMENTS.put("ИЕНиМ", List.of("ШН", "ШБ"));
        INSTITUTE_DEPARTMENTS.put("ИнЭУ", List.of("ШГУП", "ШУМИ", "ШЭМ (департамент)"));
        INSTITUTE_DEPARTMENTS.put("ИФКСиМП", List.of());
        INSTITUTE_DEPARTMENTS.put("УГИ", List.of("И", "Ф", "Ж", "ИКиД", "ПиС", "П", "МО", "Л", "ШАиПР"));
        INSTITUTE_DEPARTMENTS.put("ИРИТ-РтФ", List.of("ШБ", "ШПиАО"));
        INSTITUTE_DEPARTMENTS.put("ИНМТ", List.of("СМ", "МиМ", "М", "НМиТ (институт)"));
        INSTITUTE_DEPARTMENTS.put("УралЭНИН", List.of());
        INSTITUTE_DEPARTMENTS.put("ФТИ", List.of("-"));
        INSTITUTE_DEPARTMENTS.put("ИСА", List.of("-"));
        INSTITUTE_DEPARTMENTS.put("ХТИ", List.of("-"));
        INSTITUTE_DEPARTMENTS.put("ИТОО", List.of("-"));
        INSTITUTE_DEPARTMENTS.put("ПОдИУ", List.of("-"));
        INSTITUTE_DEPARTMENTS.put("БПУР", List.of("-"));
        INSTITUTE_DEPARTMENTS.put("УПИШ", List.of("-"));
    }

    public StartCommand(UserStorage userStorage) { // конструктор класса 
        this.userStorage = userStorage;
    }

    
    @Override
    public String getName() {
        return "/start";
    }

    
    @Override
    public String getInformation() {
        return "Начать регистрацию в системе";
    }

    
    @Override
    public String realization(String[] args) {
        return "Для регистрации введите /start в чате с ботом";
    }

   
    public SendMessage processStart(long chatId) { // метод обработки команд
        try {
            User user = userStorage.getUser(chatId); // пытаемся получить пользователя из бд
            
            if (user == null) {
                user = new User(chatId);
                userStorage.saveUser(user);
                return createMessage(chatId, 
                    "Добро пожаловать! Для начала работы с ботом необходимо зарегистрироваться.\n\n" +
                    "Пожалуйста, введите ваше имя:");
            }
            
            if (user.getState() ==  DialogState.REGISTERED) { // если пользователь уже существует и он зарегистрирован
                String userInfo = "🎓 Вы уже зарегистрированы!\n\n" +
                                 "Ваши данные:\n" +
                                 "Имя: " + user.getName() + "\n" +
                                 "Группа: " + user.getGroup() + "\n" +
                                 "Институт: " + user.getUniversity() + "\n" +
                                 "Департамент: " + user.getDepartment() + "\n" +
                                 "Курс: " + user.getCourse() + "\n\n" +
                                 "Хотите изменить данные профиля?";
                
                user.setWaitingForButton(true);
                userStorage.updateUser(user);
                
                return createMessageWithDynamicButtons(chatId, userInfo, List.of("ДА", "НЕТ")); // возвращаем сообщение с кнопками
            } else { // пользователь в процессе регистрации
                return continueRegistration(chatId, user);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return createMessage(chatId, "❌❌❌ Ошибка при обработке команды");
        }
    }

 
    public SendMessage processButtonResponse(long chatId, String messageText) { // обрабатывает ответы на кнопки да нет
        try {
            User user = userStorage.getUser(chatId);
            // DEBUG
            System.out.println("StartCommand.processButtonResponse: chatId=" + chatId + " message='" + messageText + "' state=" + (user == null ? "null" : user.getState()) + " waiting=" + (user == null ? "?" : user.getWaitingForButton()));

            user.setWaitingForButton(false); // сбрасываем флаг после обработки
            
            if (messageText.equalsIgnoreCase("ДА")) {
                user.setState(DialogState.ASK_NAME);
                userStorage.updateUser(user); // обновили состояние в хранилище
                return createMessage(chatId, 
                    "Начинаем обновление данных!\n\n" +
                    "Пожалуйста, введите ваше новое имя:");
            } else if (messageText.equalsIgnoreCase("НЕТ")) {
                userStorage.updateUser(user); 
                return createMessage(chatId, 
                    "Отлично! Данные сохранены.\n\n" +
                    "Вы можете продолжить использование бота.\n" +
                    "Введите /help для просмотра команд.");
            } 
            
            return processRegistration(chatId, messageText);
            
        } catch (Exception e) {
            e.printStackTrace();
            return createMessage(chatId, "❌ Ошибка при обработке");
        }
    }

    
    private SendMessage continueRegistration(long chatId, User user) {  // Метод для продолжения регистрации
        switch (user.getState()) {
            case ASK_NAME:
                return createMessage(chatId, "Пожалуйста, введите ваше имя:");
            case ASK_GROUP:
                return createMessage(chatId, 
                    "Пожалуйста, введите вашу группу (например, МЕН-241001):");
            default:
                return createMessage(chatId, "❌❌❌ Неизвестное состояние. Введите /start");
        }
    }


    public SendMessage processRegistration(long chatId, String messageText) { // Метод для обработки обычных сообщений в процессе регистрации
        try {
            User user = userStorage.getUser(chatId); // возвращаем пользователя
            
            switch (user.getState()) {
            case ASK_NAME:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Имя не может быть пустым. Пожалуйста, введите ваше имя:");
                }
                user.setName(messageText.trim()); 
                user.setState(DialogState.ASK_GROUP); 
                userStorage.updateUser(user); 
                return createMessage(chatId, 
                    "Отлично, " + messageText.trim() + "!\n\n" +
                    "Теперь введите вашу группу (например, МЕН-241001):");
                    
            case ASK_GROUP:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Группа не может быть пустой. Пожалуйста, введите вашу группу:");
                }
                user.setGroup(messageText.trim());
                user.setState(DialogState.ASK_UNIVERSITY);
                userStorage.updateUser(user);
                return createMessageWithDynamicButtons(chatId, // сообщение с кнопками 
                    "Выберите ваш институт из списка или введите вручную:",
                    new ArrayList<>(INSTITUTE_DEPARTMENTS.keySet())); // keyset возвращает набор ключей (все названия институтов)

            case ASK_UNIVERSITY:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Университет не может быть пустым. Введите название университета:");
                } 

                String universityInput = messageText.trim();
                user.setUniversity(universityInput);
                user.setState(DialogState.ASK_DEPARTMENT);
                userStorage.updateUser(user);

                if (INSTITUTE_DEPARTMENTS.containsKey(universityInput)) { // выводим только департаменты, относящиеся к институту
                    List<String> deps = INSTITUTE_DEPARTMENTS.get(universityInput);
                    if (!deps.isEmpty()) {
                        return createMessageWithDynamicButtons(chatId,
                            "Выберите ваш департамент из списка или введите вручную:",
                            deps);
                    }
                }

                return createMessage(chatId, 
                    "Введите название вашего департамента:");

            case ASK_DEPARTMENT:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Департамент не может быть пустым. Введите департамент:");
                }
                
                user.setDepartment(messageText.trim());
                user.setState(DialogState.ASK_COURSE);
                userStorage.updateUser(user);
                
                List<String> courses = List.of("1", "2", "3", "4", "5", "6");
                
                return createMessageWithDynamicButtons(chatId, "Выберите ваш курс из списка или введите вручную:", courses);

            case ASK_COURSE:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Курс не может быть пустым. Введите курс:");
                }
                user.setCourse(messageText.trim());
                user.setState(DialogState.REGISTERED);
                userStorage.updateUser(user);

                // Попробуем получить расписание и сохранить в локальную БД
                try {
                    ScheduleFetcher fetcher = new ScheduleFetcher();
                    Schedule schedule = fetcher.fetchForUser(user);
                    if (schedule != null) {
                        ScheduleManager sm = new ScheduleManager(userStorage);
                        sm.saveCommonSchedule(schedule);
                        sm.close();
                        return createMessage(chatId,
                            "🎓 Регистрация завершена!\n\n" +
                            "Ваши данные:\n" +
                            "Имя: " + user.getName() + "\n" +
                            "Группа: " + user.getGroup() + "\n" +
                            "Университет: " + user.getUniversity() + "\n" +
                            "Департамент: " + user.getDepartment() + "\n" +
                            "Курс: " + user.getCourse() + "\n\n" +
                            "Расписание успешно загружено!\n" +
                            "Введите /help для просмотра доступных команд.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return createMessage(chatId, 
                    "🎓 Регистрация завершена!\n\n" +
                    "Ваши данные:\n" +
                    "Имя: " + user.getName() + "\n" +
                    "Группа: " + user.getGroup() + "\n" +
                    "Департамент: " + user.getDepartment() + "\n" +
                    "Курс: " + user.getCourse() + "\n\n" +
                    "Вы успешно зарегистрировались, но расписание не найдено.\n" +
                    "Введите /help для просмотра доступных команд.");
                    
            default:
                return createMessage(chatId, "❌❌❌ Неизвестное состояние. Введите /start");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return createMessage(chatId, "❌❌❌ Ошибка при обработке");
        }
    }


    // метод для динамических кнопок (например, институты или департаменты)
    private SendMessage createMessageWithDynamicButtons(long chatId, String text, List<String> options) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup(); // ReplyKeyboardMarkup - класс для создания кастомной клавиатуры (в tg api)
        keyboardMarkup.setResizeKeyboard(true); // размер кнопок подстраивается под устройство
        keyboardMarkup.setOneTimeKeyboard(true); // Скрываем клавиатуру после нажатия

        List<KeyboardRow> keyboard = new ArrayList<>(); // Создаем список для строк кнопок

        KeyboardRow currentRow = new KeyboardRow(); // KeyboardRow - класс для представление 1 строки кнопок
        for (int i = 0; i < options.size(); i++) { // проходимся по всем элементам, которые должны быть кнопками
            currentRow.add(new KeyboardButton(options.get(i))); // для каждого элемента создается кнопка и добавляется в строку

            if ((i + 1) % 2 == 0 || i == options.size() - 1) { // первое это условие, что каждые 2 кнопки новая строка, а второе это если осталась 1 кнопка
                keyboard.add(currentRow); // добавляем строку в клаивиатуру
                currentRow = new KeyboardRow(); // делаем новую пустую строку
            }
        }

        keyboardMarkup.setKeyboard(keyboard); // keyboardMarkup метод класса ReplyKeyboardMarkup - устанавливает структуру кнопок (их местоположение)
        message.setReplyMarkup(keyboardMarkup);
        return message;
    }

    private SendMessage createMessage(long chatId, String text) { // создание сообщения
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // переводим Id в число
        message.setText(text);
        return message;
    }


    public boolean isUserInRegistration(long chatId) { // проверка, находится ли пользователь в состояние регистрации
        User user = userStorage.getUser(chatId);
        if (user == null) return false;
        DialogState s = user.getState();
        // Явно перечисляем только регистрационные состояния:
        return s == DialogState.ASK_NAME
            || s == DialogState.ASK_GROUP
            || s == DialogState.ASK_UNIVERSITY
            || s == DialogState.ASK_DEPARTMENT
            || s == DialogState.ASK_COURSE
            || s == DialogState.WAITING_BUTTON;
    }


    public boolean isWaitingForButtonResponse(long chatId) { // Проверяет, ожидает ли бот ответ на кнопки
        User user = userStorage.getUser(chatId);  
        if (user != null &&  user.getWaitingForButton()) {
            return true;
        } else {
            return false;
        }
    }
}