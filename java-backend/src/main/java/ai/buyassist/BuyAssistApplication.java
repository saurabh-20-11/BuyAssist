package ai.buyassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BuyAssist backend entry point.
 *
 * <p>This service owns all deterministic business rules (scoring, policy gate),
 * orchestrates the Jev decision layer, and talks to the AP2 service. It never
 * delegates a hard rule or payment authority to an AI model.
 */
@SpringBootApplication
public class BuyAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuyAssistApplication.class, args);
    }
}
