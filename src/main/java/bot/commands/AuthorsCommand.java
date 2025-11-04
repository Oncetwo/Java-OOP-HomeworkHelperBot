package bot.commands;

public class AuthorsCommand implements Command {
	
	@Override // метод переопределяет метод родительского класса или интерфейса
    public String getName() 
    { 
    	return "/authors"; 
    }

    @Override
    public String getInformation() 
    { 
    	return "Рассказывает о разработчиках бота"; 
    }

    @Override
    public String realization(String[] args) 
    {
        return "Нестерова Виктория, Воробьёв Александр, МЕН-241001 (КБ-202)";
    }

}
