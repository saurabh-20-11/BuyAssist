package ai.buyassist.controller;

import ai.buyassist.gemini.IntentExtractionService;
import ai.buyassist.gemini.IntentExtractionService.ExtractedIntent;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Voice/natural-language entry point. Takes a transcript (from the browser's
 * speech recognition or a typed sentence) and returns the structured intent
 * Gemini extracted, plus a spoken-style confirmation. This is the "understand"
 * step; the client then runs the normal /api/procurement/evaluate pipeline.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final IntentExtractionService extraction;

    public VoiceController(IntentExtractionService extraction) {
        this.extraction = extraction;
    }

    public record InterpretRequest(@NotBlank String transcript) {
    }

    @PostMapping("/interpret")
    public ExtractedIntent interpret(@RequestBody InterpretRequest req) {
        return extraction.extract(req.transcript());
    }
}
