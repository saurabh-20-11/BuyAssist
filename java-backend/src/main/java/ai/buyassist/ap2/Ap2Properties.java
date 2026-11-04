package ai.buyassist.ap2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for reaching the Python AP2 service, bound from {@code ap2.*}.
 */
@ConfigurationProperties(prefix = "ap2")
public record Ap2Properties(String baseUrl) {
}
