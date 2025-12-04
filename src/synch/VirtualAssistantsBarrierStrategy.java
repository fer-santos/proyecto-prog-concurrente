package synch;

import problemas.SyncMethod;
import problemas.VirtualAssistantsSim.AssistantAgent;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualAssistantsBarrierStrategy extends VirtualAssistantsBaseStrategy {

    private final Semaphore tokenSemaphore;
    private final Semaphore slotSemaphore;
    private final CyclicBarrier barrier;
    private final AtomicInteger highBatchPending = new AtomicInteger(0);

    public VirtualAssistantsBarrierStrategy(int slots, int tokens) {
        super(slots, tokens);
        this.tokenSemaphore = new Semaphore(tokens, true);
        this.slotSemaphore = new Semaphore(slots, true);
        int parties = Math.max(2, Math.min(slots, tokens));
        this.barrier = new CyclicBarrier(parties);
    }

    @Override
    public SyncMethod getMethod() {
        return SyncMethod.BARRIERS;
    }

    @Override
    public void start() {
        super.start();
        tokenSemaphore.drainPermits();
        slotSemaphore.drainPermits();
        tokenSemaphore.release(tokens);
        slotSemaphore.release(slots);
        barrier.reset();
    }

    @Override
    public void stop() {
        super.stop();
        barrier.reset();
        tokenSemaphore.release(tokens);
        slotSemaphore.release(slots);
    }

    @Override
    public int acquirePriorityToken(AssistantAgent agent) throws InterruptedException {
        if (agent.isHighPriority()) {
            highBatchPending.incrementAndGet();
            try {
                tokenSemaphore.acquire();
                return takeToken();
            } finally {
                highBatchPending.decrementAndGet();
            }
        }
        while (highBatchPending.get() > 0 && isRunning()) {
            Thread.sleep(8);
        }
        tokenSemaphore.acquire();
        return takeToken();
    }

    @Override
    public int acquireServerSlot(AssistantAgent agent) throws InterruptedException {
        awaitBarrier();
        slotSemaphore.acquire();
        return takeSlot();
    }

    private void awaitBarrier() throws InterruptedException {
        while (isRunning()) {
            try {
                barrier.await();
                return;
            } catch (BrokenBarrierException ex) {
                if (!isRunning()) {
                    throw new InterruptedException("Barrier stopped");
                }
            }
        }
        throw new InterruptedException("Barrier stopped");
    }

    @Override
    public void releaseResources(AssistantAgent agent, int tokenIndex, int slotIndex) {
        if (tokenIndex >= 0) {
            releaseToken(tokenIndex);
            tokenSemaphore.release();
        }
        if (slotIndex >= 0) {
            releaseSlot(slotIndex);
            slotSemaphore.release();
        }
    }
}
