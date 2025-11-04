package bot.schedule;

import okhttp3.OkHttpClient; // из библиотеки OkHttp для HTTP запросов
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.time.Duration; // для работы с временными интервалами

public class UrfuApiClient {
	
    private final OkHttpClient client; // объявление поля - клиент 

    public UrfuApiClient() { // конструктор 
        client = new OkHttpClient.Builder() 
                .connectTimeout(Duration.ofSeconds(20)) // таймаут на подключение (если не может 20 сек подключиться - то бросаем исключение)
                .readTimeout(Duration.ofSeconds(30)) // время на чтение
                .callTimeout(Duration.ofSeconds(60)) // общий таймер на запрос
                .build(); // завершение настройки и создание объекта
    }

    
    public String get(String url) throws IOException {
        Request req = new Request.Builder() // билдер для http запроса
                .url(url)
                .header("Accept", "application/json") // заголовок (говорит серверу, что мы ждем json в ответ)
                .build();
        
        // создает вызов и выполняет запрос (response сам закроется, тк try-with-resources)
        // newCall создает объект Call для выполнения запроса
        try (Response resp = client.newCall(req).execute()) { 
            if (!resp.isSuccessful()) { // проверка успешности ответа
                throw new IOException();
            }
            
            if(resp.body() == null) {
            	return "";
            	
            } else {
            	return resp.body().string(); // возвращаем тело ответа как строку
            }
        }
    }
}
