package bot.fsm;

public enum DialogState {

    ASK_NAME, // ждём имя
    ASK_GROUP, // ждём группу
    ASK_UNIVERSITY, // ждём университет
    ASK_DEPARTMENT, // ждём факультет
    ASK_COURSE, // ждём курс
    WAITING_BUTTON, // ждём нажатия кнопки
    REGISTERED,   // регистрация завершена
    
    EDIT_DAY,// выбрали день
    EDIT_CHOOSE_ACTION, // ожидаем ответ на кнопки удалить/добавить
    ASK_LESSON_INDEX, // ждем номер пары для удаления
    ASK_SUBJECT, // ждем название предмета
    ASK_ROOM, // узнаем номер аудитории
    ASK_TIME_BEGIN, // узнаем время начала
    ASK_TIME_END // узнаем время конца

}
