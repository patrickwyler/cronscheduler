package ch.wyler;

/**
 * Kafka connect usually works by using streams or when it's not possible, it tries to poll at a very short interval.
 * <p>
 * In some cases we would like to not start the replication right away and have a little space between each runs. Either because the
 * process is CPU intensive, the other system has a rate limit or we're paying per request to the other system.
 */
public interface Scheduler {

    /**
     * Check if execution is allowed
     */
    boolean checkIfExecutionIsAllowed();

    /**
     * Block the current thread until the next execution is allowed
     *
     * @throws InterruptedException if thread got stopped
     */
    void waitUntilNextExecutionIsAllowed() throws InterruptedException;

    /**
     * Move to next execution time slot
     */
    void moveToNextTimeSlot();

    /**
     * Immediately stop waiting for the next execution
     */
    void stop();
}
