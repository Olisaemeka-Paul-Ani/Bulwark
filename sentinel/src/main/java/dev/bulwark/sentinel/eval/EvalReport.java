package dev.bulwark.sentinel.eval;

import java.util.Map;

/**
 * Detection quality over a time range.
 *
 * <p>Window-level metrics treat each scored 30s window as one prediction. Precision for an attack
 * type uses that type's true positives against all false positives (false positives have no
 * type), so per-type precision is lower than overall precision when several types run at once.
 */
public record EvalReport(
        Totals normal,
        Map<String, AttackStats> attacks,
        Overall overall) {

    /** Normal traffic: how often it was wrongly flagged. */
    public record Totals(int windows, int flaggedWindows, double fpr, int clients, int clientsEverFlagged) {
    }

    /**
     * One attack type.
     *
     * @param secondsToFirstQuarantineMedian from the start of an attacker's first scored window to
     *        its first flagged detection; an upper bound, since the attack may begin partway through
     *        that window
     */
    public record AttackStats(
            int windows,
            int flaggedWindows,
            double recall,
            double precision,
            int clients,
            int clientsDetected,
            Double secondsToFirstQuarantineMedian,
            Double secondsToFirstQuarantineMax) {
    }

    public record Overall(double precision, double recall, double fpr) {
    }
}
