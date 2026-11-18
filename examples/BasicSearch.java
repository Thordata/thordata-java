import com.thordata.sdk.ThorDataClient;
import java.util.Map;

public final class BasicSearch {
    private BasicSearch() {
    }

    public static void main(String[] args) {
        try (ThorDataClient client = ThorDataClient.builder().build()) {
            System.out.println(client.search(Map.of(
                    "engine", "google_search",
                    "q", "latest AI agent research",
                    "gl", "us",
                    "hl", "en",
                    "num", 5)));
        } catch (RuntimeException error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
