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
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        // 1. Pensar
                        panel.state[id] = State.THINKING;
                        // Limpiar tenedores visualmente al pensar (si los tenía)
                        if (panel.chopstickOwner[id] == id) {
                            panel.chopstickOwner[id] = -1;
                        }
                        if (panel.chopstickOwner[(id + 1) % PhilosophersSim.N] == id) {
                            panel.chopstickOwner[(id + 1) % PhilosophersSim.N] = -1;
                        }
                        sleepRand(400, 1000);

                        // 2. Esperar a que TODOS terminen de pensar
                        panel.state[id] = State.HUNGRY; // Marcar como hambriento antes de esperar
                        barrier.await();

                        // 3. Intentar tomar tenedores (IZQUIERDA, luego DERECHA - propenso a deadlock)
                        int leftFork = id;
                        int rightFork = (id + 1) % PhilosophersSim.N;

                        forks[leftFork].acquire();
                        panel.chopstickOwner[leftFork] = id; // Visualización
                        // sleepRand(50, 150); // Pequeña pausa para aumentar probabilidad de deadlock (opcional)
                        forks[rightFork].acquire();
                        panel.chopstickOwner[rightFork] = id; // Visualización

                        // 4. Comer (Solo si consiguió ambos tenedores)
                        panel.state[id] = State.EATING;
                        sleepRand(500, 900);

                        // 5. Soltar tenedores
                        panel.chopstickOwner[leftFork] = -1; // Visualización
                        forks[leftFork].release();
                        panel.chopstickOwner[rightFork] = -1; // Visualización
                        forks[rightFork].release();
                        panel.state[id] = State.THINKING; // Vuelve a pensar después de comer

                        // 6. Esperar a que TODOS los que comieron terminen de soltar tenedores
                        barrier.await();

                    } catch (InterruptedException e) {
                        // Si se interrumpe (ej. al parar), liberar tenedores si los tenía
                        cleanupForks(id);
                        Thread.currentThread().interrupt(); // Salir
                    } catch (BrokenBarrierException e) {
                        // Si la barrera se rompe, liberar tenedores y salir
                        cleanupForks(id);
                        Thread.currentThread().interrupt();
                    }
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
