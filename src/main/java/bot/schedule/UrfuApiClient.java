package bot.schedule;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.time.Duration;

public class UrfuApiClient {
    private final OkHttpClient client;

    public UrfuApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(60))
                .build();
    }

    public String get(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " for " + url);
            }
            return resp.body() == null ? "" : resp.body().string();
        }
    }
}
