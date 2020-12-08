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

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CronSchedulerTest {

    private static final String CRON_EXPRESSION_EVERY_FIVE_SEC = "0/5 * * * * ?";
    private static final String CRON_EXPRESSION_EVERY_MIN = "0 0/1 * * * ?";

    @Test
    public void testCronValidation() {
        // Act & Assert
        assertThatThrownBy(() -> new CronScheduler("hello")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CronScheduler("******")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CronScheduler("* a * * * *")).isInstanceOf(IllegalArgumentException.class);
        assertThatNoException().isThrownBy(() -> new CronScheduler("* * * * * ? *"));
        assertThatNoException().isThrownBy(() -> new CronScheduler(CRON_EXPRESSION_EVERY_MIN));
        assertThatNoException().isThrownBy(() -> new CronScheduler(CRON_EXPRESSION_EVERY_FIVE_SEC));
    }

    @Test
    public void testWaitingTime() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_FIVE_SEC);

        // Act
        scheduler.waitUntilNextExecutionIsAllowed();
        final LocalDateTime firstRun = now();
        scheduler.moveToNextTimeSlot();
        scheduler.waitUntilNextExecutionIsAllowed();
        final LocalDateTime secondRun = now();

        // Assert
        final int sec = getSecondsBetween(firstRun, secondRun);
        assertThat(sec).isEqualTo(5);
    }

    @Test
    public void testMaxWaitingTime() throws InterruptedException {
        // Arrange
        final Duration MAX_WAITING_TIME = ofSeconds(2);
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_MIN, MAX_WAITING_TIME);

        // Act
        final LocalDateTime firstRun = now();
        scheduler.waitUntilNextExecutionIsAllowed();
        final LocalDateTime secondRun = now();

        // Assert
        final int sec = getSecondsBetween(firstRun, secondRun);
        assertThat(sec).isEqualTo(2);
    }

    @Test
    public void testCheckIfExecutionIsAllowed() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_FIVE_SEC);

        // Act
        final boolean firstCheck = scheduler.checkIfExecutionIsAllowed();
        scheduler.waitUntilNextExecutionIsAllowed();
        sleep(25L);
        final boolean secondCheck = scheduler.checkIfExecutionIsAllowed();
        scheduler.moveToNextTimeSlot();
        final boolean thirdCheck = scheduler.checkIfExecutionIsAllowed();

        // Assert
        assertThat(firstCheck).isFalse();
        assertThat(secondCheck).isTrue();
        assertThat(thirdCheck).isFalse();
    }

    @Test
    public void testMoveToNextTimeSlot() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_FIVE_SEC);

        // Act
        final boolean firstCheck = scheduler.checkIfExecutionIsAllowed();
        scheduler.waitUntilNextExecutionIsAllowed();
        sleep(25L);
        scheduler.moveToNextTimeSlot();
        final boolean secondCheck = scheduler.checkIfExecutionIsAllowed();

        // Assert
        assertThat(firstCheck).isFalse();
        assertThat(secondCheck).isFalse();
    }

    @Test
    public void testStop() throws InterruptedException {
        // Arrange
        final CronScheduler scheduler = new CronScheduler(CRON_EXPRESSION_EVERY_MIN);
        final boolean[] interrupted = {false};

        final Thread thread = new Thread(() -> {
            while (true) {
                log.debug("Run");
                try {
                    scheduler.waitUntilNextExecutionIsAllowed();
                } catch (final InterruptedException e) {
                    interrupted[0] = true;
                }
            }
        });

        // Act
        thread.start();
        sleep(25L);
        scheduler.stop(); // try to stop thread
        sleep(25L);

        // Assert
        assertThat(interrupted[0]).isTrue();
    }

    private int getSecondsBetween(final LocalDateTime startTime, final LocalDateTime endTime) {
        final int millis = (int) MILLIS.between(startTime, endTime);
        return ((millis + 500) / 1000); // correct rounding up/down to next second
    }
}
