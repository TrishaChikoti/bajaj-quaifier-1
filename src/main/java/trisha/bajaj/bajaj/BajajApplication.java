package trisha.bajaj.bajaj;

import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class BajajApplication {

    public static void main(String[] args) {
        SpringApplication.run(BajajApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    CommandLineRunner run(RestTemplate rt) {
        return args -> {
            // 1) Generate webhook + access token
            String url = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

            Map<String, String> payload = Map.of(
                "name",  "chikoti Trisha",
                "regNo", "22BCE7535",
                "email", "trishachikoti@gmail.com"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> resp = rt.exchange(url, HttpMethod.POST, entity, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new IllegalStateException("generateWebhook failed: " + resp.getStatusCode());
            }

            Object webhook = resp.getBody().get("webhook");
            Object accessToken = resp.getBody().get("accessToken");

            System.out.println("Webhook: " + webhook);
            System.out.println("AccessToken: " + accessToken);

            // 2) Submit final SQL to the webhook using the token
            String finalSql = """
                WITH ranked AS (
                  SELECT
                    p.amount,
                    CONCAT(e.first_name, ' ', e.last_name) AS name,
                    (EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM e.dob)
                       - CASE
                           WHEN (EXTRACT(MONTH FROM CURRENT_DATE), EXTRACT(DAY FROM CURRENT_DATE))
                                < (EXTRACT(MONTH FROM e.dob), EXTRACT(DAY FROM e.dob))
                           THEN 1 ELSE 0
                         END) AS age,
                    d.department_name,
                    ROW_NUMBER() OVER (ORDER BY p.amount DESC) AS rn
                  FROM payments p
                  JOIN employee e ON e.emp_id = p.emp_id
                  JOIN department d ON d.department_id = e.department
                  WHERE EXTRACT(DAY FROM p.payment_time) <> 1
                )
                SELECT amount AS SALARY, name AS NAME, age AS AGE, department_name AS DEPARTMENT_NAME
                FROM ranked
                WHERE rn = 1;
            """;

            Map<String, String> submitBody = Map.of("finalQuery", finalSql);

            HttpHeaders submitHeaders = new HttpHeaders();
            submitHeaders.setContentType(MediaType.APPLICATION_JSON);
            // Send token as-is; if the server requires Bearer, switch to: "Bearer " + accessToken
            submitHeaders.add(HttpHeaders.AUTHORIZATION, String.valueOf(accessToken));

            HttpEntity<Map<String, String>> submitEntity = new HttpEntity<>(submitBody, submitHeaders);

            ResponseEntity<String> submitResp = rt.exchange(
                String.valueOf(webhook),
                HttpMethod.POST,
                submitEntity,
                String.class
            );

            System.out.println("Submission status: " + submitResp.getStatusCode());
            System.out.println("Submission body: " + submitResp.getBody());
        };
    }
}
