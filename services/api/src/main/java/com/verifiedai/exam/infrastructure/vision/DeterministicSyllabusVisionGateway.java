package com.verifiedai.exam.infrastructure.vision;

import com.verifiedai.exam.application.SyllabusVisionGateway;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusExtractionCandidate;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusVisionResult;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic test and offline fallback adapter ($0.00 LLM spend).
 * Inspects embedded PDF text directives (%TOPIC: label | WEEK: n | MONTH: name)
 * or exam context so tests and local/offline modes run deterministically.
 */
@Component
@ConditionalOnProperty(
        name = "verifiedai.exam.syllabus-live-ai-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DeterministicSyllabusVisionGateway implements SyllabusVisionGateway {

    @Override
    public SyllabusVisionResult extractScheduleItems(
            byte[] mediaBytes,
            String detectedMimeType,
            String filenameHint,
            ExamDefinition pinnedExam
    ) {
        String hintLower = filenameHint == null ? "" : filenameHint.toLowerCase(Locale.ROOT);
        String textProbe = new String(mediaBytes, 0, Math.min(mediaBytes.length, 4096), StandardCharsets.UTF_8);

        List<SyllabusExtractionCandidate> directiveItems = parseExplicitDirectives(textProbe);
        if (!directiveItems.isEmpty()) {
            return new SyllabusVisionResult(directiveItems, 0, "DETERMINISTIC_DIRECTIVE");
        }

        if (hintLower.contains("ambiguous")
                || hintLower.contains("belirsiz")
                || textProbe.contains("AMBIGUOUS_SYLLABUS")) {
            return new SyllabusVisionResult(
                    List.of(
                            new SyllabusExtractionCandidate(
                                    "Tam Sayılarla İşlemler",
                                    1,
                                    "October",
                                    new BigDecimal("0.9400")
                            ),
                            new SyllabusExtractionCandidate(
                                    "Genel Karma Tekrar ve Hızlandırma Programı",
                                    2,
                                    "November",
                                    new BigDecimal("0.5800")
                            )
                    ),
                    0,
                    "DETERMINISTIC_AMBIGUOUS"
            );
        }

        String examCode = pinnedExam.examCode();
        List<SyllabusExtractionCandidate> defaults = switch (examCode) {
            case "TR_YKS_TYT_MATH" -> List.of(
                    new SyllabusExtractionCandidate("Tam Sayılarla İşlemler", 1, "October", new BigDecimal("0.9600")),
                    new SyllabusExtractionCandidate("Birinci Dereceden Denklemler", 2, "October", new BigDecimal("0.9300")),
                    new SyllabusExtractionCandidate("Üslü Sayılar", 3, "November", new BigDecimal("0.9100"))
            );
            case "TR_YKS_AYT_MATH" -> List.of(
                    new SyllabusExtractionCandidate("İkinci Dereceden Denklemler", 1, "October", new BigDecimal("0.9500")),
                    new SyllabusExtractionCandidate("Fonksiyonlar", 2, "October", new BigDecimal("0.9400")),
                    new SyllabusExtractionCandidate("Türev", 3, "November", new BigDecimal("0.9200"))
            );
            case "INTL_AP_CALC_BC", "INTL_IB_MATH_AA_HL" -> List.of(
                    new SyllabusExtractionCandidate("Limits and Continuity", 1, "September", new BigDecimal("0.9500")),
                    new SyllabusExtractionCandidate("Derivatives", 2, "October", new BigDecimal("0.9400")),
                    new SyllabusExtractionCandidate("Integrals", 3, "November", new BigDecimal("0.9300"))
            );
            default -> List.of(
                    new SyllabusExtractionCandidate("Order of Operations", 1, "September", new BigDecimal("0.9500")),
                    new SyllabusExtractionCandidate("Linear Equations", 2, "October", new BigDecimal("0.9300")),
                    new SyllabusExtractionCandidate("Quadratic Equations", 3, "November", new BigDecimal("0.9200"))
            );
        };

        return new SyllabusVisionResult(defaults, 0, "DETERMINISTIC_TEMPLATE");
    }

    private static List<SyllabusExtractionCandidate> parseExplicitDirectives(String text) {
        List<SyllabusExtractionCandidate> items = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        int fallbackWeek = 1;
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (!trimmed.startsWith("%TOPIC:")) {
                continue;
            }
            String body = trimmed.substring("%TOPIC:".length()).trim();
            String[] segments = body.split("\\|");
            String label = segments[0].trim();
            if (label.isBlank()) {
                continue;
            }
            Integer week = fallbackWeek;
            String month = "October";
            BigDecimal confidence = new BigDecimal("0.9000");
            for (int i = 1; i < segments.length; i++) {
                String seg = segments[i].trim();
                if (seg.toUpperCase(Locale.ROOT).startsWith("WEEK:")) {
                    try {
                        week = Integer.parseInt(seg.substring(5).trim());
                    } catch (NumberFormatException ignored) {
                        week = fallbackWeek;
                    }
                } else if (seg.toUpperCase(Locale.ROOT).startsWith("MONTH:")) {
                    String m = seg.substring(6).trim();
                    if (!m.isBlank()) {
                        month = m;
                    }
                } else if (seg.toUpperCase(Locale.ROOT).startsWith("CONF:")) {
                    try {
                        confidence = new BigDecimal(seg.substring(5).trim());
                    } catch (NumberFormatException ignored) {
                        confidence = new BigDecimal("0.9000");
                    }
                }
            }
            items.add(new SyllabusExtractionCandidate(label, week, month, confidence));
            fallbackWeek++;
        }
        return List.copyOf(items);
    }
}
