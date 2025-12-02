package synch;

import problemas.SyncMethod;
import problemas.VirtualAssistantsSim.AssistantAgent;

import java.util.ArrayDeque;
import java.util.Deque;

public class VirtualAssistantsMonitorStrategy extends VirtualAssistantsBaseStrategy {

    private final Object monitor = new Object();
    private final Deque<AssistantAgent> highTokenQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> lowTokenQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> highSlotQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> lowSlotQueue = new ArrayDeque<>();
    private int tokensFree;
    private int slotsFree;
    private int consecutiveHighWins = 0;

    public VirtualAssistantsMonitorStrategy(int slots, int tokens) {
        super(slots, tokens);
    }

    @Override
    public void start() {
        super.start();
        synchronized (monitor) {
            tokensFree = tokens;
            slotsFree = slots;
            highTokenQueue.clear();
            lowTokenQueue.clear();
            highSlotQueue.clear();
            lowSlotQueue.clear();
            consecutiveHighWins = 0;
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
        return SyncMethod.MONITORS;
    }

    @Override
    public int acquirePriorityToken(AssistantAgent agent) throws InterruptedException {
        synchronized (monitor) {
            Deque<AssistantAgent> queue = agent.isHighPriority() ? highTokenQueue : lowTokenQueue;
            queue.addLast(agent);
            boolean dequeued = false;
            try {
                while (isRunning()) {
                    boolean eligible = eligibleForToken(agent);
                    if (eligible && tokensFree > 0) {
                        tokensFree--;
                        dequeued = true;
                        queue.removeFirstOccurrence(agent);
                        registerGrant(agent.isHighPriority());
                        return takeToken();
                    }
                    monitor.wait();
                }
            } finally {
                if (!dequeued) {
                    queue.removeFirstOccurrence(agent);
                }
            }
        }
        throw new InterruptedException("Monitor strategy stopped");
    }

    @Override
    public int acquireServerSlot(AssistantAgent agent) throws InterruptedException {
        synchronized (monitor) {
            Deque<AssistantAgent> queue = agent.isHighPriority() ? highSlotQueue : lowSlotQueue;
            queue.addLast(agent);
            boolean dequeued = false;
            try {
                while (isRunning()) {
                    boolean eligible = eligibleForSlot(agent);
                    if (eligible && slotsFree > 0) {
                        slotsFree--;
                        dequeued = true;
                        queue.removeFirstOccurrence(agent);
                        registerGrant(agent.isHighPriority());
                        return takeSlot();
                    }
                    monitor.wait();
                }
            } finally {
                if (!dequeued) {
                    queue.removeFirstOccurrence(agent);
                }
            }
        }
        throw new InterruptedException("Monitor strategy stopped");
    }

    @Override
    public void releaseResources(AssistantAgent agent, int tokenIndex, int slotIndex) {
        synchronized (monitor) {
            if (tokenIndex >= 0) {
                tokensFree = Math.min(tokens, tokensFree + 1);
                releaseToken(tokenIndex);
            }
            if (slotIndex >= 0) {
                slotsFree = Math.min(slots, slotsFree + 1);
                releaseSlot(slotIndex);
            }
            monitor.notifyAll();
        }
    }

    private boolean eligibleForToken(AssistantAgent agent) {
        if (agent.isHighPriority()) {
            return highTokenQueue.peekFirst() == agent;
        }
        return lowTokenQueue.peekFirst() == agent
            && (highTokenQueue.isEmpty() || consecutiveHighWins >= 2);
    }

    private boolean eligibleForSlot(AssistantAgent agent) {
        if (agent.isHighPriority()) {
            return highSlotQueue.peekFirst() == agent;
        }
        return lowSlotQueue.peekFirst() == agent
            && (highSlotQueue.isEmpty() || consecutiveHighWins >= 2);
    }

    private void registerGrant(boolean highPriority) {
        if (highPriority) {
            consecutiveHighWins++;
        } else {
            consecutiveHighWins = 0;
        }
    }
}
