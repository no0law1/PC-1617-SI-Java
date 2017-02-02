package seriedois; /***
 *
 *  ISEL, LEIC, Programação Concorrente, Inverno 2016/17
 *
 *	Carlos Martins, Pedro Félix
 *
 *  Codigo anexo ao exercício 3 da SE#2
 * 
 ***/

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

/***
 * Lock-free dualqueue
 * William N. Scherer III and Michael L. Scott
 *
 * from: http://www.cs.rochester.edu/research/synchronization/pseudocode/duals.html
 *

struct cptr {			// counted pointer
	qnode *ptr;
	int sn;
};  // 64-bit datatype

struct ctptr {	// counted tagged pointer
	qnode *31 ptr;
	bool is_request;        // tag describes pointed-to node
    int sn;
};	// 64-bit datatype

struct qnode {
	cval data;
	cptr request;
	ctptr next;
};

struct dualqueue {
	cptr head;
	ctptr tail;
};

void dq_init(dualqueue *Q)
{
	qnode *qn = new qnode;
	qn->next.ptr = NULL;
	Q->head.ptr = Q->tail.ptr = qn;
	Q->tail.is_request = FALSE;
}

void enqueue(int v, dualqueue *Q)
{
	qnode *n = new qnode;
	n->data = v;
	n->next.ptr = n->request.ptr = NULL;
	while (1) {
		ctptr tail = Q->tail;
        cptr head = Q->head;
		if (tail.ptr == head.ptr || !tail.is_request) {
			// queue empty, tail falling behind, or queue contains data
			// (queue could also contain exactly one outstanding request with
			// tail pointer as yet unswung)
			cptr next = tail.ptr->next;
			if (tail == Q->tail) {	// tail and next are consistent
				if (next.ptr != NULL) {	// tail falling behind
					cas(&Q->tail, tail, {{next.ptr, next.is_request}, tail.sn + 1});
				} else {	// try to link in the new node
					if (cas(&tail.ptr->next, next, {{n, FALSE}, next.sn + 1})) {
						cas(&Q->tail, tail, {{n, FALSE}, tail.sn + 1});
                        return;
					}
				}
			}
		} else {	// queue consists of requests
			ctptr next = head.ptr->next;
			if (tail == Q->tail) {	// tail has not changed
				cptr req = head.ptr->request;
				if (head == Q->head) {	// head, next, and req are consistent
					bool success = (req.ptr == NULL && cas(&head.ptr->request, req, {n, req.sn + 1}));
					// try to remove fulfilled request even if it's not mine
                    cas(&Q->head, head, {next.ptr, head.sn + 1});
                    if (success)
						return;
				}
			}
		}
	}
}

int dequeue(dualqueue *Q{, thread_id r})
{
	qnode *n = new qnode;
	n->is_request = TRUE;
	n->ptr = n->request = NULL;
	
	while (1) {
		cptr head  = Q->head;
		ctptr tail = Q->tail;
		if (tail.ptr == head.ptr || tail.is_request) {
			// queue empty, tail falling behind, or queue contains data (queue could also
			// contain exactly one outstanding request with tail pointer as yet unswung)
			cptr next = tail.ptr->next;
			if (tail == Q->tail) {		// tail and next are consistent
				if (next.ptr != NULL) {	// tail falling behind
					cas(&Q->tail, tail, {{next.ptr, next.is_request}, tail.sn+1});
				} else {	// try to link in a request for data
					if (cas(&tail.ptr->next, next, {{n, TRUE}, next.sn + 1})) {
						// linked in request; now try to swing tail pointer
                        cas(&Q->tail, tail, {{n, TRUE}, tail.sn + 1});

                        // help someone else if I need to
                        if (head == Q->head && head.ptr->request.ptr != NULL) {
							cas(&Q->head, head, {head.ptr->next.ptr, head.sn + 1});
						}
						
						// initial linearization point
						while (tail.ptr->request.ptr == NULL)
							;  // spin
                        // help snip my node
						head =  Q->head;
						if (head.ptr == tail.ptr) {
							cas(&Q->head, head, {n, head.sn + 1});
						}
						// data is now available; read it out and go home
						int result = tail.ptr->request.ptr->data;
                        delete tail.ptr->request.ptr;
						delete tail.ptr;
                        return result;
					}
				}
			}
		} else {    // queue consists of real data
			cptr next = head.ptr->next;
			if (tail == Q->tail) {
				// head and next are consistent; read result *before* swinging head
				int result = next.ptr->data;
                if (cas(&Q->head, head, {next.ptr, head.sn + 1})) {
                    delete head.ptr;
					delete n;
					return result;
				}
			}
		}
    }
}

***/

