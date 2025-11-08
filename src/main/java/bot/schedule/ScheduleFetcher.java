package bot.schedule;

import bot.user.exception.ScheduleFetchException;
import bot.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate; // для вычисления даты
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class ScheduleFetcher {

    private static final String DIVISIONS_URL = "https://urfu.ru/api/v2/schedule/divisions"; // адрес первого запроса
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE; // для форматирования и парсинга дат

    private final UrfuApiClient httpClient;
    private final ObjectMapper jsonMapper; // Jackson-объект для чтения JSON

    public ScheduleFetcher() { // конструтор 
        this.httpClient = new UrfuApiClient(); // клиент для http запросов
        this.jsonMapper = new ObjectMapper(); // парсер json
    }

    
    
    public Schedule fetchForUser(User user) throws ScheduleFetchException { // Загружает расписание для пользователя.
        try {
            String instituteName = nullSafeTrim(user.getUniversity());
            String departmentName = nullSafeTrim(user.getDepartment());
            String groupName = nullSafeTrim(user.getGroup());
            int courseNumber = parseCourse(user.getCourse());
            
            // 1) делаем массив json объектов
            String divisionsJson = httpClient.get(DIVISIONS_URL); // возвращает строку - тело ответа на http запрос
            JsonNode divisionsArray = jsonMapper.readTree(divisionsJson); // джексон парсит в json объекты подразделений

            // 2) Находим департамент
            long departmentId = findDepartmentId(divisionsArray, instituteName, departmentName);
            if (departmentId == -1L) {
                long instituteId = findInstituteId(divisionsArray, instituteName);
                if (instituteId != -1L) {
                    // если департамента нет, используем сам институт
                    departmentId = instituteId;
                } else {
                    throw new ScheduleFetchException("Не удалось найти департамент или институт.");
                }
            }

            // 3) Получаем группы департамента
            String groupsUrl = String.format("https://urfu.ru/api/v2/schedule/divisions/%d/groups?course=%d", departmentId, courseNumber);
            String groupsJson = httpClient.get(groupsUrl);
            JsonNode groupsArray = jsonMapper.readTree(groupsJson);

            long groupId = findGroupId(groupsArray, groupName);
            if (groupId == -1L) {
                throw new ScheduleFetchException("Не удалось найти группу.");
            }

            // 4) Определяем текущую неделю
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Yekaterinburg"));
            LocalDate monday = today.with(DayOfWeek.MONDAY); // находим понедельник текущей недели
            LocalDate sunday = monday.plusDays(6); // добавляем 6 дней, получается воскресенье

            // 5) Загружаем расписание
            String scheduleUrl = String.format(
                    "https://urfu.ru/api/v2/schedule/groups/%d/schedule?date_gte=%s&date_lte=%s",
                    groupId,
                    monday.format(DATE_FORMAT),
                    sunday.format(DATE_FORMAT)
            );

            String scheduleJson = httpClient.get(scheduleUrl);
            JsonNode scheduleRoot = jsonMapper.readTree(scheduleJson);
            
            JsonNode eventsNode;
            if (scheduleRoot.has("events")) { // если в JSON есть поле events — берём именно его
                eventsNode = scheduleRoot.get("events");
            } else {
                eventsNode = scheduleRoot; // если поля events нет — значит, сам корень уже является массивом занятий
            }
            
            
            if (eventsNode == null || !eventsNode.isArray()) {
                throw new ScheduleFetchException("Ошибка формата данных расписания.");
            }

            // 6) Формируем результат
            Schedule resultSchedule = new Schedule(String.valueOf(groupId), groupName);

            for (JsonNode eventNode : eventsNode) {
                String dateText = firstNonNullText(eventNode, "date", "lesson_date", "lessonDate");
                String timeBeginText = firstNonNullText(eventNode, "timeBegin", "time_begin", "timeStart", "time_start");
                String timeEndText = firstNonNullText(eventNode, "timeEnd", "time_end");
                String subjectText = firstNonNullText(eventNode, "title", "discipline", "name");
                String classroomText = firstNonNullText(eventNode, "auditoryTitle", "auditoryLocation", "auditorium");

                if (dateText == null || subjectText == null) { // если нет даты или названия предмета - скип
                	continue;
                }

                LocalTime startTime = parseTimeOrNull(timeBeginText);
                LocalTime endTime = parseTimeOrNull(timeEndText);
                LocalDate lessonDate = LocalDate.parse(dateText, DATE_FORMAT);

                if (classroomText == null) {
                    classroomText = "";
                }
                
                Lesson lesson = new Lesson(subjectText, startTime, endTime, classroomText);
                resultSchedule.addLesson(lessonDate.getDayOfWeek().toString(), lesson);
            }

            return resultSchedule;

        } catch (ScheduleFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new ScheduleFetchException("Ошибка при загрузке расписания.");
        }
    }

    
    private long findInstituteId(JsonNode divisionsArray, String instituteName) {
        if (instituteName == null || instituteName.isEmpty()) {
            return -1L;
        }

        String normalizedInstitute = normalize(instituteName);

        // по прямому совпадению
        for (JsonNode node : divisionsArray) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && title.toLowerCase().contains(instituteName.toLowerCase())) {
                return node.path("id").asLong(-1);
            }
        }

        // по нормализованной форме
        for (JsonNode node : divisionsArray) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && normalize(title).contains(normalizedInstitute)) {
                return node.path("id").asLong(-1);
            }
        }

        return -1L;
    }

    

    private long findDepartmentId(JsonNode divisionsArray, String instituteName, String departmentName) {
        String normalizedInstitute = normalize(instituteName); // нормализуем строки 
        String normalizedDepartment = normalize(departmentName);

        Long instituteId = null;
        if (instituteName != null && !instituteName.isEmpty()) { // чтобы не искать по пустой строке
            for (JsonNode node : divisionsArray) {
                String title = firstNonNullText(node, "title", "name");
                if (title != null && title.toLowerCase().contains(instituteName.toLowerCase())) {
                    instituteId = node.path("id").asLong(-1);
                    break;
                }
            }
            // дополнительная попытка — по нормализованному названию, если прямое совпадение не найдено
            if (instituteId == null || instituteId == -1) {
                for (JsonNode node : divisionsArray) {
                    String title = firstNonNullText(node, "title", "name");
                    if (title != null && normalize(title).contains(normalizedInstitute)) {
                        instituteId = node.path("id").asLong(-1);
                        break;
                    }
                }
            }
        }

        List<JsonNode> searchArea = new ArrayList<>(); // список из json node в которых искать департамент будем
        if (instituteId != null && instituteId != -1) {
            for (JsonNode node : divisionsArray) {
                if (node.path("parentId").asLong(-1) == instituteId) { // берем узлы, где parentId совпадает с айди института
                    searchArea.add(node);
                }
            }
        } else {
            // Если институт не задан или не найден — ищем среди всех элементов массива
            for (JsonNode node : divisionsArray) { 
                searchArea.add(node);
            }
        }

        // Если departmentName не задан — не искать по пустой строке (иначе совпадет с любым title)
        if (departmentName == null || departmentName.isEmpty()) {
            return -1L;
        }

        // Сначала ищем по прямому вхождению
        for (JsonNode node : searchArea) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && title.toLowerCase().contains(departmentName.toLowerCase())) {
                return node.path("id").asLong(-1); // вернем айди нужного департамента
            }
        }

        // Потом по нормализованной форме
        for (JsonNode node : searchArea) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && normalize(title).contains(normalizedDepartment)) {
                return node.path("id").asLong(-1); // вернем айди нужного департамента
            }
        }

        return -1L;
    }


    
    private long findGroupId(JsonNode groupsArray, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
        	return -1L;
        }
        String normalizedGroup = normalize(groupName);
        for (JsonNode node : groupsArray) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && title.toLowerCase().contains(groupName.toLowerCase())) {
                return node.path("id").asLong(-1);
            }
        }
        for (JsonNode node : groupsArray) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && normalize(title).contains(normalizedGroup)) {
                return node.path("id").asLong(-1);
            }
        }

        String groupWithoutHyphen = groupName.replace("-", "").toLowerCase();
        for (JsonNode node : groupsArray) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && title.toLowerCase().replace("-", "").contains(groupWithoutHyphen)) {
                return node.path("id").asLong(-1);
            }
        }
        return -1L;
    }


    
    private static String firstNonNullText(JsonNode node, String... keys) { // принимает json node и список возможных названий ключей
        if (node == null) {
        	return null;
        }
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) { // проверка, что в json node есть поле с данным ключом и оно не пустое
                return node.get(key).asText(); // возвращаем значение (asText превращаем его в строку)
            }
        }
        return null;
    }

    
    private static LocalTime parseTimeOrNull(String text) {
        if (text == null || text.isEmpty()) { // ничего не передали или передали пустую строку
        	return null;
        }
        try {
            return LocalTime.parse(text.trim()); // метод из пакета java.time, который превращает строку в объект времени
        } catch (Exception e) {
            return null;
        }
    }

    
    private static int parseCourse(String courseText) { // возвращает номер курса в виде инт 
        if (courseText == null) {
        	return 0;
        }
        try {
            return Integer.parseInt(courseText.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    
    private static String nullSafeTrim(String s) { // убирает лишние пробелы из строки
        if (s == null) {
        	return null;
        }
        String trimmed = s.trim();
        
        if (trimmed.isEmpty()) { // если без пробелов строка пустая
        	return null ;
        } else {
        	return trimmed;
        }
        
    }

    
    private static String normalize(String s) { // нормализация строк для сравнения
        if (s == null) {
        	return "";
        }
        return s.toLowerCase() // приводит к нижнему регистру
                .replaceAll("[^\\p{Alnum}\\s]", " ") // заменяет всё, что не является буквами, цифрами или пробелом, на пробел
                .replaceAll("\\s+", " ") // сжимает все идущие подряд пробелы в один
                .trim(); // убирает пробелы с начала и конца строки
    }
    
    
    private static String removeSpaces(String s) { // метод для обработки пустого департамента
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();

        // если пользователь ввёл "-" или "—" или "нет", считаем, что департамента нет
        if (trimmed.isEmpty() || trimmed.equals("-") || trimmed.equals("—") || trimmed.equalsIgnoreCase("нет")) {
            return null;
        }

        return trimmed;
    }

}


