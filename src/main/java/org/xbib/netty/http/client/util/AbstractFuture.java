package org.xbib.netty.http.client.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * <p>
 * An abstract implementation of the {@link Future} interface.  This class
 * is an abstraction of {@link java.util.concurrent.FutureTask} to support use
 * for tasks other than {@link Runnable}s.  It uses an
 * {@link AbstractQueuedSynchronizer} to deal with concurrency issues and
 * guarantee thread safety.  It could be used as a base class to
 * {@code FutureTask}, or any other implementor of the {@code Future} interface.
 * </p>
 *
 * <p>
 * This class implements all methods in {@code Future}.  Subclasses should
 * provide a way to set the result of the computation through the protected
 * methods {@link #set(Object)}, {@link #setException(Exception)}, or
 * {@link #cancel()}.  If subclasses want to implement cancellation they can
 * override the {@link #cancel(boolean)} method with a real implementation, the
 * default implementation doesn't support cancellation.
 * </p>
 *
 * <p>
 * The state changing methods all return a boolean indicating success or
 * failure in changing the future's state.  Valid states are running,
 * completed, failed, or cancelled.  Because this class does not implement
 * cancellation it is left to the subclass to distinguish between created
 * and running tasks.
 * </p>
 *
 * <p>This class is taken from the Google Guava project.</p>
 *
 * @param <V> the future value parameter type
 */
public abstract class AbstractFuture<V> implements Future<V> {

    /**
     * Synchronization control.
     */
    private final Sync<V> sync = new Sync<>();

    /**
     * The default {@link AbstractFuture} implementation throws {@code
     * InterruptedException} if the current thread is interrupted before or during
     * the call, even if the value is already available.
     *
     * @throws InterruptedException  if the current thread was interrupted before
     *                               or during the call (optional but recommended).
     * @throws TimeoutException if operation timed out
     * @throws ExecutionException if execution fails
     */
    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException,
            TimeoutException, ExecutionException {
        return sync.get(unit.toNanos(timeout));
    }

    /**
     * The default {@link AbstractFuture} implementation throws {@code
     * InterruptedException} if the current thread is interrupted before or during
     * the call, even if the value is already available.
     *
     * @throws InterruptedException  if the current thread was interrupted before
     *                               or during the call (optional but recommended).
     * @throws ExecutionException if execution fails
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
        return sync.get();
    }

    /**
     * Checks if the sync is not in the running state.
     */
    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    /**
     * Checks if the sync is in the cancelled state.
     */
    @Override
    public boolean isCancelled() {
        return sync.isCancelled();
    }

    public boolean isSucceeded() {
        return sync.isSuccess();
    }

    public boolean isFailed() {
        return sync.isFailed();
    }

    /**
     * Default implementation of cancel that cancels the future.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (!sync.cancel()) {
            return false;
        }
        done();
        if (mayInterruptIfRunning) {
            interruptTask();
        }
        return true;
    }

    /**
     * Subclasses should invoke this method to set the result of the computation
     * to {@code value}.  This will set the state of the future to
     * {@link AbstractFuture.Sync#COMPLETED} and call {@link #done()} if the
     * state was successfully changed.
     *
     * @param value the value that was the result of the task.
     * @return true if the state was successfully changed.
     */
    protected boolean set(V value) {
        boolean result = sync.set(value);
        if (result) {
            done();
        }
        return result;
    }

    /**
     * Subclasses should invoke this method to set the result of the computation
     * to an error, {@code throwable}.  This will set the state of the future to
     * {@link AbstractFuture.Sync#COMPLETED} and call {@link #done()} if the
     * state was successfully changed.
     *
     * @param exception the exception that the task failed with.
     * @return true if the state was successfully changed.
     */
    protected boolean setException(Exception exception) {
        boolean result = sync.setException(exception);
        if (result) {
            done();
        }
        return result;
    }

    /**
     * Subclasses should invoke this method to mark the future as cancelled.
     * This will set the state of the future to {@link
     * AbstractFuture.Sync#CANCELLED} and call {@link #done()} if the state was
     * successfully changed.
     *
     * @return true if the state was successfully changed.
     */
    protected final boolean cancel() {
        boolean result = sync.cancel();
        if (result) {
            done();
        }
        return result;
    }

    /**
     * Called by the success, failed, or cancelled methods to indicate that the
     * value is now available and the latch can be released.  Subclasses can
     * use this method to deal with any actions that should be undertaken when
     * the task has completed.
     */
    protected void done() {
    }

    /**
     * Subclasses can override this method to implement interruption of the
     * future's computation. The method is invoked automatically by a successful
     * call to {@link #cancel(boolean) cancel(true)}.
     * The default implementation does nothing.
     */
    protected void interruptTask() {
    }

    /**
     * <p>
     * Following the contract of {@link AbstractQueuedSynchronizer} we create a
     * private subclass to hold the synchronizer.  This synchronizer is used to
     * implement the blocking and waiting calls as well as to handle state changes
     * in a thread-safe manner.  The current state of the future is held in the
     * Sync state, and the lock is released whenever the state changes to either
     * {@link #COMPLETED} or {@link #CANCELLED}.
     * </p>
     * <p>
     * To avoid races between threads doing release and acquire, we transition
     * to the final state in two steps.  One thread will successfully CAS from
     * RUNNING to COMPLETING, that thread will then set the result of the
     * computation, and only then transition to COMPLETED or CANCELLED.
     * </p>
     * <p>
     * We don't use the integer argument passed between acquire methods so we
     * pass around a -1 everywhere.
     * </p>
     */
    static final class Sync<V> extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = -796072460488712821L;

        static final int RUNNING = 0;
        static final int COMPLETING = 1;
        static final int COMPLETED = 2;
        static final int CANCELLED = 4;

        private V value;
        private Exception exception;

        /*
        * Acquisition succeeds if the future is done, otherwise it fails.
        */
        @Override
        protected int tryAcquireShared(int ignored) {
            return isDone() ? 1 : -1;
        }

        /*
        * We always allow a release to go through, this means the state has been
        * successfully changed and the result is available.
        */
        @Override
        protected boolean tryReleaseShared(int finalState) {
            setState(finalState);
            return true;
        }

        /**
         * Blocks until the task is complete or the timeout expires.  Throws a
         * {@link TimeoutException} if the timer expires, otherwise behaves like
         * {@link #get()}.
         */
        V get(long nanos) throws TimeoutException, CancellationException,
                ExecutionException, InterruptedException {
            // Attempt to acquire the shared lock with a timeout.
            if (!tryAcquireSharedNanos(-1, nanos)) {
                throw new TimeoutException("Timeout waiting for task.");
            }
            return getValue();
        }

        /**
         * Blocks until {@link #complete(Object, Exception, int)} has been
         * successfully called.  Throws a {@link CancellationException} if the task
         * was cancelled, or a {@link ExecutionException} if the task completed with
         * an error.
         */
        V get() throws CancellationException, ExecutionException,
                InterruptedException {
            // Acquire the shared lock allowing interruption.
            acquireSharedInterruptibly(-1);
            return getValue();
        }

        /**
         * Implementation of the actual value retrieval.  Will return the value
         * on success, an exception on failure, a cancellation on cancellation, or
         * an illegal state if the synchronizer is in an invalid state.
         */
        private V getValue() throws CancellationException, ExecutionException {
            int state = getState();
            switch (state) {
                case COMPLETED:
                    if (exception != null) {
                        throw new ExecutionException(exception);
                    } else {
                        return value;
                    }
                case CANCELLED:
                    throw new CancellationException("task was cancelled");
                default:
                    throw new IllegalStateException("error, synchronizer in invalid state: " + state);
            }
        }

        /**
         * Checks if the state is {@link #COMPLETED} or {@link #CANCELLED}.
         */
        boolean isDone() {
            return (getState() & (COMPLETED | CANCELLED)) != 0;
        }

        /**
         * Checks if the state is {@link #CANCELLED}.
         */
        boolean isCancelled() {
            return getState() == CANCELLED;
        }

        boolean isSuccess() {
            return value != null && getState() == COMPLETED;
        }

        boolean isFailed() {
            return exception != null && getState() == COMPLETED;
        }

        /**
         * Transition to the COMPLETED state and set the value.
         */
        boolean set(V v) {
            return complete(v, null, COMPLETED);
        }

        /**
         * Transition to the COMPLETED state and set the exception.
         */
        boolean setException(Exception exception) {
            return complete(null, exception, COMPLETED);
        }

        /**
         * Transition to the CANCELLED state.
         */
        boolean cancel() {
            return complete(null, null, CANCELLED);
        }

        /**
         * Implementation of completing a task.  Either {@code v} or {@code t} will
         * be set but not both.  The {@code finalState} is the state to change to
         * from {@link #RUNNING}.  If the state is not in the RUNNING state we
         * return {@code false} after waiting for the state to be set to a valid
         * final state ({@link #COMPLETED} or {@link #CANCELLED}).
         *
         * @param v the value to set as the result of the computation.
         * @param exception the exception to set as the result of the computation.
         * @param finalState the state to transition to.
         */
        private boolean complete(V v, Exception exception, int finalState) {
            boolean doCompletion = compareAndSetState(RUNNING, COMPLETING);
            if (doCompletion) {
                // If this thread successfully transitioned to COMPLETING, set the value
                // and exception and then release to the final state.
                this.value = v;
                this.exception = exception;
                releaseShared(finalState);
            } else if (getState() == COMPLETING) {
                // If some other thread is currently completing the future, block until
                // they are done so we can guarantee completion.
                acquireShared(-1);
            }
            return doCompletion;
        }
    }
}
