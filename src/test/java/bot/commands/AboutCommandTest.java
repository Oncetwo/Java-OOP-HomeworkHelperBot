package bot.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

 // Предположение: AboutCommand реализует CommandInterface.

public class AboutCommandTest {

    @Test // помечаем метод как тест
    void aboutBasics() {
        AboutCommand cmd = new AboutCommand();

        // ожидаем строгое соответствие имени "/about"
        assertEquals("/about", cmd.getName());

        // проверяем, что информация о команде не null и не пустая
        String info = cmd.getInformation();
        assertNotNull(info, "getInformation() не должен вернуть null");
        assertEquals("Краткое описание бота", info);

        // проверяем реализацию: передаём минимальный массив аргументов (команда без дополнительных аргументов)
        String result = cmd.realization(new String[]{"/about"});
        assertNotNull(result, "realization не должен возвращать null");
        assertEquals("Привет! Я - бот, который поможет тебе не забывать о своем домашнем задании.", result);
    }
    
    // проверка на лищние слова после команды
    void aboutWithExtraWord() {
        AboutCommand cmd = new AboutCommand();
        
        String result = cmd.realization(new String[]{"/about", "extra", "word"});
        assertNotNull(result);
        assertEquals("Привет! Я - бот, который поможет тебе не забывать о своем домашнем задании.", result);
    }
    
    
    // проверка на регистр, табуляцию и пробелы в команде
    @ParameterizedTest 
    @ValueSource(strings = { "/about", "/ABOUT", "/About", "/aBoUt", "  /about  ", "/about\t", "   /ABOUT   " })
    void aboutWithTab(String commandName) {
        AboutCommand cmd = new AboutCommand();
        
        String result = cmd.realization(new String[]{commandName});
        assertNotNull(result);
        assertEquals("Привет! Я - бот, который поможет тебе не забывать о своем домашнем задании.", result);
    }   
}
