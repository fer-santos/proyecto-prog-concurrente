package synch;

import problemas.SyncMethod;
import problemas.VirtualAssistantsSim.AssistantAgent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class VirtualAssistantsConditionStrategy extends VirtualAssistantsBaseStrategy {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition tokenCondition = lock.newCondition();
    private final Condition slotCondition = lock.newCondition();
    private final Deque<AssistantAgent> highTokenQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> lowTokenQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> highSlotQueue = new ArrayDeque<>();
    private final Deque<AssistantAgent> lowSlotQueue = new ArrayDeque<>();
    private int tokensFree;
    private int slotsFree;

    public VirtualAssistantsConditionStrategy(int slots, int tokens) {
        super(slots, tokens);
    }

    @Override
    public void start() {
        super.start();
        lock.lock();
        try {
            tokensFree = tokens;
            slotsFree = slots;
            highTokenQueue.clear();
            lowTokenQueue.clear();
            highSlotQueue.clear();
            lowSlotQueue.clear();
            tokenCondition.signalAll();
            slotCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        super.stop();
        lock.lock();
        try {
            tokenCondition.signalAll();
            slotCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SyncMethod getMethod() {
        return SyncMethod.VAR_COND;
    }

    @Override
    public int acquirePriorityToken(AssistantAgent agent) throws InterruptedException {
        lock.lockInterruptibly();
        boolean dequeued = false;
        try {
            Deque<AssistantAgent> queue = agent.isHighPriority() ? highTokenQueue : lowTokenQueue;
            queue.addLast(agent);
            while (isRunning()) {
                boolean eligible = agent.isHighPriority() ? highTokenQueue.peekFirst() == agent : highTokenQueue.isEmpty() && lowTokenQueue.peekFirst() == agent;
                if (eligible && tokensFree > 0) {
                    tokensFree--;
                    dequeued = true;
                    queue.removeFirstOccurrence(agent);
                    return takeToken();
                }
                tokenCondition.await();
            }
            throw new InterruptedException("Condition strategy stopped");
        } finally {
            if (!dequeued) {
                Deque<AssistantAgent> queue = agent.isHighPriority() ? highTokenQueue : lowTokenQueue;
                queue.removeFirstOccurrence(agent);
            }
            lock.unlock();
        }
    }

    @Override
    public int acquireServerSlot(AssistantAgent agent) throws InterruptedException {
        lock.lockInterruptibly();
        boolean dequeued = false;
        try {
            Deque<AssistantAgent> queue = agent.isHighPriority() ? highSlotQueue : lowSlotQueue;
            queue.addLast(agent);
            while (isRunning()) {
                boolean eligible = agent.isHighPriority() ? highSlotQueue.peekFirst() == agent : highSlotQueue.isEmpty() && lowSlotQueue.peekFirst() == agent;
                if (eligible && slotsFree > 0) {
                    slotsFree--;
                    dequeued = true;
                    queue.removeFirstOccurrence(agent);
                    return takeSlot();
                }
                slotCondition.await();
            }
            throw new InterruptedException("Condition strategy stopped");
        } finally {
            if (!dequeued) {
                Deque<AssistantAgent> queue = agent.isHighPriority() ? highSlotQueue : lowSlotQueue;
                queue.removeFirstOccurrence(agent);
            }
            lock.unlock();
        }
    }

    @Override
    public void releaseResources(AssistantAgent agent, int tokenIndex, int slotIndex) {
        lock.lock();
        try {
            if (tokenIndex >= 0) {
                tokensFree = Math.min(tokens, tokensFree + 1);
                releaseToken(tokenIndex);
            }
            if (slotIndex >= 0) {
                slotsFree = Math.min(slots, slotsFree + 1);
                releaseSlot(slotIndex);
            }
            tokenCondition.signalAll();
            slotCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
