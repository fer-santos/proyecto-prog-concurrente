package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.PhilosophersSim;
import problemas.PhilosophersSim.State;

/**
 * Implementación del patrón Monitor (según Hoare) para la Cena de los Filósofos.
 * Utiliza ReentrantLock y un array de Condition (una por filósofo).
 * Un filósofo solo come si está HAMBRIENTO y sus vecinos NO están COMIENDO.
 * Si no puede comer, hace await() en su propia condición.
 * Al terminar, hace test() a sus vecinos para despertarlos si ahora pueden comer.
 */
public class PhilosophersMonitorStrategy implements SynchronizationStrategy {

    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    
    // --- Componentes del Monitor ---
    private ReentrantLock lock; // Mutex para exclusión [cite: 99]
    private Condition[] self;   // Una variable de condición por filósofo [cite: 53]
    private static final long VISUALIZATION_DELAY = 420L;

    public PhilosophersMonitorStrategy(PhilosophersSim panel) {
        this.panel = panel;
    }
    
    @Override
    public void start() {
        lock = new ReentrantLock(true);
        self = new Condition[PhilosophersSim.N];
        for (int i = 0; i < PhilosophersSim.N; i++) {
            self[i] = lock.newCondition();
        }

        for (int i = 0; i < PhilosophersSim.N; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                int left = id;
                int right = (id + 1) % PhilosophersSim.N;
                try {
                    while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.state[id] = State.THINKING;
                        panel.updateGraphPhilosopherIdleMonitor(id, left, right);
                        sleepRand(400, 1000);

                        if (!panel.running.get()) {
                            break;
                        }

                        pickup(id);
                        sleepRand(500, 900);
                        drop(id);
                        panel.updateGraphPhilosopherIdleMonitor(id, left, right);
                        sleepRand(200, 500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    panel.updateGraphPhilosopherReleasingMonitor(id, left, right);
                    panel.updateGraphPhilosopherExitMonitor(id);
                }
            }, "Philosopher-Monitor-" + id); // Nombre del hilo
            threads[i].setDaemon(true);
            threads[i].start();
        }
    }

    /**
     * Procedimiento del monitor para tomar los tenedores.
     */
    private void pickup(int id) throws InterruptedException {
        panel.updateGraphPhilosopherRequestingMonitor(id);
        boolean locked = false;
        lock.lockInterruptibly();
        locked = true;
        try {
            panel.updateGraphPhilosopherInsideMonitor(id);
            Thread.sleep(VISUALIZATION_DELAY);

            panel.state[id] = State.HUNGRY;
            test(id);

            while (panel.state[id] != State.EATING) {
                panel.updateGraphPhilosopherWaitingMonitor(id);
                Thread.sleep(VISUALIZATION_DELAY);
                self[id].await();
                panel.updateGraphPhilosopherSignaledMonitor(id);
                Thread.sleep(VISUALIZATION_DELAY);
                panel.updateGraphPhilosopherInsideMonitor(id);
                Thread.sleep(VISUALIZATION_DELAY);
            }

            int left = id;
            int right = (id + 1) % PhilosophersSim.N;
            panel.updateGraphPhilosopherEatingMonitor(id, left, right);
            Thread.sleep(VISUALIZATION_DELAY);
        } finally {
            if (locked) {
                panel.updateGraphPhilosopherExitMonitor(id);
                lock.unlock();
            }
        }
    }

    /**
     * Procedimiento del monitor para soltar los tenedores.
     */
    private void drop(int id) throws InterruptedException {
        panel.updateGraphPhilosopherRequestingMonitor(id);
        boolean locked = false;
        lock.lockInterruptibly();
        locked = true;
        try {
            panel.updateGraphPhilosopherInsideMonitor(id);
            Thread.sleep(VISUALIZATION_DELAY);

            panel.state[id] = State.THINKING;
            int leftFork = id;
            int rightFork = (id + 1) % PhilosophersSim.N;
            panel.updateGraphPhilosopherReleasingMonitor(id, leftFork, rightFork);
            panel.chopstickOwner[leftFork] = -1;
            panel.chopstickOwner[rightFork] = -1;
            Thread.sleep(VISUALIZATION_DELAY);

            int left = (id + PhilosophersSim.N - 1) % PhilosophersSim.N;
            int right = (id + 1) % PhilosophersSim.N;
            test(left);
            test(right);
        } finally {
            if (locked) {
                panel.updateGraphPhilosopherExitMonitor(id);
                lock.unlock();
            }
        }
    }

    /**
     * Método auxiliar interno del monitor.
     * Comprueba si el filósofo 'id' PUEDE comer AHORA y lo despierta si es así.
     * Solo debe llamarse con el lock adquirido.
     */
    private void test(int id) {
        int left = (id + PhilosophersSim.N - 1) % PhilosophersSim.N;
        int right = (id + 1) % PhilosophersSim.N;

        if (panel.state[id] == State.HUNGRY && 
            panel.state[left] != State.EATING && 
            panel.state[right] != State.EATING) 
        {
            panel.state[id] = State.EATING;
            
            // Actualiza visualizador (toma tenedor izq 'id' y der '(id+1)%N')
            panel.chopstickOwner[id] = id;
            panel.chopstickOwner[(id + 1) % PhilosophersSim.N] = id;
            
            // Despierta al filósofo 'id' que podría estar esperando en self[id].await()
            self[id].signal(); // [cite: 47, 112]
        }
    }

    @Override
    public void stop() {
        for (Thread t : threads) {
            if (t != null) t.interrupt();
        }
    }
    
    private void sleepRand(int min, int max) throws InterruptedException {
        Thread.sleep(min + (int)(Math.random() * (max-min)));
    }
}