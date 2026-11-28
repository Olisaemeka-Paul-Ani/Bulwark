package dev.bulwark.sentinel.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static LabeledDetection row(String client, boolean attacker, int window, boolean flagged) {
        Instant start = T0.plusSeconds(30L * window);
        return new LabeledDetection(client, Evaluator.attackType(client, attacker), start, start.plusSeconds(35), flagged);
    }

    @Test
    void parsesAttackTypeOnlyForAttackers() {
        assertThat(Evaluator.attackType("atk-credstuff-01", true)).isEqualTo("credstuff");
        assertThat(Evaluator.attackType("atk-credstuff-01", false)).isNull();
        assertThat(Evaluator.attackType("weird", true)).isEqualTo("unknown");
    }

    @Test
    void computesWindowAndClientMetrics() {
        EvalReport report = Evaluator.evaluate(List.of(
                // normal: 10 windows, 1 false positive, from norm-002
                row("norm-001", false, 0, false), row("norm-001", false, 1, false), row("norm-001", false, 2, false),
                row("norm-001", false, 3, false), row("norm-001", false, 4, false),
                row("norm-002", false, 0, false), row("norm-002", false, 1, true), row("norm-002", false, 2, false),
                row("norm-002", false, 3, false), row("norm-002", false, 4, false),
                // burst: 4 windows, 3 flagged; first flag in window 1
                row("atk-burst-01", true, 0, false), row("atk-burst-01", true, 1, true),
                row("atk-burst-01", true, 2, true), row("atk-burst-01", true, 3, true),
                // lowslow: 2 windows, none flagged
                row("atk-lowslow-01", true, 0, false), row("atk-lowslow-01", true, 1, false)));

        assertThat(report.normal().windows()).isEqualTo(10);
        assertThat(report.normal().fpr()).isCloseTo(0.1, within(1e-9));
        assertThat(report.normal().clientsEverFlagged()).isEqualTo(1);

        EvalReport.AttackStats burst = report.attacks().get("burst");
        assertThat(burst.recall()).isCloseTo(0.75, within(1e-9));
        assertThat(burst.precision()).isCloseTo(0.75, within(1e-9)); // 3 TP vs 1 FP
        assertThat(burst.clientsDetected()).isEqualTo(1);
        assertThat(burst.secondsToFirstQuarantineMedian()).isEqualTo(65.0); // window 1 start + 35s

        EvalReport.AttackStats lowslow = report.attacks().get("lowslow");
        assertThat(lowslow.recall()).isZero();
        assertThat(lowslow.secondsToFirstQuarantineMedian()).isNull();

        assertThat(report.overall().recall()).isCloseTo(3 / 6.0, within(1e-9));
        assertThat(report.overall().precision()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void emptyInputGivesZeros() {
        EvalReport report = Evaluator.evaluate(List.of());

        assertThat(report.attacks()).isEmpty();
        assertThat(report.overall().precision()).isZero();
        assertThat(report.normal().fpr()).isZero();
    }
}
