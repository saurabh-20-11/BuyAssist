package ai.buyassist.ap2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Mirrors the AP2 service's ServiceResponse shape: {ok, operation, detail, data}.
 * {@code data} is left as a loose map because its contents vary by operation
 * (cart mandate, payment mandate, receipt, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Ap2Response(
        boolean ok,
        String operation,
        String detail,
        Map<String, Object> data
) {
    public Map<String, Object> dataOrEmpty() {
        return data == null ? Map.of() : data;
    }
}
