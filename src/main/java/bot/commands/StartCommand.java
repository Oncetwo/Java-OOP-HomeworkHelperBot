package bot.commands;

import bot.user.*;
import bot.fsm.DialogState;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class StartCommand implements Command {
    private final UserStorage userStorage; // объявляем ссылку на объект, который реализует интерфейс хранилища

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
                
                return createMessageWithButtons(chatId, userInfo, "ДА", "НЕТ"); // возвращаем сообщение с кнопками
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
            
         // проверяем, не обработали ли мы уже это сообщение
         //   if (!user.getWaitingForButton()) {
         //       return createMessage(chatId, "Команда уже обработана");
         //   }
           
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
                return createMessage(chatId, 
                    "Хорошо!\nТеперь введите название вашего института (например ИЕНИМ):");

            case ASK_UNIVERSITY:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Университет не может быть пустым. Введите название университета:");
                }
                user.setUniversity(messageText.trim());
                user.setState(DialogState.ASK_DEPARTMENT);
                userStorage.updateUser(user);
                return createMessage(chatId, 
                    "Введите название вашего департамента (например для ИЕНИМа - ШН/ШБ)");

            case ASK_DEPARTMENT:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Департамент не может быть пустым. Введите департамент:");
                }
                user.setDepartment(messageText.trim());
                user.setState(DialogState.ASK_COURSE);
                userStorage.updateUser(user);
                return createMessage(chatId, 
                    "Введите ваш курс (например, 1, 2, 3, 4, или 5):");

            case ASK_COURSE:
                if (messageText.trim().isEmpty()) {
                    return createMessage(chatId, "❌❌❌ Курс не может быть пустым. Введите курс:");
                }
                user.setCourse(messageText.trim());
                user.setState(DialogState.REGISTERED);
                userStorage.updateUser(user);
                return createMessage(chatId, 
                    "🎓 Регистрация завершена!\n\n" +
                    "Ваши данные:\n" +
                    "Имя: " + user.getName() + "\n" +
                    "Группа: " + user.getGroup() + "\n" +
                    "Университет: " + user.getUniversity() + "\n" +
                    "Департамент: " + user.getDepartment() + "\n" +
                    "Курс: " + user.getCourse() + "\n\n" +
                    "Теперь вы можете пользоваться всеми функциями бота!\n" +
                    "Введите /help для просмотра доступных команд");
                    
                default:
                    return createMessage(chatId, "❌❌❌ Неизвестное состояние. Введите /start");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return createMessage(chatId, "❌❌❌ Ошибка при обработке");
        }
    }

    
    // Создание сообщения с кнопками
    private SendMessage createMessageWithButtons(long chatId, String text, String buttonOne, String buttonTwo) {
        SendMessage message = new SendMessage();  // Создаем объект сообщения
        message.setChatId(String.valueOf(chatId));  // Устанавливаем ID чата
        message.setText(text);  // Устанавливаем текст сообщения

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();  // ReplyKeyboardMarkup - класс для создания кастомной клавиатуры (в tg api)
        keyboardMarkup.setResizeKeyboard(true);  // размер кнопок подстраивается под устройство
        keyboardMarkup.setOneTimeKeyboard(true);  // Скрываем клавиатуру после нажатия

        List<KeyboardRow> keyboard = new ArrayList<>();  // Создаем список для строк кнопок
        KeyboardRow row = new KeyboardRow();  // KeyboardRow - класс для представление 1 строки кнопок
        
        row.add(new KeyboardButton(buttonOne));  // Добавляем первую кнопку с текстом buttonOne
        row.add(new KeyboardButton(buttonTwo));  // Добавляем вторую кнопку с текстом buttonTwo
        
        keyboard.add(row);  // Добавляем строку с кнопками в клавиатуру
        keyboardMarkup.setKeyboard(keyboard);  // keyboardMarkup метод класса ReplyKeyboardMarkup - устанавливает структуру кнопок (их местоположение)
        message.setReplyMarkup(keyboardMarkup);  // Прикрепляем клавиатуру к сообщению

        return message;  // Возвращаем готовое сообщение с двумя кнопками
    }

    private SendMessage createMessage(long chatId, String text) { // создание сообщения
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId)); // переводим Id в число
        message.setText(text);
        return message;
    }


    public boolean isUserInRegistration(long chatId) { // проверка, находится ли пользователь в состояние регистрации
        User user = userStorage.getUser(chatId);
        if (user != null && user.getState() != DialogState.REGISTERED) {
            return true;
        } else {
            return false;
        }
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