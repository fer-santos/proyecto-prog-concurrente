package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore; 
import problemas.PhilosophersSim;
import problemas.PhilosophersSim.State;










public class PhilosophersBarrierStrategy implements SynchronizationStrategy {

    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];


    private CyclicBarrier barrier;


    private Semaphore[] forks;
    private static final long VISUALIZATION_DELAY = 420L;

    public PhilosophersBarrierStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(PhilosophersSim.N); 
        forks = new Semaphore[PhilosophersSim.N];
        for (int i = 0; i < PhilosophersSim.N; i++) {
            forks[i] = new Semaphore(1); 
        }

        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                final int leftFork = id;
                final int rightFork = (id + 1) % PhilosophersSim.N;
                boolean holdsLeft = false;
                boolean holdsRight = false;
                try {
                    while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.state[id] = State.THINKING;
                        panel.updateGraphPhilosopherThinkingBarrier(id, leftFork, rightFork);
                        panel.chopstickOwner[leftFork] = -1;
                        panel.chopstickOwner[rightFork] = -1;
                        sleepRand(400, 1000);

                        if (!panel.running.get()) {
                            break;
                        }

                        panel.state[id] = State.HUNGRY;
                        panel.updateGraphPhilosopherWaitingBarrier(id);
                        Thread.sleep(VISUALIZATION_DELAY);
                        barrier.await();
                        panel.updateGraphPhilosopherReleasedBarrier(id);
                        Thread.sleep(VISUALIZATION_DELAY);

                        int firstFork = (id % 2 == 0) ? leftFork : rightFork;
                        int secondFork = (id % 2 == 0) ? rightFork : leftFork;

                        panel.updateGraphPhilosopherRequestingForkBarrier(id, firstFork);
                        Thread.sleep(VISUALIZATION_DELAY);
                        forks[firstFork].acquire();
                        if (firstFork == leftFork) {
                            holdsLeft = true;
                            panel.chopstickOwner[leftFork] = id;
                        } else {
                            holdsRight = true;
                            panel.chopstickOwner[rightFork] = id;
                        }
                        panel.updateGraphPhilosopherHoldingForkBarrier(id, firstFork);
                        Thread.sleep(VISUALIZATION_DELAY);

                        panel.updateGraphPhilosopherRequestingForkBarrier(id, secondFork);
                        Thread.sleep(VISUALIZATION_DELAY);
                        forks[secondFork].acquire();
                        if (secondFork == leftFork) {
                            holdsLeft = true;
                            panel.chopstickOwner[leftFork] = id;
                        } else {
                            holdsRight = true;
                            panel.chopstickOwner[rightFork] = id;
                        }
                        panel.updateGraphPhilosopherHoldingForkBarrier(id, secondFork);
                        panel.state[id] = State.EATING;
                        panel.updateGraphPhilosopherEatingBarrier(id, leftFork, rightFork);
                        sleepRand(500, 900);

                        panel.chopstickOwner[leftFork] = -1;
                        panel.chopstickOwner[rightFork] = -1;
                        panel.updateGraphPhilosopherReleasingBarrier(id, leftFork, rightFork);
                        Thread.sleep(VISUALIZATION_DELAY);
                        if (holdsRight) {
                            forks[rightFork].release();
                            holdsRight = false;
                        }
                        if (holdsLeft) {
                            forks[leftFork].release();
                            holdsLeft = false;
                        }
                        panel.state[id] = State.THINKING;

                        panel.updateGraphPhilosopherWaitingBarrier(id);
                        Thread.sleep(VISUALIZATION_DELAY);
                        barrier.await();
                        panel.updateGraphPhilosopherReleasedBarrier(id);
                        Thread.sleep(VISUALIZATION_DELAY);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (holdsRight) {
                        forks[rightFork].release();
                    }
                    if (holdsLeft) {
                        forks[leftFork].release();
                    }
                    cleanupForks(id);
                    panel.updateGraphPhilosopherReleasingBarrier(id, leftFork, rightFork);
                }
            }, "Philosopher-Barrier-" + id);
            threads[i].setDaemon(true);
            threads[i].start();
        }
    }


    private void cleanupForks(int id) {
        int leftFork = id;
        int rightFork = (id + 1) % PhilosophersSim.N;
        
        if (forks[leftFork].availablePermits() == 0 && panel.chopstickOwner[leftFork] == id) {
            panel.chopstickOwner[leftFork] = -1;
            forks[leftFork].release();
        }
        if (forks[rightFork].availablePermits() == 0 && panel.chopstickOwner[rightFork] == id) {
            panel.chopstickOwner[rightFork] = -1;
            forks[rightFork].release();
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
        Thread.sleep(min + (int) (Math.random() * (max - min)));
    }
}
