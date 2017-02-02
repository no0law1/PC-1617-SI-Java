package serieum;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SynchronousThreadPoolExecutor<T> {

    /**
     * Auxiliary class of a callable method
     */
    private class ToCall {
        Callable<T> method;
        T result;
        Exception exception;
        Condition condition;

        ToCall(Callable<T> method, Condition condition) {
            this.method = method;
            this.condition = condition;
        }

        boolean hasResult(){
            return result != null || exception != null;
        }

        T getResult() throws Exception {
            if(result != null){
                return result;
            }
            throw exception;
        }
    }

    private HashMap<Long, Thread> threadPool;

    private LinkedList<ToCall> methods;

    /**
     * Number of threads working
     */
    private int workingThreads;

    /**
     * Maximum number of existing threads
     */
    private int maxPoolSize;

    /**
     * Maximum time a worker thread can be inactive
     */
    private final long keepAliveTime;

    /**
     * Synchronous Thread Pool Executor Lock
     */
    private final Lock lock;

    /**
     * Condition to wake up sleeping threads to work
     */
    private final Condition waiterCondition;

    private Condition shutdown;

    /**
     *
     * @param maxPoolSize Maximum number of worker threads
     * @param keepAliveTime Maximum time a worker thread can be inactive
     */
    public SynchronousThreadPoolExecutor(int maxPoolSize, int keepAliveTime){
        this.workingThreads = 0;
        this.lock = new ReentrantLock();
        this.waiterCondition = lock.newCondition();
        this.maxPoolSize = maxPoolSize;
        this.threadPool = new HashMap<>();
        this.methods = new LinkedList<>();
        this.keepAliveTime = keepAliveTime;
    }

    public T execute(Callable<T> toCall) throws Exception {
        lock.lock();
        try {
            if (shutdown != null) {
                throw new IllegalStateException();
            }
            Condition condition = lock.newCondition();
            ToCall tc = new ToCall(toCall, condition);
            methods.addLast(tc);

            if (workingThreads < threadPool.size()) {
                this.waiterCondition.signalAll();
            } else if (threadPool.size() < maxPoolSize) {
                Thread thread = new Thread(this::work);
                threadPool.put(thread.getId(), thread);
                thread.start();
            }

            try {
                while (!tc.hasResult()) //Spurious awakes. does not work with contains. we do poll in worker thread
                    condition.await();
            } catch (InterruptedException e) {
                methods.remove(tc);
                throw e;
            }

            return tc.getResult();
        } finally {
            if (shutdown != null && methods.isEmpty()) {
                shutdown.signal();
            }
            lock.unlock();
        }
    }

    public void shutdown(){
        lock.lock();
        try {
            shutdown = lock.newCondition();
            while(!methods.isEmpty() || workingThreads > 0)
                shutdown.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void work() {
        long nanos = keepAliveTime;
        lock.lock();
        try{
            do {
                ToCall toCall;
                while ((toCall = methods.poll()) != null){
                    workingThreads++;
                    try {
                        lock.unlock();
                        toCall.result = toCall.method.call();
                    } catch (Exception ex) {
                        toCall.exception = ex;
                    } finally {
                        lock.lock();
                        toCall.condition.signal();
                        workingThreads--;
                    }
                }

                if(nanos <= 0 || shutdown != null){
                    return;
                }

                try {
                    nanos = this.waiterCondition.awaitNanos(nanos);
                } catch (InterruptedException ex){
                    return;
                }
            } while (true);
        } finally {
            threadPool.remove(Thread.currentThread().getId());
            lock.unlock();
        }
    }
}
