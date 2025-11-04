package bot.user;
import bot.fsm.DialogState;

public class User {
    private Long chatId;
    private String name;
    private String group;
    private String university; 
    private String department; 
    private String course;
   
    private DialogState state; // Текущее состояние процесса регистрации
    private boolean waitingForButton;
    private boolean hasCustomSchedule; 
    
    
    public User(Long chatId) { // конструктор нового пользователя
        this.chatId = chatId;
        this.university = "";
        this.department = "";
        this.course = "";
        this.state = DialogState.ASK_NAME; // Новый пользователь начинает с этапа ввода имени
        this.waitingForButton = false; // по умолчанию false
        this.hasCustomSchedule = false; // флаг изменял ли расписание пользователь
        
    }
    // Конструктор для создания пользователя из базы данных
    public User(Long chatId, String name, String group, String university, String department, String course, DialogState state) { 
        this.chatId = chatId;
        this.name = name;
        this.group = group;
        this.university = university;
        this.department = department;
        this.course = course;
        this.state = state; // состояние регистрации
        this.waitingForButton = false; // по умолчанию false
        this.hasCustomSchedule = false;
    }

    
    public Long getChatId() {  // Id чата поменять не можем, можем только вернуть
        return chatId; 
    }
    
    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public String getGroup() { 
        return group; 
    }
    
    public void setGroup(String group) { 
        this.group = group; 
    }
    
    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }
    
    public DialogState getState() { 
        return state; 
    }
    
    public void setState(DialogState state) { 
        this.state = state; 
    }
    
    public boolean getWaitingForButton() {
        return waitingForButton;
    }
    
    public void setWaitingForButton(boolean waitingForButton) {
        this.waitingForButton = waitingForButton;
    }
    
    public boolean getHasCustomSchedule() { 
    	return hasCustomSchedule; 
    	}
    
    public void setHasCustomSchedule(boolean hasCustomSchedule) { 
        this.hasCustomSchedule = hasCustomSchedule; 
    }


    @Override
    public String toString() { // Строковое представление 
        return "User{" +
                "chatId=" + chatId +
                ", name='" + name + '\'' +
                ", group='" + group + '\'' +
                ", university='" + university + '\'' +   
                ", department='" + department + '\'' +   
                ", course='" + course + '\'' +          
                ", state=" + state +
                '}';
    }
}