/*
 * Lock-free dualqueue
 * William N. Scherer III and Michael L. Scott
 */

public class LockFreeDualQueue_<T> {

	// types of queue nodes
	private enum NodeType { DATUM, REQUEST };
	
	// the queue node
	private static class QNode<T> {
		NodeType type;
		final T data;
		final AtomicReference<QNode<T>> request; 
		final AtomicReference<QNode<T>> next;
		
		//  build a datum or request node
		QNode(T d, NodeType t) {
			type = t;
			data = d;
			request = new AtomicReference<QNode<T>>(null);
			next = new AtomicReference<QNode<T>>(null);
		}
	}
	
	// the head and tail references
	private final AtomicReference<QNode<T>> head;
	private final AtomicReference<QNode<T>> tail;
	
	public LockFreeDualQueue_() {
		QNode<T> sentinel = new QNode<T>(null, NodeType.DATUM);
		head = new AtomicReference<QNode<T>>(sentinel);
		tail = new AtomicReference<QNode<T>>(sentinel);
	}
	
	// enqueue a datum	
	public void enqueue(T v) {}

	// dequeue a datum - spinning if necessary
	public T dequeue() throws InterruptedException {
		QNode<T> h, hnext, t, tnext, n = null;
		do {
			h = head.get();
			t = tail.get();
			
			if (t == h || t.type == NodeType.REQUEST) {
				// queue empty, tail falling behind, or queue contains data (queue could also
				// contain exactly one outstanding request with tail pointer as yet unswung)
				tnext = t.next.get();
				
				if (t == tail.get()) {		// tail and next are consistent
					if (tnext != null) {	// tail falling behind
						tail.compareAndSet(t, tnext);
					} else {	// try to link in a request for data
						if (n == null) {
							n = new QNode<T>(null, NodeType.REQUEST);
						}
						if (t.next.compareAndSet(null, n)) {
							// linked in request; now try to swing tail pointer
                        	tail.compareAndSet(t, n);
                        
							// help someone else if I need to
                        	if (h == head.get() && h.request.get() != null) {
								head.compareAndSet(h, h.next.get());							
							}
						
							// busy waiting for a data done.
							// we use sleep instead od yield in order to accept interrupts
							while (t.request.get() == null) {
								Thread.sleep(0);  // spin accepting interrupts!!!
							}
							
                        	// help snip my node
							h = head.get();
							if (h == t) {
								head.compareAndSet(h, n);
							}
							
							// data is now available; read it out and go home
							return t.request.get().data;
						}
					}
				}
			} else {    // queue consists of real data
				hnext = h.next.get();
				if (t == tail.get()) {
					// head and next are consistent; read result *before* swinging head
					T result = hnext.data;
                	if (head.compareAndSet(h, hnext)) {
						return result;
					}
				}
			}
		} while (true);
    }
	
	public boolean isEmpty() {
		return true;
	}

	/*
	 + Test code
	 */
		
	// Auxiliary methods
	
	private static void sleepUninterruptibly(long milliseconds) {
		long expiresAt = System.currentTimeMillis() + milliseconds;
		do {
			try {
				Thread.sleep(milliseconds);
				break;
			} catch (InterruptedException ie) {}
			milliseconds = expiresAt - System.currentTimeMillis();
		} while (milliseconds > 0);
	}
	
	private static boolean joinUninterruptibly(Thread toJoin, long timeout) {
		do {
			try {
				toJoin.join(timeout);
				return !toJoin.isAlive();
			} catch (InterruptedException ie) {}
		} while (true);
	}
	
	//
	// Test dual queue to drive in a producer/consumer context.
	//

