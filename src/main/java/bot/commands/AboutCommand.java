package bot.commands;

public class AboutCommand implements CommandInterface { // implements → когда класс реализует интерфейс.

    @Override // метод переопределяет метод родительского класса или интерфейса
    public String getName() 
    { 
    	return "/about"; 
    }

    @Override
    public String getInformation() 
    { 
    	return "Краткое описание бота"; 
    }

    @Override
    public String realization(String[] args) 
    {
        return "Привет! Я - бот, который поможет тебе не забывать о своем домашнем задании.";
    }
    
}
