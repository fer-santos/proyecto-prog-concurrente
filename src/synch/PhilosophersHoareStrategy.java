package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.PhilosophersSim;
import problemas.PhilosophersSim.State;

public class PhilosophersHoareStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition[] self = new Condition[PhilosophersSim.N];

    public PhilosophersHoareStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        for (int i = 0; i < PhilosophersSim.N; i++) {
            self[i] = lock.newCondition();
        }
        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> runPhilosopher(id), "PhilosophersHoare-" + id);
            threads[i].setDaemon(true);
            threads[i].start();
        }
    }

    private void runPhilosopher(int id) {
        final int leftFork = id;
        final int rightFork = (id + 1) % PhilosophersSim.N;
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.state[id] = State.THINKING;
                panel.chopstickOwner[leftFork] = -1;
                panel.chopstickOwner[rightFork] = -1;
                panel.updateGraphPhilosopherThinkingDemo(id, leftFork, rightFork);
                sleepRand(420, 860);
                if (!panel.running.get()) {
                    break;
                }

                lock.lockInterruptibly();
                try {
                    panel.state[id] = State.HUNGRY;
                    panel.updateGraphPhilosopherRequestingForkDemo(id, leftFork);
                    panel.updateGraphPhilosopherRequestingForkDemo(id, rightFork);
                    test(id);
                    while (panel.state[id] != State.EATING && panel.running.get()) {
                        self[id].await();
                    }
                    if (!panel.running.get()) {
                        break;
                    }
                    panel.chopstickOwner[leftFork] = id;
                    panel.chopstickOwner[rightFork] = id;
                    panel.updateGraphPhilosopherHoldingForkDemo(id, leftFork);
                    panel.updateGraphPhilosopherHoldingForkDemo(id, rightFork);
                    panel.updateGraphPhilosopherEatingDemo(id, leftFork, rightFork);
                } finally {
                    lock.unlock();
                }

                Thread.sleep(620);

                lock.lockInterruptibly();
                try {
                    panel.state[id] = State.THINKING;
                    panel.chopstickOwner[leftFork] = -1;
                    panel.chopstickOwner[rightFork] = -1;
                    panel.updateGraphPhilosopherReleasingForksDemo(id, leftFork, rightFork);
                    panel.updateGraphPhilosopherThinkingDemo(id, leftFork, rightFork);
                    test(leftNeighbor(id));
                    test(rightNeighbor(id));
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.lock();
            try {
                panel.state[id] = State.THINKING;
                panel.chopstickOwner[leftFork] = -1;
                panel.chopstickOwner[rightFork] = -1;
                panel.updateGraphPhilosopherReleasingForksDemo(id, leftFork, rightFork);
                self[id].signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private void test(int index) {
        int left = leftNeighbor(index);
        int right = rightNeighbor(index);
        if (panel.state[index] == State.HUNGRY && panel.state[left] != State.EATING && panel.state[right] != State.EATING) {
            panel.state[index] = State.EATING;
            self[index].signal();
        }
    }

    private int leftNeighbor(int index) {
        return (index + PhilosophersSim.N - 1) % PhilosophersSim.N;
    }

    private int rightNeighbor(int index) {
        return (index + 1) % PhilosophersSim.N;
    }

    @Override
    public void stop() {
        for (Thread t : threads) {
            if (t != null) {
                t.interrupt();
            }
        }
        lock.lock();
        try {
            for (Condition condition : self) {
                if (condition != null) {
                    condition.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void sleepRand(int min, int max) throws InterruptedException {
        Thread.sleep(min + (int) (Math.random() * (max - min + 1)));
    }
}
