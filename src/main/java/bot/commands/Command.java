package bot.commands;

public interface Command {
	
	String getName();
	String getInformation();
	String realization(String[] args);

}
