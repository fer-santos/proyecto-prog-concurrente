package synch;

import problemas.SyncMethod;
import problemas.VirtualAssistantsSim.AssistantAgent;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualAssistantsSemaphoreStrategy extends VirtualAssistantsBaseStrategy {

    private final Semaphore tokenSemaphore;
    private final Semaphore slotSemaphore;
    private final AtomicInteger highTokenWaiting = new AtomicInteger(0);
    private final AtomicInteger highSlotWaiting = new AtomicInteger(0);

    public VirtualAssistantsSemaphoreStrategy(int slots, int tokens) {
        super(slots, tokens);
        this.tokenSemaphore = new Semaphore(tokens, true);
        this.slotSemaphore = new Semaphore(slots, true);
    }

    @Override
    public SyncMethod getMethod() {
        return SyncMethod.SEMAPHORES;
    }

    @Override
    public void start() {
        super.start();
        tokenSemaphore.drainPermits();
        slotSemaphore.drainPermits();
        tokenSemaphore.release(tokens);
        slotSemaphore.release(slots);
    }

    @Override
    public void stop() {
        super.stop();
        tokenSemaphore.release(tokens);
        slotSemaphore.release(slots);
    }

    @Override
    public int acquirePriorityToken(AssistantAgent agent) throws InterruptedException {
        while (isRunning()) {
            if (agent.isHighPriority()) {
                highTokenWaiting.incrementAndGet();
                try {
                    tokenSemaphore.acquire();
                    return takeToken();
                } finally {
                    highTokenWaiting.decrementAndGet();
                }
            }
            if (highTokenWaiting.get() > 0) {
                Thread.sleep(10);
                continue;
            }
            tokenSemaphore.acquire();
            return takeToken();
        }
        throw new InterruptedException("Semaphore strategy stopped");
    }

    @Override
    public int acquireServerSlot(AssistantAgent agent) throws InterruptedException {
        while (isRunning()) {
            if (agent.isHighPriority()) {
                highSlotWaiting.incrementAndGet();
                try {
                    slotSemaphore.acquire();
                    return takeSlot();
                } finally {
                    highSlotWaiting.decrementAndGet();
                }
            }
            if (highSlotWaiting.get() > 0) {
                Thread.sleep(10);
                continue;
            }
            slotSemaphore.acquire();
            return takeSlot();
        }
        throw new InterruptedException("Semaphore strategy stopped");
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
