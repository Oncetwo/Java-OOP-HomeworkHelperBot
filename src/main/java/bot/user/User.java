package bot.user;

public class User {
    private Long chatId;
    private String name;
    private String group;
    private RegistrationState state; // Текущее состояние процесса регистрации
    private boolean waitingForButton;

    
    public User(Long chatId) { // конструктор нового пользователя
        this.chatId = chatId;
        this.state = RegistrationState.ASK_NAME; // Новый пользователь начинает с этапа ввода имени
        this.waitingForButton = false; // по умолчанию false
    }

    public User(Long chatId, String name, String group, RegistrationState state) { //  Конструктор для создания пользователя из базы данных
        this.chatId = chatId;
        this.name = name;
        this.group = group;
        this.state = state; // состояние регистрации
        this.waitingForButton = false; // по умолчанию false
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
    
    public RegistrationState getState() { 
        return state; 
    }
    
    public void setState(RegistrationState state) { 
        this.state = state; 
    }
    
    public boolean getWaitingForButton() {
        return waitingForButton;
    }
    
    public void setWaitingForButton(boolean waitingForButton) {
        this.waitingForButton = waitingForButton;
    }


    @Override
    public String toString() { // Строковое представление пользователя для отладки.
        return "User{" +
                "chatId=" + chatId +
                ", name='" + name + '\'' +
                ", group='" + group + '\'' +
                ", state=" + state +
                '}';
    }
}