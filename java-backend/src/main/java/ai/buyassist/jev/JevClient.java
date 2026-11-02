package ai.buyassist.jev;

import ai.buyassist.jev.wire.JevApiRequest;
import ai.buyassist.jev.wire.JevApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin HTTP client for the Jev (TypeSafe AI) System One endpoint.
 *
 * <p>Responsibilities only: build the authorized POST, send the typed request,
 * map transport/HTTP errors, and retry on 429/529 with backoff. It contains no
 * business logic — {@link JevDecisionService} owns question construction and
 * result mapping.
 *
 * <p>Endpoint: {@code POST {baseUrl}/v1/systemone} with
 * {@code Authorization: Bearer <TYPESAFE_API_KEY>}.
 */
@Component
public class JevClient {

    private static final Logger log = LoggerFactory.getLogger(JevClient.class);
    private static final int MAX_RETRIES = 3;

    private final JevProperties props;
    private final RestClient restClient;

    public JevClient(JevProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.baseUrl(props.baseUrl()).build();
    }

    /**
     * Call the System One endpoint. Retries transient 429/529 responses.
     *
     * @throws JevException on auth/validation failures or exhausted retries
     */
    public JevApiResponse systemOne(JevApiRequest request) {
        if (!props.isConfigured()) {
            throw new JevException("Jev API key not configured (TYPESAFE_API_KEY).");
        }

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return restClient.post()
                        .uri("/v1/systemone")
                        .header("Authorization", "Bearer " + props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                            int code = res.getStatusCode().value();
                            String body = new String(res.getBody().readAllBytes());
                            throw new JevHttpException(code, body);
                        })
                        .body(JevApiResponse.class);
            } catch (JevHttpException e) {
                boolean retryable = e.status() == 429 || e.status() == 529;
                if (retryable && attempt <= MAX_RETRIES) {
                    long backoffMs = (long) (Math.pow(2, attempt) * 200);
                    log.warn("Jev {} (attempt {}/{}), backing off {}ms",
                            e.status(), attempt, MAX_RETRIES, backoffMs);
                    sleep(backoffMs);
                    continue;
                }
                throw new JevException(describe(e.status()) + " body=" + e.body(), e);
            }
        }
    }

    private String describe(int status) {
        return switch (status) {
            case 401 -> "Jev 401: missing or invalid API key.";
            case 422 -> "Jev 422: request validation failed.";
            case 429 -> "Jev 429: rate limited.";
            case 529 -> "Jev 529: overloaded.";
            default -> "Jev HTTP error " + status + ".";
        };
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new JevException("Interrupted during Jev retry backoff", ie);
        }
    }

    /** Internal carrier for an HTTP error with status + body. */
    private static final class JevHttpException extends RuntimeException {
        private final int status;
        private final String body;

        JevHttpException(int status, String body) {
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        String body() {
            return body;
        }
    }
}
