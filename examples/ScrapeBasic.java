import com.thordata.sdk.ThorDataClient;
import java.util.Map;

public final class ScrapeBasic {
    private ScrapeBasic() {
    }

    public static void main(String[] args) {
        try (ThorDataClient client = ThorDataClient.builder().build()) {
            System.out.println(client.scrape(
                    "linkedin_company_information_by-url",
                    Map.of("url", "https://www.linkedin.com/company/microsoft")));
        } catch (RuntimeException error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
