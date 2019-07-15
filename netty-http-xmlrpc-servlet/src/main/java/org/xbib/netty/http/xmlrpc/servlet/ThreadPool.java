package org.xbib.netty.http.xmlrpc.servlet;

import java.util.ArrayList;
import java.util.List;

/** Simple thread pool. A task is executed by obtaining a thread from
 * the pool
 */
public class ThreadPool {

    /** A task, which may be interrupted, if the pool is shutting down. 
     */
    public interface InterruptableTask extends Runnable {
        /** Interrupts the task.
         * @throws Throwable Shutting down the task failed.
         */
        void shutdown() throws Throwable;
    }

    private class Poolable extends Thread implements Runnable{

    	private volatile boolean shuttingDown;

        private Runnable task;

        Poolable(ThreadGroup pGroup, int pNum) {
			super(pGroup, pGroup.getName() + "-" + pNum);
			super.start();
		}

		@Override
		public void run() {
			while (!shuttingDown) {
				final Runnable t = getTask();
				if (t == null) {
					try {
						synchronized (this) {
							if (!shuttingDown  &&  getTask() == null) {
								wait();
							}
						}
					} catch (InterruptedException e) {
						// Do nothing
					}
				} else {
					try {
						t.run();
						resetTask();
						repool(Poolable.this);
					} catch (Throwable e) {
						remove(Poolable.this);
						Poolable.this.shutdown();
						resetTask();
					}
				}
			}
		}

        synchronized void shutdown() {
            shuttingDown = true;
            final Runnable t = getTask();
            if (t instanceof InterruptableTask) {
                try {
                    ((InterruptableTask) t).shutdown();
                } catch (Throwable th) {
                    // Ignore me
                }
            }
            task = null;
            synchronized (this) {
                super.notify();
            }
        }

        private Runnable getTask() {
            return task;
        }

        private void resetTask() {
            task = null;
        }

        void start(Runnable pTask) {
            task = pTask;
            synchronized (this) {
                super.notify();
            }
        }
    }

	private final ThreadGroup threadGroup;

    private final int maxSize;

    private final List<Runnable> waitingThreads = new ArrayList<>();

    private final List<Runnable> runningThreads = new ArrayList<>();

    private final List<Runnable> waitingTasks = new ArrayList<>();

    private int num;


	/** Creates a new instance.
	 * @param pMaxSize Maximum number of concurrent threads.
	 * @param pName Thread group name.
	 */
	public ThreadPool(int pMaxSize, String pName) {
		maxSize = pMaxSize;
		threadGroup = new ThreadGroup(pName);
	}

	private synchronized void remove(Poolable pPoolable) {
        runningThreads.remove(pPoolable);
        waitingThreads.remove(pPoolable);
	}

	private void repool(Poolable pPoolable) {
	    boolean discarding = false;
		Runnable task = null;
	    Poolable poolable = null;
	    synchronized (this) {
	        if (runningThreads.remove(pPoolable)) {
	            if (maxSize != 0  &&  runningThreads.size() + waitingThreads.size() >= maxSize) {
	                discarding = true;
	            } else {
	                waitingThreads.add(pPoolable);
	                if (waitingTasks.size() > 0) {
	                    task = waitingTasks.remove(waitingTasks.size() - 1);
	                    poolable = getPoolable(task, false);
	                }
	            }
	        } else {
	            discarding = true;
	        }
	        if (discarding) {
	            remove(pPoolable);
	        }
	    }
	    if (poolable != null) {
	        poolable.start(task);
	    }
	    if (discarding) {
	        pPoolable.shutdown();
	    }
	}

	/**
	 * Starts a task immediately.
	 * @param pTask The task being started.
	 * @return True, if the task could be started immediately. False, if
	 * the maxmimum number of concurrent tasks was exceeded.
	 */
	public boolean startTask(Runnable pTask) {
	    final Poolable poolable = getPoolable(pTask, false);
	    if (poolable == null) {
	        return false;
	    }
	    poolable.start(pTask);
		return true;
	}

	private synchronized Poolable getPoolable(Runnable pTask, boolean pQueue) {
        if (maxSize != 0  &&  runningThreads.size() >= maxSize) {
            if (pQueue) {
                waitingTasks.add(pTask);
            }
            return null;
        }
        Poolable poolable;
        if (waitingThreads.size() > 0) {
            poolable = (Poolable) waitingThreads.remove(waitingThreads.size()-1);
        } else {
            poolable = new Poolable(threadGroup, num++);
        }
        runningThreads.add(poolable);
        return poolable;
	}
	
	/**
	 * Adds a task for immediate or deferred execution.
	 * @param pTask The task being added.
	 * @return True, if the task was started immediately. False, if
	 * the task will be executed later.
	 * @deprecated No longer in use.
	 */
	@Deprecated
	public boolean addTask(Runnable pTask) {
	    final Poolable poolable = getPoolable(pTask, true);
	    if (poolable != null) {
	        poolable.start(pTask);
	        return true;
	    }
	    return false;
	}

	/** Closes the pool.
	 */
	public synchronized void shutdown() {
        while (!waitingThreads.isEmpty()) {
            Poolable poolable = (Poolable) waitingThreads.remove(waitingThreads.size()-1);
            poolable.shutdown();
        }
        while (!runningThreads.isEmpty()) {
            Poolable poolable = (Poolable) runningThreads.remove(runningThreads.size()-1);
            poolable.shutdown();
        }
	}

	/** Returns the maximum number of concurrent threads.
	 * @return Maximum number of threads.
	 */
	public int getMaxThreads() { return maxSize; }

	/** Returns the number of threads, which have actually been created,
     * as opposed to the number of currently running threads.
	 */
    public synchronized int getNumThreads() { return num; }
}
