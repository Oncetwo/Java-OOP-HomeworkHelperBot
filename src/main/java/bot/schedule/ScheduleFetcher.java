package bot.schedule;

import bot.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ScheduleFetcher {

    private static final String DIVISIONS_URL = "https://urfu.ru/api/v2/schedule/divisions";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UrfuApiClient httpClient;
    private final ObjectMapper jsonMapper;

    public ScheduleFetcher() {
        this.httpClient = new UrfuApiClient();
        this.jsonMapper = new ObjectMapper();
    }

    /**
     * Загружает расписание для пользователя. В случае ошибки бросает ScheduleFetchException,
     * сообщение которого можно показать пользователю (оно содержит проблемный параметр).
     */
    public Schedule fetchForUser(User user) throws ScheduleFetchException {
        try {
            String instituteName = nullSafeTrim(user.getUniversity());
            String departmentName = nullSafeTrim(user.getDepartment());
            String groupName = nullSafeTrim(user.getGroup());
            int courseNumber = parseCourse(user.getCourse());

            // 1) получить divisions
            String divisionsJson = httpClient.get(DIVISIONS_URL);
            JsonNode divisionsArray = jsonMapper.readTree(divisionsJson);

            // 2) найти departmentId: сначала по institute (если задан), затем среди детей искать департамент
            long departmentId = findDepartmentId(divisionsArray, instituteName, departmentName);
            if (departmentId == -1L) {
                // если не нашли департамент — сообщаем какой параметр не найден (предпочтительно department, иначе institute)
                String missingParam = (departmentName != null && !departmentName.isEmpty()) ? ("департамент '" + departmentName + "'") :
                        (instituteName != null && !instituteName.isEmpty()) ? ("институт '" + instituteName + "'") :
                                "институт/департамент";
                throw new ScheduleFetchException("Не удалось найти " + missingParam + ". Проверьте параметры.");
            }

            // 3) получить группы для department + course
            String groupsUrl = String.format("https://urfu.ru/api/v2/schedule/divisions/%d/groups?course=%d", departmentId, courseNumber);
            String groupsJson = httpClient.get(groupsUrl);
            JsonNode groupsArray = jsonMapper.readTree(groupsJson);

            long groupId = findGroupId(groupsArray, groupName);
            if (groupId == -1L) {
                throw new ScheduleFetchException("Группа '" + (groupName == null ? "" : groupName) + "' не найдена.");
            }

            // 4) вычислить диапазон текущей недели (понедельник - воскресенье) в зоне Europe/Helsinki
            LocalDate today = LocalDate.now(ZoneId.of("Europe/Helsinki"));
            LocalDate monday = today;
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.minusDays(1);
            }
            LocalDate sunday = monday.plusDays(6);

            String scheduleUrl = String.format(
                    "https://urfu.ru/api/v2/schedule/groups/%d/schedule?date_gte=%s&date_lte=%s",
                    groupId,
                    monday.format(DATE_FORMAT),
                    sunday.format(DATE_FORMAT)
            );

            String scheduleJson = httpClient.get(scheduleUrl);
            JsonNode scheduleRoot = jsonMapper.readTree(scheduleJson);

            JsonNode eventsNode = scheduleRoot.has("events") ? scheduleRoot.get("events") : scheduleRoot;

            Schedule resultSchedule = new Schedule(String.valueOf(groupId), groupName);

            if (eventsNode != null && eventsNode.isArray()) {
                for (JsonNode eventNode : eventsNode) {
                    // читаем поля, учитывая возможные варианты имен
                    String dateText = firstNonNullText(eventNode, "date", "lesson_date", "lessonDate");
                    String timeBeginText = firstNonNullText(eventNode, "timeBegin", "time_begin", "timeStart", "time_start");
                    String timeEndText = firstNonNullText(eventNode, "timeEnd", "time_end", "timeEnd", "time_end");
                    String subjectText = firstNonNullText(eventNode, "title", "discipline", "name");
                    String classroomText = firstNonNullText(eventNode, "auditoryTitle", "auditoryLocation", "auditorium", "auditory_title");

                    if (dateText == null || subjectText == null) {
                        // если нет даты или предмета — пропускаем запись
                        continue;
                    }

                    LocalTime startTime = parseTimeOrNull(timeBeginText);
                    LocalTime endTime = parseTimeOrNull(timeEndText);

                    // используем вашу сущность Lesson
                    Lesson lesson = new Lesson(subjectText, startTime, endTime, classroomText == null ? "" : classroomText);

                    // добавляем в Schedule (используем ключ днём недели в виде "MONDAY"/"TUESDAY" и т.д.)
                    LocalDate lessonDate = LocalDate.parse(dateText, DATE_FORMAT);
                    String dayKey = lessonDate.getDayOfWeek().toString();
                    resultSchedule.addLesson(dayKey, lesson);
                }
            } else {
                // если структура неожиданная — возвращаем понятную ошибку
                throw new ScheduleFetchException("Непредвиденная структура ответа расписания от сервера.");
            }

            return resultSchedule;

        } catch (ScheduleFetchException e) {
            // пробрасываем пользовательские исключения без изменений
            throw e;
        } catch (Exception e) {
            // оборачиваем техническую ошибку в понятное сообщение
            throw new ScheduleFetchException("Ошибка при загрузке расписания: " + e.getMessage());
        }
    }

    // ----- вспомогательные методы -----

    private long findDepartmentId(JsonNode divisionsArray, String instituteName, String departmentName) {
        String normalizedInstitute = normalize(instituteName);
        String normalizedDepartment = normalize(departmentName);

        // 1) Найти instituteId (если instituteName задан)
        Long instituteId = null;
        if (instituteName != null && !instituteName.isEmpty()) {
            for (JsonNode node : divisionsArray) {
                String title = firstNonNullText(node, "title", "name");
                if (title != null && title.toLowerCase().contains(instituteName.toLowerCase())) {
                    instituteId = node.path("id").asLong(-1);
                    break;
                }
            }
            if (instituteId == null) {
                for (JsonNode node : divisionsArray) {
                    String title = firstNonNullText(node, "title", "name");
                    if (title != null && normalize(title).contains(normalizedInstitute)) {
                        instituteId = node.path("id").asLong(-1);
                        break;
                    }
                }
            }
        }

        // 2) Если institute найден — искать департамент среди детей (preferred)
        if (instituteId != null && instituteId != -1) {
            // collect children
            List<JsonNode> children = new ArrayList<>();
            for (JsonNode node : divisionsArray) {
                if (node.has("parentId") && !node.get("parentId").isNull() && node.path("parentId").asLong(-1) == instituteId) {
                    children.add(node);
                }
            }
            // search children by departmentName
            if (departmentName != null && !departmentName.isEmpty()) {
                // raw contains
                for (JsonNode child : children) {
                    String title = firstNonNullText(child, "title", "name");
                    if (title != null && title.toLowerCase().contains(departmentName.toLowerCase())) {
                        return child.path("id").asLong(-1);
                    }
                }
                // normalized contains
                for (JsonNode child : children) {
                    String title = firstNonNullText(child, "title", "name");
                    if (title != null && normalize(title).contains(normalizedDepartment)) {
                        return child.path("id").asLong(-1);
                    }
                }
            }
            // если среди детей не нашли — попробуем global fallback дальше
        }

        // 3) global fallback — найдем любой division, содержащий departmentName и имеющий parentId != null
        if (departmentName != null && !departmentName.isEmpty()) {
            for (JsonNode node : divisionsArray) {
                String title = firstNonNullText(node, "title", "name");
                if (title != null && title.toLowerCase().contains(departmentName.toLowerCase())) {
                    if (node.has("parentId") && !node.get("parentId").isNull()) return node.path("id").asLong(-1);
                }
            }
            for (JsonNode node : divisionsArray) {
                String title = firstNonNullText(node, "title", "name");
                if (title != null && normalize(title).contains(normalizedDepartment)) {
                    if (node.has("parentId") && !node.get("parentId").isNull()) return node.path("id").asLong(-1);
                }
            }
        }

        // nothing found
        return -1L;
    }

    private long findGroupId(JsonNode groupsArray, String groupName) {
        if (groupName == null || groupName.isEmpty()) return -1L;
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
        // also try variant without hyphen (e.g., MENGROUP vs MEN-GROUP)
        String groupWithoutHyphen = groupName.replace("-", "").toLowerCase();
        for (JsonNode node : groupsArray) {
            String title = firstNonNullText(node, "title", "name");
            if (title != null && title.toLowerCase().replace("-", "").contains(groupWithoutHyphen)) {
                return node.path("id").asLong(-1);
            }
        }
        return -1L;
    }

    private static String firstNonNullText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private static LocalTime parseTimeOrNull(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return LocalTime.parse(text);
        } catch (Exception e) {
            // try to normalize like "HH:mm" or "H:mm"
            try {
                String normalized = text.trim();
                return LocalTime.parse(normalized);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static int parseCourse(String courseText) {
        if (courseText == null) return 0;
        try {
            return Integer.parseInt(courseText.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String nullSafeTrim(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
