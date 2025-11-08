package bot.homework;

import java.time.LocalDate;

// Класс описывает одно домашнее задание конкретного пользователя.

public class HomeworkItem {
    
    private long id;  // идентификатор записи в бд (чтобы нумеровать дз одного пользователя по одному предмету)
    private long chatId;
    private String subject; // название предмета
    private String description; // само дз
    private LocalDate dueDate; // дата, к которой нужно выполнить (по умолчанию следующая пара, но можно изменить)
    private boolean completed; // статус выполнения 
    private int remindBeforeDays; // за сколько дней до сдачи напоминать о дедлайне (по умолчанию день)


    public HomeworkItem(long id, long chatId, String subject, String description,
                        LocalDate dueDate, boolean completed, int remindBeforeDays) {
        this.id = id;
        this.chatId = chatId;
        this.subject = subject;
        this.description = description;
        this.dueDate = dueDate;
        this.completed = completed;
        this.remindBeforeDays = remindBeforeDays;
    }


    public long getId() {
    	return id; 
    	} 
    
    public long getChatId() { 
    	return chatId; 
    	} 
    
    public String getSubject() { 
    	return subject; 
    	} 
    
    public String getDescription() { 
    	return description;
    	} 
    
    public LocalDate getDueDate() { 
    	return dueDate; 
    	}
    public boolean isCompleted() { 
    	return completed;
    	} 
    public int getRemindBeforeDays() { 
    	return remindBeforeDays; 
    	} 

    public void setSubject(String subject) {
    	this.subject = subject;
    	} 
    
    public void setDescription(String description) { 
    	this.description = description; 
    	} 
    
    public void setDueDate(LocalDate dueDate) {
    	this.dueDate = dueDate;
    	} 
    public void setCompleted(boolean completed) {
    	this.completed = completed; 
    	}
    public void setRemindBeforeDays(int remindBeforeDays) { 
    	this.remindBeforeDays = remindBeforeDays;
    	} 


    @Override
    public String toString() {
        return String.format(
            "[%s] %s — %s (до %s)%s",
            subject, // название предмета
            description, // само задание
            completed ? "✅ Выполнено" : "⏳ Не выполнено", // статус выполнения
            dueDate, // дата дедлайна
            remindBeforeDays > 0 ? " 🔔 Напомнить за " + remindBeforeDays + " дн." : "" // если есть напоминание — вывести
        );
    }
}
