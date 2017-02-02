package utils;

import java.util.concurrent.locks.Lock;

/**
 * Simple Lock class that implements AutoCloseable to simplify coding
 */
public class LockWrapper implements AutoCloseable {

    /**
     * The lock
     */
    private final Lock _lock;

    /**
     *
     * @param l the lock
     */
    public LockWrapper(Lock l) {
        this._lock = l;
        _lock.lock();
    }

    @Override
    public void close() throws Exception {
        this._lock.unlock();
    }
}