	public static boolean testLockFreeDualQueue() {
	
		final int CONSUMER_THREADS = 2;
		final int PRODUCER_THREADS = 1;
		final int MAX_PRODUCE_INTERVAL = 25;
		final int MAX_CONSUME_TIME = 25;
		final int FAILURE_PERCENT = 5;
		final int JOIN_TIMEOUT = 100;
		final int RUN_TIME = 10 * 1000;
		final int POLL_INTERVAL = 20;
		
		
		Thread[] consumers = new Thread[CONSUMER_THREADS];
		Thread[] producers = new Thread[PRODUCER_THREADS];
		final LockFreeDualQueue_<String> dqueue = new LockFreeDualQueue_<String>();
		final int[] productions = new int[PRODUCER_THREADS];
		final int[] consumptions = new int[CONSUMER_THREADS];
		final int[] failuresInjected = new int[PRODUCER_THREADS];
		final int[] failuresDetected = new int[CONSUMER_THREADS];
		
		// create and start the consumer threads.	
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			final int tid = i;
			consumers[i] = new Thread(() -> {
				Random rnd = new Random(Thread.currentThread().getId());
				int count = 0;

				System.out.printf("-->c#%02d starts...%n", tid);
				do {
					try {
						String data = dqueue.dequeue();
						if (!data.equals("hello")) {
							failuresDetected[tid]++;
							System.out.printf("[f#%d]", tid);
						}
				
						if (++count % 100 == 0)
							System.out.printf("[c#%02d]", tid);

						// simulate the time needed to process the data.							
						Thread.sleep(rnd.nextInt(MAX_CONSUME_TIME + 1));
														
					} catch (InterruptedException ie) {
						//do {} while (tid == 0);
						break;
					}
				} while (true);
					
				// display consumer thread's results.
				System.out.printf("%n<--c#%02d exits, consumed: %d, failures: %d",
								  tid, count, failuresDetected[tid]);
				consumptions[tid] = count;
			});
			consumers[i].setDaemon(true);
			consumers[i].start();
		}

		// create and start the producer threads.
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			final int tid = i;
			producers[i] = new Thread( () -> {
				Random rnd = new Random(Thread.currentThread().getId());
				int count = 0;

				System.out.printf("-->p#%02d starts...%n", tid);
				do {
					String data;
							
					if (rnd.nextInt(100) >= FAILURE_PERCENT) {
						data = "hello";
					} else {
						data = "HELLO";
						failuresInjected[tid]++;
					}
					
					// enqueue a data item
					dqueue.enqueue(data);
							
					// Increment request count and periodically display the "alive" menssage.
					if (++count % 100 == 0)
						System.out.printf("[p#%02d]", tid);
							
					// production interval.
					try {
						Thread.sleep(rnd.nextInt(MAX_PRODUCE_INTERVAL));
					} catch (InterruptedException ie) {
						//do {} while (tid == 0);
						break;
					}
				} while (true);
				System.out.printf("%n<--p#%02d exits, produced: %d, failures: %d",
								  tid, count, failuresInjected[tid]);
				productions[tid] = count;
			});
			producers[i].setDaemon(true);			
			producers[i].start();
		}

		// run the test RUN_TIME milliseconds		
		sleepUninterruptibly(RUN_TIME);

		// interrupt all producer threads and wait for until each one finished. 
		int stillRunning = 0;
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			producers[i].interrupt();
			if (!joinUninterruptibly(producers[i], JOIN_TIMEOUT))
				stillRunning++;		
		}
		
		// wait until the queue is empty 
		while (!dqueue.isEmpty()) {
			sleepUninterruptibly(POLL_INTERVAL);
		}
		
		// Interrupt each consumer thread and wait for a while until each one finished.
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			consumers[i].interrupt();
			if (!joinUninterruptibly(consumers[i], JOIN_TIMEOUT))
				stillRunning++;
		}
		
		// If any thread failed to fisnish, something is wrong.	
		if (stillRunning > 0) {
			System.out.printf("%n<--*** failure: %d thread(s) did answer to interrupt%n", stillRunning);
			return false;
		}
				
		// Compute and display the results.
		
		long sumProductions = 0, sumFailuresInjected = 0;
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			sumProductions += productions[i];
			sumFailuresInjected += failuresInjected[i];
		}
		long sumConsumptions = 0, sumFailuresDetected = 0;
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			sumConsumptions += consumptions[i];
			sumFailuresDetected += failuresDetected[i];
		}
		System.out.printf("%n<-- successful: %d/%d, failed: %d/%d%n",
						  sumProductions, sumConsumptions, sumFailuresInjected, sumFailuresDetected);
						   
		return sumProductions == sumConsumptions && sumFailuresInjected == sumFailuresDetected;
	}
	
	public static void main(String[] args) {
		System.out.printf("%n--> Test lock free dual queue: %s%n",
				 		  (testLockFreeDualQueue() ? "passed" : "failed")); 
	}
}



