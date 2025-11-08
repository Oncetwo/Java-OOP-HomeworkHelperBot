package bot.commands;

import bot.schedule.*;
import bot.user.User;
import bot.user.UserStorage;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ScheduleCommandTest {

    private UserStorage userStorage;
    private final long CHAT_ID = 99999L;

    @BeforeEach
    public void setUp() {
        userStorage = mock(UserStorage.class);
    }

    @Test
    public void testScheduleFetcher_serializationDeserialization_parsesJsonToSchedule() throws Exception {
        // Тест проверяет: при подстановке JSON (mock клиента) ScheduleFetcher корректно парсит данные в Java-объекты

        ScheduleFetcher fetcher = new ScheduleFetcher();
        // создаём реальный экземпляр fetcher — но подменим у него приватный HTTP-клиент

        UrfuApiClient mockClient = mock(UrfuApiClient.class);
        // мок, который будет отдавать заранее подготовленные JSON-строки

        String divisionsJson = "[{\"id\":1,\"title\":\"Институт тестовый\"},{\"id\":2,\"title\":\"ИЕНиМ\"}]";
        String groupsJson = "[{\"id\":100,\"title\":\"МЕН-241001\"}]";
        String scheduleJson = "[" +
                "{\"date\":\"2025-11-03\",\"timeBegin\":\"09:00\",\"timeEnd\":\"10:30\",\"title\":\"Математика\",\"auditoryTitle\":\"101\"}" +
                "]";

        // Мокаем поведение HTTP-клиента: при запросе на divisions -> возвращаем divisionsJson
        when(mockClient.get("https://urfu.ru/api/v2/schedule/divisions")).thenReturn(divisionsJson);
        // при запросах для групп конкретного института (например, divisions/2/groups?course=2) -> возвращаем groupsJson
        when(mockClient.get("https://urfu.ru/api/v2/schedule/divisions/2/groups?course=2")).thenReturn(groupsJson);
        // а при любом запросе, начинающемся с https://urfu.ru/api/v2/schedule/groups/ вернём scheduleJson
        when(mockClient.get(startsWith("https://urfu.ru/api/v2/schedule/groups/"))).thenReturn(scheduleJson);

        // Подмена приватного поля httpClient в fetcher через reflection (ScheduleFetcher создаёт его в конструкторе)
        Field clientField = ScheduleFetcher.class.getDeclaredField("httpClient"); //достаём приватное поле
        clientField.setAccessible(true); // разрешаем подмену
        clientField.set(fetcher, mockClient); // произошла подмена

        User user = new User(1L, "Test", "МЕН-241001", "ИЕНиМ", "", "2", bot.fsm.DialogState.REGISTERED);

        Schedule schedule = fetcher.fetchForUser(user);
        // Вызываем метод — он должен выполнить "HTTP" запросы (через mock) и собрать Schedule

        assertNotNull(schedule);

        assertEquals("МЕН-241001", schedule.getGroupName());

        Map<String, List<Lesson>> weekly = schedule.getWeeklySchedule();
        assertTrue(weekly.containsKey("MONDAY") || weekly.size() > 0);
        // убеждаемся, что расписание содержит хотя бы один день

        // Ищем в расписании урок с названием "Математика" и временем начала 09:00
        boolean found = weekly.values().stream()
                .flatMap(List::stream)
                .anyMatch(l -> "Математика".equals(l.getSubject()) && LocalTime.parse("09:00").equals(l.getStartTime()));

        assertTrue(found, "Не найдена ожидаемая пара 'Математика' 09:00");
    }

    @Test
    public void testScheduleCommand_realizationWithChatId_readsFromSqliteAndFormatsOutput() throws Exception {
        // Тест проверяет ScheduleCommand в сочетании с SQLiteScheduleStorage:
        // 1) создаём временную физическую базу schedules_test.db
        // 2) сохраняем туда Schedule
        // 3) переименовываем в schedules.db (так как ScheduleCommand внутри может создавать именно этот файл)
        // 4) вызываем метод реализации команды и проверяем текст вывода

        String tmpDb = "schedules_test.db";
        File f = new File(tmpDb);
        if (f.exists()) f.delete();
        // удаляем старый файл, если остался от предыдущих запусков

        SQLiteScheduleStorage storage = new SQLiteScheduleStorage(tmpDb);
        storage.initialize();

        Schedule schedule = new Schedule("g-42", "МЕН-241001");
        Lesson lesson = new Lesson("Физика", java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 30), "301");
        schedule.addLesson("MONDAY", lesson);

        storage.saveSchedule(schedule);
        storage.close();

        User user = new User(CHAT_ID, "Пётр", "МЕН-241001", "ИЕНиМ", "ШН", "2", bot.fsm.DialogState.REGISTERED);
        when(userStorage.getUser(CHAT_ID)).thenReturn(user);

        ScheduleCommand scheduleCommand = new ScheduleCommand(userStorage);

        // scheduleCommand ожидает файл schedules.db; поэтому переименуем только что созданный tmpDb
        File src = new File(tmpDb);
        File dest = new File("schedules.db");
        if (dest.exists()) dest.delete();
        boolean renamed = src.renameTo(dest);
        assertTrue(renamed);
        // Если не удалось переименовать, тест должен упасть — значит среда не готова

        try {
            // Вызываем метод реализации команды. В реальном проекте это может быть отправка SendMessage,
            // но в тесте предполагается, что реализация возвращает строку или текст — адаптируй под реальную сигнатуру.
            String out = scheduleCommand.realizationWithChatId(CHAT_ID, new String[]{"/schedule"});
            assertNotNull(out);
            assertTrue(out.contains("Расписание для группы"));
            assertTrue(out.contains("Физика"));
            assertTrue(out.contains("10:00") || out.contains("10:0"));
            // Проверяем, что общий вывод содержит заголовок, предмет и время

            // Дополнительно проверяем фильтрацию по дню:
            String out2 = scheduleCommand.realizationWithChatId(CHAT_ID, new String[]{"/schedule", "Monday"});
            assertNotNull(out2);
            assertTrue(out2.contains("Физика"));
            assertTrue(out2.contains("301"));
        } finally {
            // Всегда стараемся убрать временную БД, даже если тест упал
            File dbFile = new File("schedules.db");
            if (dbFile.exists()) dbFile.delete();
        }
    }

    // Утиль — возвращает Mockito-матчер, который оценивает аргумент как String, начинающийся с префикса.
    // Используется в when(...).thenReturn(...) для соответствия любым URL, начинающимся с базового префикса.
    private static String startsWith(String prefix) {
        return Mockito.argThat(s -> s != null && s.startsWith(prefix));
    }
}