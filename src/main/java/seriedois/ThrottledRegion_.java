package seriedois; /***
 *
 *  ISEL, LEIC, Programação Concorrente, Inverno 2016/17
 *
 *	Carlos Martins, Pedro Felix
 *
 *  Codigo anexo ao exercício 2 da SE#2
 *
 ***/

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ThrottledRegion_ {

    private final int maxInside;

    private final int maxWaiting;

    private final long waitTimeout;

    private class ThrottledRegionForKey {

        private Lock lock;

        private Condition condition;

        private AtomicInteger maxInside;

        private volatile int maxWaiting;

        public ThrottledRegionForKey() {
            this.lock = new ReentrantLock();
            this.condition = lock.newCondition();
            this.maxInside = new AtomicInteger(ThrottledRegion_.this.maxInside);
            this.maxWaiting = ThrottledRegion_.this.maxWaiting;
        }

        public boolean tryEnter() throws InterruptedException {
            // Fast Path
            if(maxWaiting == ThrottledRegion_.this.maxWaiting && tryAcquire()){
                return true;
            }

            // Slow Path
            lock.lock();
            try {
                if(this.maxWaiting <= 0){   // Maxed out Waiters
                    return false;
                }

                this.maxWaiting--;

                if(tryAcquire()) {
                    this.maxWaiting++;
                    return true;
                }

                long nanos = ThrottledRegion_.this.waitTimeout;
                do {
                    try {
                        nanos = condition.awaitNanos(nanos);
                    } catch (InterruptedException e){
                        this.maxWaiting++;
                        condition.signal();
                        throw e;
                    }

                    if (tryAcquire()) {
                        this.maxWaiting++;
                        condition.signal();
                        return true;
                    }

                    if (nanos <= 0){
                        this.maxWaiting++;
                        condition.signal();
                        return false;
                    }
                } while (true);
            } finally {
                lock.unlock();
            }
        }

        public void leave() {
            do {
                int observed = maxInside.get();
                if (maxInside.compareAndSet(observed, observed + 1)) {
                    break;
                }
            } while (true);

            lock.lock();
            try {
                if (this.maxWaiting < ThrottledRegion_.this.maxWaiting) {
                    condition.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean tryAcquire() {
            do {
                int observed = maxInside.get();
                if (observed <= 0) {
                    return false;
                }
                if (maxInside.compareAndSet(observed, observed - 1)) {
                    return true;
                }
            } while (true);
        }
    }

    private final ConcurrentMap<Integer, ThrottledRegionForKey> keyToRegion = new ConcurrentHashMap<>();

    /**
     *
     * @param maxInside
     * @param maxWaiting
     * @param waitTimeout in milliseconds!!
     */
    public ThrottledRegion_(int maxInside, int maxWaiting, int waitTimeout) {
        this.maxInside = maxInside;
        this.maxWaiting = maxWaiting;
        this.waitTimeout = TimeUnit.MILLISECONDS.toNanos(waitTimeout);  // Hack. waitTimeout must be in millis
    }


    public boolean tryEnter(int key) throws InterruptedException {
        return keyToRegion.computeIfAbsent(key, k -> new ThrottledRegionForKey()).tryEnter();
    }

    public void leave(int key) {
        keyToRegion.get(key).leave();
    }
}
