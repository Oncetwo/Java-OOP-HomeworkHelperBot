package bot.commands;

import bot.schedule.*;
import bot.user.User;
import bot.user.UserStorage;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import bot.user.exception.ScheduleStorageException;

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
        // Новый, надёжный вариант: прямо создаём schedules.db (без ненадёжного переименования).
        String dbName = "schedules.db";
        File dbFile = new File(dbName);
        if (dbFile.exists()) {
            // пытаемся удалить оставшийся от прошлых тестов файл
            dbFile.delete();
        }

        // Создаём и инициализируем именно тот файл, который ожидает ScheduleManager/ScheduleCommand
        SQLiteScheduleStorage storage = new SQLiteScheduleStorage(dbName);
        storage.initialize();

        Schedule schedule = new Schedule("g-42", "МЕН-241001");
        Lesson lesson = new Lesson("Физика", java.time.LocalTime.of(10, 0),
                java.time.LocalTime.of(11, 30), "301");
        schedule.addLesson("MONDAY", lesson);

        // Сохраняем расписание; если оно уже есть — игнорируем это исключение (чтобы тесты были идемпотентными)
        try {
            storage.saveSchedule(schedule);
        } catch (ScheduleStorageException e) {
            if (!e.getMessage().contains("Расписание для этой группы уже существует")) {
                storage.close();
                throw e;
            }
            // else — уже есть, можно продолжать
        } finally {
            // в любом случае закрываем storage (чтобы не держать файл открытым)
            storage.close();
        }

        User user = new User(CHAT_ID, "Пётр", "МЕН-241001", "ИЕНиМ", "ШН", "2", bot.fsm.DialogState.REGISTERED);
        when(userStorage.getUser(CHAT_ID)).thenReturn(user);

        ScheduleCommand scheduleCommand = new ScheduleCommand(userStorage);

        try {
            String out = scheduleCommand.realizationWithChatId(CHAT_ID, new String[]{"/schedule"});
            assertNotNull(out);
            assertTrue(out.contains("Расписание для группы"));
            assertTrue(out.contains("Физика"));
            assertTrue(out.contains("10:00") || out.contains("10:0"));

            // Проверяем фильтрацию по дню:
            String out2 = scheduleCommand.realizationWithChatId(CHAT_ID, new String[]{"/schedule", "Monday"});
            assertNotNull(out2);
            assertTrue(out2.contains("Физика"));
            assertTrue(out2.contains("301"));
        } finally {
            // Чистим файл после теста
            File f = new File(dbName);
            if (f.exists()) {
                f.delete();
            }
        }
    }

    // Утиль — возвращает Mockito-матчер, который оценивает аргумент как String, начинающийся с префикса.
    // Используется в when(...).thenReturn(...) для соответствия любым URL, начинающимся с базового префикса.
    private static String startsWith(String prefix) {
        return Mockito.argThat(s -> s != null && s.startsWith(prefix));
    }
}