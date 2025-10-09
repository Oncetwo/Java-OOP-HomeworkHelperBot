package bot.commands;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;  // для создания параметризованных тестов
import org.junit.jupiter.params.provider.ValueSource; // предоставляет массив значений для теста

public class AuthorsCommandTest {

    @Test
    void authorsBasics() {
        AuthorsCommand cmd = new AuthorsCommand();

        assertEquals("/authors", cmd.getName(), "Имя команды должно быть /authors");

        String info = cmd.getInformation();
        assertNotNull(info, "getInformation() не должен вернуть null");
        assertEquals("Рассказывает о разработчиках бота", info);

        String result = cmd.realization(new String[]{"/authors"});
        assertNotNull(result, "realization не должен возвращать null");
        assertEquals("Нестерова Виктория, Воробьёв Александр, МЕН-241001 (КБ-202)", result);
    }
    
    
    // проверка на лищние слова после команды
    void authorsWithExtraWord() {
        AuthorsCommand cmd = new AuthorsCommand();
        
        String result = cmd.realization(new String[]{"/authors", "extra", "word"});
        assertNotNull(result);
        assertEquals("Нестерова Виктория, Воробьёв Александр, МЕН-241001 (КБ-202)", result);
    }
    
    
    // проверка на регистр, табуляцию и пробелы в команде
    @ParameterizedTest 
    @ValueSource(strings = { "/authors", "/AUTHORS", "/Authors", "/aUtHoRs", "  /authors  ", "/authors\t", "   /AUTHORS   " })
    void authorsWithTab(String commandName) {
        AuthorsCommand cmd = new AuthorsCommand();
        
        String result = cmd.realization(new String[]{commandName});
        assertNotNull(result);
        assertEquals("Нестерова Виктория, Воробьёв Александр, МЕН-241001 (КБ-202)", result);
    }      
}

