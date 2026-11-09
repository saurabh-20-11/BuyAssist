package ai.buyassist.config;

import ai.buyassist.ap2.Ap2Properties;
import ai.buyassist.ebay.EbayProperties;
import ai.buyassist.gemini.GeminiProperties;
import ai.buyassist.jev.JevProperties;
import ai.buyassist.scoring.ScoringProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires configuration properties and shared HTTP client infrastructure.
 */
@Configuration
@EnableConfigurationProperties({JevProperties.class, GeminiProperties.class,
        ScoringProperties.class, Ap2Properties.class, EbayProperties.class})
public class AppConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
