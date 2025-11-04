package bot.fsm;

public enum DialogState {

    ASK_NAME, // ждём имя
    ASK_GROUP, // ждём группу
    ASK_UNIVERSITY, // ждём университет
    ASK_DEPARTMENT, // ждём факультет
    ASK_COURSE, // ждём курс
    WAITING_BUTTON, // ждём нажатия кнопки
    REGISTERED,   // регистрация завершена


}
