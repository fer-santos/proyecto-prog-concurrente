package synch;

import java.util.concurrent.Semaphore;
import problemas.PhilosophersSim;

public class PhilosophersSemaphoreStrategy implements SynchronizationStrategy {
    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    private Semaphore[] forks;
    private Semaphore waiter;
    private static final long VISUALIZATION_DELAY = 420L;

    public PhilosophersSemaphoreStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }

    private void sleepRand(int min, int max) throws InterruptedException {
        Thread.sleep(min + (int) (Math.random() * (max - min)));
    }
    
    @Override
    public void start() {
        forks = new Semaphore[PhilosophersSim.N];
        for(int i=0; i<PhilosophersSim.N; i++) forks[i] = new Semaphore(1, true);
        waiter = new Semaphore(PhilosophersSim.N - 1, true);

        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                final int left = id;
                final int right = (id + 1) % PhilosophersSim.N;
                boolean waiterAcquired = false;
                boolean leftAcquired = false;
                boolean rightAcquired = false;
                try {
                    while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.state[id] = PhilosophersSim.State.THINKING;
                        panel.updateGraphPhilosopherReleasingSemaphore(id, left, right);
                        sleepRand(400, 1000);

                        if (!panel.running.get()) {
                            break;
                        }

                        panel.state[id] = PhilosophersSim.State.HUNGRY;
                        panel.updateGraphPhilosopherRequestingWaiter(id);
                        Thread.sleep(VISUALIZATION_DELAY);

                        waiter.acquire();
                        waiterAcquired = true;
                        panel.updateGraphPhilosopherGrantedWaiter(id);
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphPhilosopherRequestingFork(id, left);
                        Thread.sleep(VISUALIZATION_DELAY);
                        forks[left].acquire();
                        leftAcquired = true;
                        panel.chopstickOwner[left] = id;
                        panel.updateGraphPhilosopherHoldingFork(id, left);
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphPhilosopherRequestingFork(id, right);
                        Thread.sleep(VISUALIZATION_DELAY);
                        forks[right].acquire();
                        rightAcquired = true;
                        panel.chopstickOwner[right] = id;
                        panel.updateGraphPhilosopherHoldingFork(id, right);
                        panel.state[id] = PhilosophersSim.State.EATING;
                        panel.updateGraphPhilosopherEatingSemaphore(id, left, right);
                        sleepRand(500, 900);

                        panel.chopstickOwner[left] = -1;
                        panel.chopstickOwner[right] = -1;
                        panel.updateGraphPhilosopherReleasingSemaphore(id, left, right);
                        Thread.sleep(VISUALIZATION_DELAY);

                        if (rightAcquired) {
                            forks[right].release();
                            rightAcquired = false;
                        }
                        if (leftAcquired) {
                            forks[left].release();
                            leftAcquired = false;
                        }
                        if (waiterAcquired) {
                            waiter.release();
                            waiterAcquired = false;
                        }

                        Thread.sleep(Math.max(140L, VISUALIZATION_DELAY / 2));
                        sleepRand(200, 500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (rightAcquired) {
                        forks[right].release();
                    }
                    if (leftAcquired) {
                        forks[left].release();
                    }
                    if (waiterAcquired) {
                        waiter.release();
                    }
                    panel.chopstickOwner[left] = -1;
                    panel.chopstickOwner[right] = -1;
                    panel.updateGraphPhilosopherReleasingSemaphore(id, left, right);
                }
            }, "Philosopher-Sem-" + id);
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