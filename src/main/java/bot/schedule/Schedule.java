package bot.schedule;

import java.util.*;

public class Schedule {
    private String groupId; // айди группы
    private String groupName;
    private Map<String, List<Lesson>> weeklySchedule; // день недели -> список пар

    public Schedule(String groupId, String groupName) { // конструктор (делаем новый объект расписания с пустой мапой)
        this.groupId = groupId;
        this.groupName = groupName;
        this.weeklySchedule = new HashMap<>();
    }

    public void addLesson(String dayOfWeek, Lesson lesson) {
        if (!weeklySchedule.containsKey(dayOfWeek)) { // проверка, что списка еще нет
            List<Lesson> lessonsForDay = new ArrayList<>(); // создаем новый пустой список для этого дня
            weeklySchedule.put(dayOfWeek, lessonsForDay); // добавляем пару ключ значение
        }
        
        List<Lesson> lessonsForDay = weeklySchedule.get(dayOfWeek);
        
        lessonsForDay.add(lesson); // Добавляем новую пару в этот список
    }

    public List<Lesson> getLessonsForDay(String dayOfWeek) { // получение пар на конкретный день
        return weeklySchedule.getOrDefault(dayOfWeek, new ArrayList<>());
    }


    public String getGroupId() { 
    	return groupId; 
    	}
    
    public String getGroupName() { 
    	return groupName;
    	}
    
    public Map<String, List<Lesson>> getWeeklySchedule() { 
    	return weeklySchedule; 
    	}


    public void setGroupId(String groupId) { 
    	this.groupId = groupId; 
    	}
    
    public void setGroupName(String groupName) { 
    	this.groupName = groupName;
    	}
    
    public void setWeeklySchedule(Map<String, List<Lesson>> weeklySchedule) { 
    	this.weeklySchedule = weeklySchedule; 
    	}
    
    
    
    
    
}