package ch.wyler;

import static java.lang.Thread.sleep;
import static java.time.Duration.ofSeconds;
import static java.time.LocalDateTime.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CronSchedulerTest {

    private static final String CRON_EXPRESSION_EVERY_SEC = "* * * * * ?";
    private static final String CRON_EXPRESSION_EVERY_MIN = "0 * * * * ?";

    @Test
    void testCronValidation() {
        assertThatThrownBy(() -> new CronScheduler("hello")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CronScheduler("******")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CronScheduler("* a * * * *")).isInstanceOf(IllegalArgumentException.class);
        assertThatNoException().isThrownBy(() -> new CronScheduler("* * * * * ? *"));
        assertThatNoException().isThrownBy(() -> new CronScheduler(CRON_EXPRESSION_EVERY_SEC));
        assertThatNoException().isThrownBy(() -> new CronScheduler(CRON_EXPRESSION_EVERY_MIN));
    }

    @Test
    void testWaitingTime() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_SEC);

        // Act
        scheduler.waitUntilNextExecutionIsAllowed();
        final LocalDateTime firstRun = now();
        scheduler.moveToNextTimeSlot();
        scheduler.waitUntilNextExecutionIsAllowed();
        final LocalDateTime secondRun = now();

        // Assert
        assertThat(MILLIS.between(firstRun, secondRun)).isBetween(1000L, 1100L);
    }

    @Test
    void testMaxWaitingTime() throws InterruptedException {
        // Arrange
        final Duration maxWaitingTime = ofSeconds(2);
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_MIN, maxWaitingTime);

        // Act
        final LocalDateTime firstRun = now();
        scheduler.waitUntilNextExecutionIsAllowed();
        final LocalDateTime secondRun = now();

        // Assert
        assertThat(MILLIS.between(firstRun, secondRun)).isBetween(2000L, 2200L);
    }

    @Test
    void testCheckIfExecutionIsAllowed() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_SEC);

        // Act
        final boolean firstCheck = scheduler.checkIfExecutionIsAllowed();
        scheduler.waitUntilNextExecutionIsAllowed();
        final boolean secondCheck = scheduler.checkIfExecutionIsAllowed();
        scheduler.moveToNextTimeSlot();
        final boolean thirdCheck = scheduler.checkIfExecutionIsAllowed();

        // Assert
        assertThat(firstCheck).isFalse();
        assertThat(secondCheck).isTrue();
        assertThat(thirdCheck).isFalse();
    }

    @Test
    void testMoveToNextTimeSlot() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_SEC);

        // Act
        final boolean firstCheck = scheduler.checkIfExecutionIsAllowed();
        scheduler.waitUntilNextExecutionIsAllowed();
        scheduler.moveToNextTimeSlot();
        final boolean secondCheck = scheduler.checkIfExecutionIsAllowed();

        // Assert
        assertThat(firstCheck).isFalse();
        assertThat(secondCheck).isFalse();
    }

    @Test
    void testStop() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_SEC);
        final boolean[] interrupted = {false};

        final Thread thread = new Thread(() -> {
            while (true) {
                log.debug("Run");
                try {
                    scheduler.waitUntilNextExecutionIsAllowed();
                } catch (final InterruptedException e) {
                    interrupted[0] = true;
                    return;
                }
            }
        });

        // Act
        thread.start();
        sleep(200L);
        scheduler.stop(); // try to stop thread
        sleep(100L);

        // Assert
        assertThat(interrupted[0]).isTrue();
    }

    @BeforeEach
    void preventTestingIssue() throws InterruptedException {
        final int second = now().getSecond();
        if (second > 55) {
            sleep((60 - second) * 1000);
        }
    }
}
