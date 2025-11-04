package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import problemas.PhilosophersSim;
import problemas.PhilosophersSim.State;

public class PhilosophersDeadlockStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    private final ReentrantLock[] forks = new ReentrantLock[PhilosophersSim.N];
    private CyclicBarrier barrier;
    private CyclicBarrier afterLeftBarrier;

    public PhilosophersDeadlockStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
    barrier = new CyclicBarrier(PhilosophersSim.N);
    afterLeftBarrier = new CyclicBarrier(PhilosophersSim.N);
        for (int i = 0; i < PhilosophersSim.N; i++) {
            forks[i] = new ReentrantLock(true);
        }
        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> runPhilosopher(id), "PhilosophersDeadlock-" + id);
            threads[i].setDaemon(true);
            threads[i].start();
        }
    }

    private void runPhilosopher(int id) {
        final int leftFork = id;
        final int rightFork = (id + 1) % PhilosophersSim.N;
        boolean holdsLeft = false;
        boolean holdsRight = false;
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

                panel.state[id] = State.HUNGRY;
                panel.updateGraphPhilosopherRequestingForkDemo(id, leftFork);
                panel.updateGraphPhilosopherRequestingForkDemo(id, rightFork);
                barrier.await();

                forks[leftFork].lockInterruptibly();
                holdsLeft = true;
                panel.chopstickOwner[leftFork] = id;
                panel.updateGraphPhilosopherHoldingForkDemo(id, leftFork);
                Thread.sleep(VISUALIZATION_DELAY);
                afterLeftBarrier.await();

                panel.updateGraphPhilosopherWaitingForkDemo(id, rightFork);
                forks[rightFork].lockInterruptibly();
                holdsRight = true;
                panel.chopstickOwner[rightFork] = id;
                panel.updateGraphPhilosopherHoldingForkDemo(id, rightFork);

                panel.state[id] = State.EATING;
                panel.updateGraphPhilosopherEatingDemo(id, leftFork, rightFork);
                Thread.sleep(600);

                panel.chopstickOwner[leftFork] = -1;
                panel.chopstickOwner[rightFork] = -1;
                panel.updateGraphPhilosopherReleasingForksDemo(id, leftFork, rightFork);
                holdsRight = false;
                holdsLeft = false;
                forks[rightFork].unlock();
                forks[leftFork].unlock();
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (holdsRight && forks[rightFork].isHeldByCurrentThread()) {
                forks[rightFork].unlock();
            }
            if (holdsLeft && forks[leftFork].isHeldByCurrentThread()) {
                forks[leftFork].unlock();
            }
            panel.chopstickOwner[leftFork] = -1;
            panel.chopstickOwner[rightFork] = -1;
            panel.updateGraphPhilosopherReleasingForksDemo(id, leftFork, rightFork);
        }
    }

    @Override
    public void stop() {
        for (Thread t : threads) {
            if (t != null) {
                t.interrupt();
            }
        }
    }

    private void sleepRand(int min, int max) throws InterruptedException {
        Thread.sleep(min + (int) (Math.random() * (max - min + 1)));
    }
}
