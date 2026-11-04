package ai.buyassist.ap2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Thin HTTP client for the Python AP2 service. Bounded connect/read timeouts so
 * an unavailable AP2 service fails fast rather than hanging the request.
 */
@Component
public class Ap2Client {

    private static final Logger log = LoggerFactory.getLogger(Ap2Client.class);

    private final RestClient restClient;

    public Ap2Client(Ap2Properties props, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        this.restClient = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    public Ap2Response checkoutMandate(Ap2Requests.CheckoutMandate body) {
        return post("/ap2/checkout-mandate", body);
    }

    public Ap2Response paymentMandate(Ap2Requests.PaymentMandate body) {
        return post("/ap2/payment-mandate", body);
    }

    public Ap2Response intentMandate(Ap2Requests.IntentMandate body) {
        return post("/ap2/intent-mandate", body);
    }

    public Ap2Response verify(Ap2Requests.Verify body) {
        return post("/ap2/verify", body);
    }

    public Ap2Response pay(Ap2Requests.PaymentMandate body) {
        return post("/ap2/pay", body);
    }

    private Ap2Response post(String path, Object body) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Ap2Response.class);
        } catch (Exception e) {
            log.error("AP2 call to {} failed: {}", path, e.getMessage());
            throw new Ap2Exception("AP2 service call to " + path + " failed: " + e.getMessage(), e);
        }
    }
}
