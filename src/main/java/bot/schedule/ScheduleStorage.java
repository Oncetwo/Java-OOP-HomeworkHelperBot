package bot.schedule;

public interface ScheduleStorage {
    
    void initialize();
	
    Schedule getScheduleByGroupId(String groupId); // получить полное расписание
    
    Schedule getScheduleByGroupName(String groupName); // получить полное расписание по имени группы
    
    void saveSchedule(Schedule schedule); // сохранить новое расписание 
    
    void updateSchedule(Schedule schedule); // обновить расписание
    
    void deleteSchedule(String groupId); // удалить расписание по группе
    
    boolean scheduleExists(String groupId); // проверка есть ли расписание 
    
    void close();
    
    
    
    String getGroupIdByName(String groupName); // вернуть айди группы по имени группы
    
    void saveGroupMapping(String groupName, String groupId); // сохрвнить группу в таблицу
    
    boolean groupMappingExists(String groupName); // проверить есть ли группа в таблице 
     
    void updateMappingTimestamp(String groupName);
    
    void cleanupOldMappings(int daysOld);
}