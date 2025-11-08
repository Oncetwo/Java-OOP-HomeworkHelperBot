package bot.homework;

import java.time.LocalDate;
import java.util.List;


public interface HomeworkStorage {

    void initialize();
	
    void addHomework(long chatId, String subject, String description, LocalDate dueDate); // Добавить новое домашнее задание 

    List<HomeworkItem> getHomeworkByUser(long chatId); // Получить все домашние задания пользователя

    List<HomeworkItem> getHomeworkBySubject(long chatId, String subject); // Получить все домашние задания по конкретному предмету 

    List<HomeworkItem> getHomeworkForDate(LocalDate date);  // Получить домашние задания, которые нужно выполнить в указанную дату 

    List<HomeworkItem> getHomeworkForReminder(LocalDate date); // Получить домашние задания, для которых нужно выслать напоминание

    void updateHomework(long id, String newSubject, String newDescription, LocalDate newDueDate); // Обновить существующее задание (описание, дату и предмет) 

    void markAsCompleted(long id, boolean completed); // Отметить задание как выполненное или невыполненное 

    void deleteHomework(long id);  // Удалить конкретное задание по ID 
 
    void deleteOldHomework(LocalDate date); // Удалить все старые задания (с прошедшей датой выполнения)

    void deleteAllHomeworkForUser(long chatId);  // Удалить все задания пользователя 
    
    List<HomeworkItem> getActiveHomeworkBySubjects(long chatId, List<String> subjects); // для вечерней рассылки (собирает дз по расписанию на следующий день)

}
