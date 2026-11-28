package dev.bulwark.sentinel.eval;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Pure computation of an {@link EvalReport} from labeled detections. */
final class Evaluator {

    private Evaluator() {
    }

    static EvalReport evaluate(Collection<LabeledDetection> rows) {
        List<LabeledDetection> normal = rows.stream().filter(r -> r.attackType() == null).toList();
        Map<String, List<LabeledDetection>> byType = rows.stream()
                .filter(r -> r.attackType() != null)
                .collect(Collectors.groupingBy(LabeledDetection::attackType, TreeMap::new, Collectors.toList()));

        int falsePositives = flagged(normal);
        EvalReport.Totals normalTotals = new EvalReport.Totals(
                normal.size(),
                falsePositives,
                ratio(falsePositives, normal.size()),
                clients(normal).size(),
                clientsEverFlagged(normal));

        Map<String, EvalReport.AttackStats> attacks = new TreeMap<>();
        int truePositives = 0;
        int attackWindows = 0;
        for (Map.Entry<String, List<LabeledDetection>> entry : byType.entrySet()) {
            List<LabeledDetection> windows = entry.getValue();
            int tp = flagged(windows);
            truePositives += tp;
            attackWindows += windows.size();
            List<Double> delays = secondsToFirstFlag(windows);
            attacks.put(entry.getKey(), new EvalReport.AttackStats(
                    windows.size(),
                    tp,
                    ratio(tp, windows.size()),
                    ratio(tp, tp + falsePositives),
                    clients(windows).size(),
                    clientsEverFlagged(windows),
                    delays.isEmpty() ? null : delays.get(delays.size() / 2),
                    delays.isEmpty() ? null : delays.get(delays.size() - 1)));
        }

        EvalReport.Overall overall = new EvalReport.Overall(
                ratio(truePositives, truePositives + falsePositives),
                ratio(truePositives, attackWindows),
                normalTotals.fpr());
        return new EvalReport(normalTotals, attacks, overall);
    }

    /** {@code atk-credstuff-01} → {@code credstuff}; anything else → {@code null}. */
    static String attackType(String clientId, boolean isAttacker) {
        if (!isAttacker) {
            return null;
        }
        String[] parts = clientId.split("-");
        return parts.length >= 3 && parts[0].equals("atk") ? parts[1] : "unknown";
    }

    private static int flagged(List<LabeledDetection> rows) {
        return (int) rows.stream().filter(LabeledDetection::flagged).count();
    }

    private static Map<String, List<LabeledDetection>> clients(List<LabeledDetection> rows) {
        return rows.stream().collect(Collectors.groupingBy(LabeledDetection::clientId));
    }

    private static int clientsEverFlagged(List<LabeledDetection> rows) {
        return (int) clients(rows).values().stream()
                .filter(windows -> windows.stream().anyMatch(LabeledDetection::flagged))
                .count();
    }

    /** Per detected client, sorted ascending. */
    private static List<Double> secondsToFirstFlag(List<LabeledDetection> rows) {
        List<Double> delays = new ArrayList<>();
        for (List<LabeledDetection> windows : clients(rows).values()) {
            Instant firstWindow = null;
            Instant firstFlag = null;
            for (LabeledDetection w : windows) {
                if (firstWindow == null || w.windowStart().isBefore(firstWindow)) {
                    firstWindow = w.windowStart();
                }
                if (w.flagged() && (firstFlag == null || w.detectedAt().isBefore(firstFlag))) {
                    firstFlag = w.detectedAt();
                }
            }
            if (firstFlag != null) {
                delays.add(Duration.between(firstWindow, firstFlag).toMillis() / 1000.0);
            }
        }
        delays.sort(Double::compare);
        return delays;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
