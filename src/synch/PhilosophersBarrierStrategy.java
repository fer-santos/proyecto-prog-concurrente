package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore; // Necesitamos semáforos para los tenedores
import problemas.PhilosophersSim;
import problemas.PhilosophersSim.State;

/**
 * Implementación "forzada" de Barreras para la Cena de los Filósofos. Utiliza
 * CyclicBarrier para sincronizar a todos los filósofos después de pensar y
 * después de soltar los tenedores. *** ADVERTENCIA: Esta implementación NO
 * resuelve el problema del deadlock inherente a los filósofos si todos intentan
 * tomar los tenedores al mismo tiempo. Simplemente añade puntos de
 * sincronización con barreras. Se usan semáforos individuales para proteger el
 * acceso a cada tenedor. ***
 */
public class PhilosophersBarrierStrategy implements SynchronizationStrategy {

    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];

    // Barrera para N filósofos
    private CyclicBarrier barrier;

    // Semáforos para proteger cada tenedor individualmente
    private Semaphore[] forks;
    private static final long VISUALIZATION_DELAY = 420L;

    public PhilosophersBarrierStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(PhilosophersSim.N); // Barrera para todos los filósofos
        forks = new Semaphore[PhilosophersSim.N];
        for (int i = 0; i < PhilosophersSim.N; i++) {
            forks[i] = new Semaphore(1); // Cada tenedor es un semáforo binario
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

    // Método auxiliar para liberar tenedores en caso de interrupción
    private void cleanupForks(int id) {
        int leftFork = id;
        int rightFork = (id + 1) % PhilosophersSim.N;
        // Solo libera si realmente los posee (usa tryAcquire para no bloquear)
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
