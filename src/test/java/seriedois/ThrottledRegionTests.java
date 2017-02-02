package seriedois;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.*;

public class ThrottledRegionTests {

    public ThrottledRegion_ region;
    public Queue<Exception> exceptionQueue;

    public final int ID1 = 1;
    public final int ID2 = 2;

    @Before
    public void setUp() {
        region = new ThrottledRegion_(2, 2, 3600);
        exceptionQueue = new LinkedList<>();
    }

    public void EnterRegionSuccessfully() {
        try {
            if (!region.tryEnter(ID1)) {
                fail();
            }
        } catch (InterruptedException e) {
            exceptionQueue.add(e);
        }
    }

    public void CannotEnterRegion() {
        try {
            if (region.tryEnter(ID1)) {
                fail();
            }
        } catch (InterruptedException e) {
            exceptionQueue.add(e);
        }
    }

    @Test
    public void SimpleThrottledRegionTest() throws Exception {
        Thread t1 = new Thread(this::EnterRegionSuccessfully);
        Thread t2 = new Thread(this::EnterRegionSuccessfully);
        Thread t3 = new Thread(this::EnterRegionSuccessfully);

        t1.start();
        t2.start();
        t1.join();
        t2.join();
        region.leave(ID1);
        t3.start();
        t3.join();

        assertEquals(0, exceptionQueue.size());
    }

    @Test
    public void SimpleThrottledRegionFailByTimeoutTest() throws Exception {
        Thread t1 = new Thread(this::EnterRegionSuccessfully);
        Thread t2 = new Thread(this::EnterRegionSuccessfully);
        Thread t3 = new Thread(() -> {
            try {
                long past = System.currentTimeMillis();
                if (region.tryEnter(ID1)) {
                    fail();
                }
                long now = System.currentTimeMillis();

                assertTrue((now - past) > 3600);
            } catch (InterruptedException e) {
                exceptionQueue.add(e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
        t3.start();
        t3.join();
        assertEquals(0, exceptionQueue.size());
    }

    @Test
    public void SimpleThrottledRegionFailByMaxWaitingTest() throws Exception {
        region = new ThrottledRegion_(2, 2, 100000);
        Thread t1 = new Thread(this::EnterRegionSuccessfully);
        Thread t2 = new Thread(this::EnterRegionSuccessfully);
        Thread t3 = new Thread(this::EnterRegionSuccessfully);
        Thread t4 = new Thread(this::EnterRegionSuccessfully);
        Thread t5 = new Thread(() ->
        {
            try {
                long past = System.currentTimeMillis();
                if (region.tryEnter(ID1)) {
                    fail();
                }
                long now = System.currentTimeMillis();

                assertTrue((now - past) < 3600);
            } catch (InterruptedException e) {
                exceptionQueue.add(e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
        t3.start();
        t4.start();
        Thread.sleep(1000);
        t5.start();
        t5.join();  // Assert.Fail()
        region.leave(ID1);
        region.leave(ID1);
        t3.join();
        t4.join();
        assertEquals(0, exceptionQueue.size());
    }

    @Test
    public void InterruptedThreadThrowsTIETest() throws Exception {
        assertTrue(region.tryEnter(ID1));
        assertTrue(region.tryEnter(ID1));

        Thread t = new Thread(() -> {
            try {
                region.tryEnter(ID1);
            } catch (InterruptedException e) {
                exceptionQueue.add(e);
            }
        });

        t.start();
        t.interrupt();
        t.join();

        assertEquals(1, exceptionQueue.size());
        assertEquals(InterruptedException.class, exceptionQueue.poll().getClass());
    }

    @Test
    public void MultipleThreadsInsideForDifferentKeysTest() throws Exception {
        region = new ThrottledRegion_(1, 1, 100000);

        List<Thread> threads = new LinkedList<>();

        // add 2 threads for key 1 assert both enter
        // add 2 threads for key 2 assert both enter
        for (int i = 0; i < 2; i++) {
            threads.add(new Thread(() -> {
                try {
                    assertTrue(region.tryEnter(1));
                    Thread.sleep(100);
                } catch (Exception e) {
                    exceptionQueue.add(e);
                } finally {
                    region.leave(1);
                }
            }));
            threads.add(new Thread(() -> { // should not wait and enter immediately
                try {
                    assertTrue(region.tryEnter(2));
                    Thread.sleep(100);
                } catch (Exception e) {
                    exceptionQueue.add(e);
                } finally {
                    region.leave(2);
                }
            }));
        }

        threads.forEach(Thread::start);
        Thread.sleep(500);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                exceptionQueue.add(e);
            }
        });

        assertEquals(0, exceptionQueue.size());
    }
}
