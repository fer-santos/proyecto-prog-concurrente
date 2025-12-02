package synch;

import problemas.SyncMethod;
import problemas.VirtualAssistantsSim.AssistantAgent;

import java.util.ArrayDeque;
import java.util.Deque;

public class VirtualAssistantsMutexStrategy extends VirtualAssistantsBaseStrategy {

    private final Object monitor = new Object();
    private final Deque<AssistantAgent> highTokenQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> lowTokenQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> highSlotQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> lowSlotQueue = new ArrayDeque<>();
    private int availableTokens;
    private int availableSlots;

    public VirtualAssistantsMutexStrategy(int slots, int tokens) {
        super(slots, tokens);
    }

    @Override
    public void start() {
        super.start();
        synchronized (monitor) {
            availableSlots = this.slots;
            availableTokens = this.tokens;
            highTokenQueue.clear();
            lowTokenQueue.clear();
            highSlotQueue.clear();
            lowSlotQueue.clear();
            monitor.notifyAll();
        }
    }

    @Override
    public void stop() {
        super.stop();
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    @Override
    public SyncMethod getMethod() {
        return SyncMethod.MUTEX;
    }

    @Override
    public int acquirePriorityToken(AssistantAgent agent) throws InterruptedException {
        synchronized (monitor) {
            Deque<AssistantAgent> queue = agent.isHighPriority() ? highTokenQueue : lowTokenQueue;
            queue.addLast(agent);
            boolean queued = true;
            try {
                while (isRunning()) {
                    boolean eligible = agent.isHighPriority() ? highTokenQueue.peekFirst() == agent : highTokenQueue.isEmpty() && lowTokenQueue.peekFirst() == agent;
                    if (eligible && availableTokens > 0) {
                        availableTokens--;
                        queued = false;
                        queue.removeFirstOccurrence(agent);
                        return takeToken();
                    }
                    monitor.wait();
                }
            } finally {
                if (queued) {
                    queue.removeFirstOccurrence(agent);
                }
            }
        }
        throw new InterruptedException("Mutex strategy stopped");
    }

    @Override
    public int acquireServerSlot(AssistantAgent agent) throws InterruptedException {
        synchronized (monitor) {
            Deque<AssistantAgent> queue = agent.isHighPriority() ? highSlotQueue : lowSlotQueue;
            queue.addLast(agent);
            boolean queued = true;
            try {
                while (isRunning()) {
                    boolean eligible = agent.isHighPriority() ? highSlotQueue.peekFirst() == agent : highSlotQueue.isEmpty() && lowSlotQueue.peekFirst() == agent;
                    if (eligible && availableSlots > 0) {
                        availableSlots--;
                        queued = false;
                        queue.removeFirstOccurrence(agent);
                        return takeSlot();
                    }
                    monitor.wait();
                }
            } finally {
                if (queued) {
                    queue.removeFirstOccurrence(agent);
                }
            }
        }
        throw new InterruptedException("Mutex strategy stopped");
    }

    @Override
    public void releaseResources(AssistantAgent agent, int tokenIndex, int slotIndex) {
        synchronized (monitor) {
            if (tokenIndex >= 0) {
                availableTokens = Math.min(tokens, availableTokens + 1);
                releaseToken(tokenIndex);
            }
            if (slotIndex >= 0) {
                availableSlots = Math.min(slots, availableSlots + 1);
                releaseSlot(slotIndex);
            }
            monitor.notifyAll();
        }
    }
}
