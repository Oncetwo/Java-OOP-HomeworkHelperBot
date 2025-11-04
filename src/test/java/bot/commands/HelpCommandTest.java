package bot.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

public class HelpCommandTest {

    private Map<String, Command> getTestCommands() {
        Map<String, Command> commands = new HashMap<>();
        commands.put("/about", new AboutCommand());
        commands.put("/authors", new AuthorsCommand());
        commands.put("/help", new HelpCommand(commands)); // сам себя
        return commands;
    }

    @Test
    void helpListsAllCommands() {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{"/help"});
        assertNotNull(result);
        assertTrue(result.contains("/about"));
        assertTrue(result.contains("/authors"));
        assertTrue(result.contains("/help"));
    }

    @Test
    void helpInfoForAbout() {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{"/help", "/about"});
        assertEquals("/about — Краткое описание бота", result);
    }
    
    @Test
    void helpInfoForAuthors() {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{"/help", "/authors"});
        assertEquals("/authors — Рассказывает о разработчиках бота", result);
    }

    @Test
    void helpunknownCommand() {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{"/help", "/unknown"});
        assertEquals("Команда /unknown не найдена.", result);
    }

    // Проверка разных вариантов написания команды (регистр, пробелы, табуляция)
    @ParameterizedTest
    @ValueSource(strings = { "/help", "/HELP", "/Help", "/hElP", "   /help   ", "/help\t", "   /HELP   " })
    void helpWithTab(String inputCommand) {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{inputCommand});
        assertNotNull(result);
        assertTrue(result.contains("/about"));
        assertTrue(result.contains("/authors"));
        assertTrue(result.contains("/help"));
    }

    // Проверка выдачи информации о команде с лишними пробелами и разным регистром
    @ParameterizedTest
    @ValueSource(strings = { "/about", "/ABOUT", "   /about   ", "/about\t", "/aBoUt" })
    void helpWithTabAbout(String commandName) {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{"/help", commandName});
        assertNotNull(result);
        assertTrue(result.startsWith("/about"));
        assertTrue(result.contains("Краткое описание бота"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = { "/authors", "/AUTHORS", "   /authors   ", "/authors\t", "/aUtHoRs" })
    void helpWithTabAuthors(String commandName) {
        HelpCommand helpCmd = new HelpCommand(getTestCommands());
        String result = helpCmd.realization(new String[]{"/help", commandName});
        assertNotNull(result);
        assertTrue(result.startsWith("/authors"));
        assertTrue(result.contains("Рассказывает о разработчиках бота"));
    }
}