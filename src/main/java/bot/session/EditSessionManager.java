package bot.session;

import java.util.concurrent.ConcurrentHashMap; // несколько потоков могут одновременно читать/писать (поточно-безопасная реализация map)
import java.util.Map;

public class EditSessionManager {
    private static final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public static Session getSession(long chatId) {
    	Session session = sessions.get(chatId);
    	if (session == null) {
    	    session = new Session();
    	    sessions.put(chatId, session);
    	}
    	return session;
    }

    public static void clearSession(long chatId) {
        sessions.remove(chatId);
    }
}

