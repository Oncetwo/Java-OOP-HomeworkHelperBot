package bot.schedule;

import java.time.LocalTime;

public class Lesson {
    private String subject; // предмет
    private LocalTime startTime; // время начала
    private LocalTime endTime; // время окончания
    private String classroom; // аудитория

    public Lesson(String subject, LocalTime startTime, LocalTime endTime, String classroom) { // конструктор
        this.subject = subject;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroom = classroom;
    }

    public String getSubject() { 
    	return subject; 
    	}
    
    public LocalTime getStartTime() { 
    	return startTime; 
    	}
    
    public LocalTime getEndTime() {
    	return endTime;
    	}
    
    public String getClassroom() {
    	return classroom; 
    	}


    public void setSubject(String subject) { 
    	this.subject = subject; 
    	}
   
    public void setStartTime(LocalTime startTime) { 
    	this.startTime = startTime; 
    	}
    
    public void setEndTime(LocalTime endTime) { 
    	this.endTime = endTime; 
    	}
    
    public void setClassroom(String classroom) { 
    	this.classroom = classroom; 
    	}
}





