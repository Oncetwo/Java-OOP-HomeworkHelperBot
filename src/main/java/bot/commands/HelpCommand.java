package bot.commands;

import java.util.List; // подключаем интерфейс списка

public class HelpCommand implements CommandInterface {

    private final List<CommandInterface> commands; //ссылка на список всех команд. final - 
    // список можно изменять, но ссылка на объект не заменится)

    public HelpCommand(List<CommandInterface> commands) { // конструктор (передается список всех команд)
        this.commands = commands;
    }

    @Override
    public String getName() {
        return "/help";
    }

    @Override
    public String getInformation() {
        return "Выводит список всех доступных команд или информацию о конкретной команде";
    }

    @Override
    public String realization(String[] args) {
        if (args.length > 1) { // условие, что после /help идет имя команды
            String cmdName = args[1];
            for (CommandInterface commandName : commands) {
                if (commandName.getName().equalsIgnoreCase(cmdName)) { // equalsIgnoreCase - метод сравнивает строки без учета регистра
                    return commandName.getName() + " — " + commandName.getInformation();
                }
            }
            return "Команда " + cmdName + " не найдена.";
        }
        
        else { // если аргументов нет — показать все команды
            String result = "Доступные команды:\n";
            for (CommandInterface command : commands) {
                result += command.getName() + " — " + command.getInformation() + "\n";
            }
            return result;
        }
    }
}
