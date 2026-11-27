package dev.bulwark.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import dev.bulwark.gateway.ratelimit.TokenBucket.Decision;
import dev.bulwark.gateway.ratelimit.TokenBucket.State;

class TokenBucketTest {

    private static final double CAPACITY = 20;
    private static final double RATE = 10; // tokens per second
    private static final long T0 = 1_000_000L;

    @Test
    void refillAddsTokensProportionalToElapsedTime() {
        assertThat(TokenBucket.refill(0, T0, T0 + 500, CAPACITY, RATE)).isCloseTo(5.0, within(1e-9));
        assertThat(TokenBucket.refill(2.5, T0, T0 + 100, CAPACITY, RATE)).isCloseTo(3.5, within(1e-9));
    }

    @Test
    void refillIsCappedAtCapacity() {
        assertThat(TokenBucket.refill(15, T0, T0 + 60_000, CAPACITY, RATE)).isEqualTo(CAPACITY);
    }

    @Test
    void refillIgnoresClockGoingBackwards() {
        assertThat(TokenBucket.refill(3, T0, T0 - 5_000, CAPACITY, RATE)).isEqualTo(3);
    }

    @Test
    void newBucketStartsFullAndTakesOne() {
        Decision d = TokenBucket.tryTake(null, T0, CAPACITY, RATE);

        assertThat(d.allowed()).isTrue();
        assertThat(d.state().tokens()).isEqualTo(CAPACITY - 1);
        assertThat(d.state().lastRefillMs()).isEqualTo(T0);
        assertThat(d.remaining()).isEqualTo(19);
        assertThat(d.retryAfterMs()).isZero();
    }

    @Test
    void burstUpToCapacityThenDenied() {
        State state = null;
        for (int i = 0; i < CAPACITY; i++) {
            Decision d = TokenBucket.tryTake(state, T0, CAPACITY, RATE);
            assertThat(d.allowed()).as("request %d", i + 1).isTrue();
            state = d.state();
        }

        Decision denied = TokenBucket.tryTake(state, T0, CAPACITY, RATE);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterMs()).isEqualTo(100); // one token at 10/s
        assertThat(denied.state().tokens()).isEqualTo(0);
    }

    @Test
    void deniedRequestDoesNotConsumeTokens() {
        State empty = new State(0.4, T0);

        Decision denied = TokenBucket.tryTake(empty, T0, CAPACITY, RATE);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.state().tokens()).isCloseTo(0.4, within(1e-9));
        assertThat(denied.retryAfterMs()).isEqualTo(60); // 0.6 tokens at 10/s
    }

    @Test
    void emptyBucketRecoversAfterRefill() {
        State empty = new State(0, T0);

        assertThat(TokenBucket.tryTake(empty, T0 + 99, CAPACITY, RATE).allowed()).isFalse();
        Decision afterRefill = TokenBucket.tryTake(empty, T0 + 100, CAPACITY, RATE);
        assertThat(afterRefill.allowed()).isTrue();
        assertThat(afterRefill.state().tokens()).isCloseTo(0, within(1e-9));
        assertThat(afterRefill.state().lastRefillMs()).isEqualTo(T0 + 100);
    }

    @Test
    void sustainedRateAtRefillRateIsAlwaysAllowed() {
        State state = new State(0, T0);
        for (int i = 1; i <= 100; i++) {
            Decision d = TokenBucket.tryTake(state, T0 + i * 100L, CAPACITY, RATE);
            assertThat(d.allowed()).as("request %d", i).isTrue();
            state = d.state();
        }
    }

    @Test
    void backwardsClockKeepsLatestTimestamp() {
        Decision d = TokenBucket.tryTake(new State(5, T0), T0 - 1_000, CAPACITY, RATE);

        assertThat(d.allowed()).isTrue();
        assertThat(d.state().tokens()).isEqualTo(4);
        assertThat(d.state().lastRefillMs()).isEqualTo(T0);
    }
}
