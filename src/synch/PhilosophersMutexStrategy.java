package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.PhilosophersSim;

public class PhilosophersMutexStrategy implements SynchronizationStrategy {
    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    private ReentrantLock diningMutex;
    private static final long VISUALIZATION_DELAY = 420L;

    public PhilosophersMutexStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }
    
    private void sleepRand(int min, int max) throws InterruptedException {
        Thread.sleep(min + (int) (Math.random() * (max - min)));
    }

    @Override
    public void start() {
        diningMutex = new ReentrantLock(true);
        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                try {
                    while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.state[id] = PhilosophersSim.State.THINKING;
                        panel.updateGraphPhilosopherReleasingLock(id);
                        sleepRand(400, 1000);

                        if (!panel.running.get()) {
                            break;
                        }

                        panel.state[id] = PhilosophersSim.State.HUNGRY;
                        panel.updateGraphPhilosopherRequestingLock(id);
                        Thread.sleep(VISUALIZATION_DELAY);

                        int left = id;
                        int right = (id + 1) % PhilosophersSim.N;

                        diningMutex.lockInterruptibly();
                        boolean locked = true;
                        try {
                            panel.updateGraphPhilosopherHoldingLock(id);
                            Thread.sleep(VISUALIZATION_DELAY);

                            panel.chopstickOwner[left] = id;
                            panel.chopstickOwner[right] = id;
                            panel.state[id] = PhilosophersSim.State.EATING;
                            sleepRand(500, 900);
                            panel.chopstickOwner[left] = -1;
                            panel.chopstickOwner[right] = -1;
                        } finally {
                            if (locked) {
                                panel.updateGraphPhilosopherReleasingLock(id);
                                diningMutex.unlock();
                                locked = false;
                            }
                        }

                        sleepRand(200, 500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    panel.updateGraphPhilosopherReleasingLock(id);
                }
            }, "Philosopher-Mutex-" + id);
            threads[i].setDaemon(true);
            threads[i].start();
        }
    }

    @Override
    public void stop() {
        for (Thread t : threads) {
            if (t != null) t.interrupt();
        }
    }
}