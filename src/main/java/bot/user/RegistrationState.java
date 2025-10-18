package bot.user;

public enum RegistrationState {
	 ASK_NAME,  // пользователь начал регистрацию, ждем имя
     ASK_GROUP, // пользователь ввел имя, ждем группу
     REGISTERED // пользователь ввел группу, завершили регистрацию
}
