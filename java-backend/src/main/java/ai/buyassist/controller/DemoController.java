package ai.buyassist.controller;

import ai.buyassist.procurement.Candidate;
import ai.buyassist.procurement.DecisionTrace;
import ai.buyassist.procurement.ProcurementIntent;
import ai.buyassist.procurement.ProcurementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Temporary demo endpoint to exercise the end-to-end evaluation pipeline
 * (scoring -> policy gate -> bounded decision) on the canonical monitor
 * scenario. This will be replaced by the real voice/intent-driven flow in a
 * later phase; it exists now to verify the pipeline and the live Gemini
 * decision call.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final ProcurementService procurementService;

    public DemoController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @GetMapping("/monitors")
    public DecisionTrace monitors() {
        ProcurementIntent intent = new ProcurementIntent(
                "27 inch monitor", 5, 1200.0,
                List.of("USB-C"), 3,
                "for the analytics team");

        List<Candidate> candidates = List.of(
                new Candidate("Dell", "TechMart", 219.0, 5, 3, 2,
                        List.of("USB-C", "HDMI"), 0.95),
                new Candidate("HP", "OfficeHub", 229.0, 5, 3, 3,
                        List.of("USB-C"), 0.90),
                new Candidate("Lenovo", "BusinessDirect", 215.0, 5, 1, 1,
                        List.of("USB-C"), 0.88));

        return procurementService.evaluate(intent, candidates, null);
    }
}
