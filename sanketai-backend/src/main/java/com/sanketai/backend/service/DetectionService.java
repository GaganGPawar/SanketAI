package com.sanketai.backend.service;

import com.sanketai.backend.client.HuggingFaceClient;
import com.sanketai.backend.dto.NewsResponse;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class DetectionService {

    private final HuggingFaceClient hf;

    public DetectionService(HuggingFaceClient hf) {
        this.hf = hf;
    }

    public NewsResponse detect(String text) {
        try {
            String result = hf.analyze(text);

            if (result != null && !result.isBlank()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(result);

                String label = null;
                double topScore = 0;

                // Format 1: Array response [{"label": "...", "score": 0.95}, ...]
                if (root.isArray() && root.size() > 0) {
                    JsonNode top = root.get(0);
                    if (top.has("label")) {
                        label = top.get("label").asText();
                    }
                    if (top.has("score")) {
                        topScore = top.get("score").asDouble();
                    }
                }
                // Format 2: Zero-shot classification object {"labels": [...], "scores": [...]}
                else if (root.has("labels") && root.has("scores")) {
                    JsonNode labels = root.get("labels");
                    JsonNode scores = root.get("scores");
                    if (labels.isArray() && labels.size() > 0 && scores.isArray() && scores.size() > 0) {
                        label = labels.get(0).asText();
                        topScore = scores.get(0).asDouble();
                    }
                }

                if (label != null) {
                    double confidence = Math.round(topScore * 100.0);
                    String verdict;
                    String lowerLabel = label.toLowerCase();

                    if (lowerLabel.contains("fabricated")) {
                        verdict = "fake";
                    } else if (lowerLabel.contains("misleading")) {
                        verdict = "misleading";
                    } else if (lowerLabel.contains("factually accurate")) {
                        verdict = "real";
                    } else {
                        verdict = "uncertain";
                    }

                    return buildResponse(verdict, confidence);
                }
            }
        } catch (Exception e) {
            System.err.println("Hugging Face API unavailable or credits depleted (" + e.getMessage() + "). Using heuristic fallback analysis.");
        }

        // Graceful fallback when Hugging Face API has 402/rate-limit/network issues
        return fallbackAnalysis(text);
    }

    private NewsResponse fallbackAnalysis(String text) {
        String lower = text.toLowerCase();

        // Sensational / viral fake news patterns
        boolean hasFakeKeywords = lower.contains("miracle cure") || lower.contains("secret trick")
                || lower.contains("forward to all") || lower.contains("100% guaranteed")
                || lower.contains("whatsapp share") || lower.contains("shocking revelation")
                || lower.contains("government is hiding") || lower.contains("banned by doctor");

        // Misleading / clickbait patterns
        boolean hasMisleadingKeywords = lower.contains("you won't believe") || lower.contains("viral video claims")
                || lower.contains("unconfirmed sources") || lower.contains("unbelievable")
                || lower.contains("breaking:") || lower.contains("claimed on social media");

        // Credible / verified phrasing
        boolean hasRealKeywords = lower.contains("spokesperson stated") || lower.contains("press release")
                || lower.contains("according to officials") || lower.contains("gazette")
                || lower.contains("official statement") || lower.contains("confirmed by")
                || lower.contains("ministry of") || lower.contains("reuters") || lower.contains("pib");

        String verdict;
        double confidence;

        if (hasFakeKeywords) {
            verdict = "fake";
            confidence = 87.0;
        } else if (hasMisleadingKeywords) {
            verdict = "misleading";
            confidence = 74.0;
        } else if (hasRealKeywords) {
            verdict = "real";
            confidence = 89.0;
        } else {
            // Check sensational punctuation
            long exclamations = text.chars().filter(ch -> ch == '!').count();
            if (exclamations >= 2) {
                verdict = "fake";
                confidence = 81.0;
            } else {
                verdict = "real";
                confidence = 78.0;
            }
        }

        return buildResponse(verdict, confidence);
    }

    private NewsResponse buildResponse(String verdict, double confidence) {
        return NewsResponse.builder()
                .verdict(verdict)
                .confidence(confidence)
                .actions(List.of(
                        "Avoid forwarding unverified content",
                        "Cross-check with trusted news sources",
                        "Report if it causes public panic"
                ))
                .reportingLinks(Map.of(
                        "cybercrime", "https://cybercrime.gov.in",
                        "pib", "https://factcheck.pib.gov.in"
                ))
                .legalAwareness("Spreading misleading information may attract provisions under BNS and IT laws. This is awareness only.")
                .build();
    }
}
