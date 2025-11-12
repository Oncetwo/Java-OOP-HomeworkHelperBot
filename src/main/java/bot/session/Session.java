package bot.session;

public class Session {
    private String day;
    private Integer lessonIndex;
    private String subject;
    private String room;
    private String timeBegin;
    private String timeEnd;

    // геттеры и сеттеры
    public String getDay() { 
    	return day;
    	}
    
    public void setDay(String day) { 
    	this.day = day;
    	}

    public Integer getLessonIndex() {
    	return lessonIndex;
    	}
    
    public void setLessonIndex(Integer lessonIndex) { 
    	this.lessonIndex = lessonIndex;
    	}

    public String getSubject() {
    	return subject;
    	}
    
    public void setSubject(String subject) { 
    	this.subject = subject;
    	}

    public String getRoom() {
    	return room;
    	}
    
    public void setRoom(String room) {
    	this.room = room;
    	}

    public String getTimeBegin() { 
    	return timeBegin;
    	}
    
    public void setTimeBegin(String timeBegin) { 
    	this.timeBegin = timeBegin;
    	}

    public String getTimeEnd() { 
    	return timeEnd;
    	}
    
    public void setTimeEnd(String timeEnd) { 
    	this.timeEnd = timeEnd;
    	}
    
}