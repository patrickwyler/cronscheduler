package ch.wyler;

import static com.cronutils.model.CronType.QUARTZ;
import static com.cronutils.model.definition.CronDefinitionBuilder.instanceDefinitionFor;
import static com.cronutils.model.time.ExecutionTime.forCron;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.time.Duration.ZERO;
import static java.time.Duration.between;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDurationWords;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter(PRIVATE)
@Setter(PRIVATE)
public class CronScheduler implements Scheduler {

    private static final ZoneOffset TIMEZONE = UTC;
    private Duration maxWaitingTime;
    private ExecutionTime executionTime;
    private Thread thread;
    private ZonedDateTime lastStart;

    public CronScheduler(final String cronExpression) {
        this(cronExpression, ZERO);
    }

    public CronScheduler(final String cronExpression, final Duration maxWaitingTime) {
        final CronDefinition cronDefinition = instanceDefinitionFor(QUARTZ);
        final Cron cron = new CronParser(cronDefinition).parse(cronExpression).validate();
        setExecutionTime(forCron(cron));
        setLastStart(now(TIMEZONE));
        setMaxWaitingTime(maxWaitingTime);
    }

    @Override
    public boolean checkIfExecutionIsAllowed() {
        final Duration timeToWaitForExecution = calculateTimeToWait();
        log.debug("Check {}", timeToWaitForExecution.isZero());
        return timeToWaitForExecution.isZero();
    }

    @Override
    public void waitUntilNextExecutionIsAllowed() throws InterruptedException {
        setThread(currentThread());
        log.debug("Thread {}", getThread().getName());

        final Duration timeToWaitForExecution = calculateTimeToWait();

        if (timeToWaitForExecution.isZero()) {
            return; // no need to wait
        }

        try {
            log.debug("Sleep {}", timeToWaitForExecution.toMillis());
            sleep(timeToWaitForExecution.toMillis());
        } catch (final InterruptedException e) {
            log.info("Blocking thread has been interrupted.");
            throw e;
        }
    }

    @Override
    public void moveToNextTimeSlot() {
        setLastStart(now(TIMEZONE));
        setThread(null);
    }

    @Override
    public void stop() {
        if (getThread() == null) {
            log.debug("There is no thread to stop.");
            return;
        }

        getThread().interrupt();
        log.info("Thread has been interrupted.");
        setThread(null);
    }

    private Duration calculateTimeToWait() {
        final ZonedDateTime nextExecutionTime = getExecutionTime().nextExecution(getLastStart())
                .orElseThrow(() -> new RuntimeException(
                        "Unable to compute previous execution for task. Cron expression could be wrong."));

        Duration timeToWait = between(now(TIMEZONE), nextExecutionTime);
        if (timeToWait.isNegative() || timeToWait.isZero()) {
            log.debug("No waiting time. Next execution time: '{}'", nextExecutionTime);
            return ZERO;
        }

        if (!getMaxWaitingTime().isZero()) {
            final boolean isWaitingTimeTooLong = timeToWait.compareTo(getMaxWaitingTime()) >= 0;
            if (isWaitingTimeTooLong) {
                log.debug("Waiting time of {} is too long we will wait only {}", timeToWait, getMaxWaitingTime());
                timeToWait = getMaxWaitingTime();
            }
        }

        final String formattedTimeToWait = formatDurationWords(timeToWait.toMillis(), true, true);
        log.debug("Waiting time before next execution is {}. Next execution will be at '{}'", formattedTimeToWait,
                nextExecutionTime);
        return timeToWait;
    }
}
