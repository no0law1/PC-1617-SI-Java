package serieum;

import org.junit.Before;
import org.junit.Test;
import serieum.SynchronousThreadPoolExecutor;

import java.util.LinkedList;
import java.util.Queue;

import static org.junit.Assert.*;

public class SynchronousThreadPoolExecutorTest {

    private static final int KEEP_ALIVE_TIME = 500_000_000; // 500 milliseconds

    private SynchronousThreadPoolExecutor<String> executor;
    private Queue<Exception> exceptionQueue;

    @Before
    public void setUp(){
        this.executor = new SynchronousThreadPoolExecutor<>(3, KEEP_ALIVE_TIME);
        this.exceptionQueue = new LinkedList<>();
    }

    @Test
    public void execute() throws Exception {
        assertEquals("Hello World", executor.execute(() -> "Hello World"));
    }

    @Test(expected = Exception.class)
    public void executeCallableWithException() throws Exception {
        assertEquals("Hello World", executor.execute(() -> {
            throw new Exception();
        }));
    }

    @Test
    public void executeSeveralCalls() throws Exception {
        final int[] executed = {0};
        executor = new SynchronousThreadPoolExecutor<>(5, KEEP_ALIVE_TIME);
        LinkedList<Thread> threads = new LinkedList<>();
        for (int i = 0; i < 100; i++) {
            threads.add(new Thread(() -> {
                try {
                    assertEquals("Hello World", executor.execute(() -> {
                        executed[0]++;
                        return "Hello World";
                    }));
                } catch (Exception e) {
                    exceptionQueue.add(e);
                }
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach((thread) -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                exceptionQueue.add(e);
            }
        });

        assertEquals(100, executed[0]);
        assertEquals(0, exceptionQueue.size());
    }

    @Test(expected = IllegalStateException.class)
    public void exceptionThrownAfterShutdown() throws Exception {
        executor.shutdown();

        executor.execute(() -> "Hello World");
    }

    @Test()
    public void shutdownWaitsForWorkToFinish() throws Exception {
        final boolean[] isInside = {false};
        new Thread(() -> {
            try {
                assertEquals("Hello World", executor.execute(() -> {
                    isInside[0] = true;
                    Thread.sleep(500);
                    return "Hello World";
                }));
            } catch (Exception e) {
                exceptionQueue.add(e);
            }
        }).start();

        while (!isInside[0]);
        long past = System.currentTimeMillis();
        executor.shutdown();
        long now = System.currentTimeMillis();

        assertTrue((now - past) > 450);
        assertEquals(0, exceptionQueue.size());
    }

    @Test
    public void WorkerThreadIsPreferred() throws Exception {
        SynchronousThreadPoolExecutor<Long> executor = new SynchronousThreadPoolExecutor<>(2, KEEP_ALIVE_TIME);

        long id_a = executor.execute(() -> Thread.currentThread().getId());
        Thread.sleep(20);       //Guarantees thread used in a has finished
        long id_b = executor.execute(() -> Thread.currentThread().getId());

        assertEquals(id_a, id_b);
    }

    @Test
    public void WorkExecutesInDifferentThreads() throws Exception {
        SynchronousThreadPoolExecutor<Long> executor = new SynchronousThreadPoolExecutor<>(2, KEEP_ALIVE_TIME);

        final boolean[] inside = {false};
        final long[] id_a = new long[1];
        final long[] id_b = new long[1];
        Thread t1 = new Thread(() -> {
            try {
                id_a[0] = executor.execute(() -> {
                    inside[0] = true;
                    Thread.sleep(500);
                    return Thread.currentThread().getId();
                });
            } catch (Exception e) {
                exceptionQueue.add(e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                id_b[0] = executor.execute(() -> Thread.currentThread().getId());
            } catch (Exception e) {
                exceptionQueue.add(e);
            }
        });

        t1.start();
        while(!inside[0]);
        t2.start();
        t1.join();
        t2.join();

        assertNotEquals(id_a[0], id_b[0]);
    }
}