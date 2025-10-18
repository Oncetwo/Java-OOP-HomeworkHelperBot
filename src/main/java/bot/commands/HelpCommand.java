package bot.commands;

import java.util.Map;
public class HelpCommand implements CommandInterface {

	private final Map<String, CommandInterface> commands; //ссылка на мапу всех команд. final - 
    // можно изменять, но ссылка на объект не заменится)

    public HelpCommand(Map<String, CommandInterface> commands) { // конструктор (передается список всех команд)
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
        	String cmdName = args[1].trim().toLowerCase();
            CommandInterface cmd = commands.get(cmdName); // ищем значение по ключу в мапе
            if (cmd != null) {
                return cmd.getName() + " — " + cmd.getInformation();
            } 
            else {
                return "Команда " + cmdName + " не найдена.";
            }
        }
        
        else { // если аргументов нет — показать все команды
        	String result = "Доступные команды:\n";
        	for (CommandInterface command : commands.values()) {
        	    result += command.getName() + " — " + command.getInformation() + "\n";
            }
            return result;
        }
    }
}