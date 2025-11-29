package bot.commands;

import bot.user.User;
import bot.user.UserStorage;

public class SubscriptionCommand implements Command {
    
    private final UserStorage userStorage;
    
    public SubscriptionCommand(UserStorage userStorage) {
        this.userStorage = userStorage;
    }
    
    @Override
    public String getName() {
        return "/subscription";
    }
    
    @Override
    public String getInformation() {
        return "Управление подпиской на ежедневные уведомления\n" +
               "/subscription on - включить уведомления\n" +
               "/subscription off - выключить уведомления\n" +
               "/subscription status - статус подписки";
    }
    
    @Override
    public String realization(String[] args) {
        return "Использование:\n" +
               "/subscription on - включить уведомления\n" +
               "/subscription off - выключить уведомления\n" +
               "/subscription status - статус подписки";
    }
    
    public String realizationWithChatId(long chatId, String[] args) {
        User user = userStorage.getUser(chatId);
        if (user == null) {
            return "❌ Вы не зарегистрированы. Введите /start для регистрации.";
        }
        
        if (args.length < 2) {
            return realization(args);
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "on":
            case "вкл":
            case "включить":
                user.setSubscriptionEnabled(true);
                userStorage.updateUser(user);
                return "✅ Ежедневные уведомления включены! Вы будете получать напоминания о ДЗ.";
                
            case "off":
            case "выкл":
            case "выключить":
                user.setSubscriptionEnabled(false);
                userStorage.updateUser(user);
                return "🔕 Ежедневные уведомления выключены. Вы больше не будете получать напоминания.\n" +
                       "Чтобы включить снова, используйте /subscription on";
                
            case "status":
            case "статус":
                boolean isEnabled = user.getSubscriptionEnabled();
                return isEnabled ? 
                    "📢 Статус подписки: ВКЛЮЧЕНА\nВы получаете ежедневные уведомления о ДЗ." :
                    "🔕 Статус подписки: ВЫКЛЮЧЕНА\nВы не получаете ежедневные уведомления.";
                    
            default:
                return "❌ Неизвестная команда. Используйте:\n" +
                       "/subscription on - включить уведомления\n" +
                       "/subscription off - выключить уведомления\n" +
                       "/subscription status - статус подписки";
        }
    }
}