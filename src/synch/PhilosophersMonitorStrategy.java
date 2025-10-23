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
                while (panel.running.get()) {
                    try {
                        // 1. Pensar
                        panel.state[id] = State.THINKING;
                        sleepRand(400, 1000);
                        
                        // 2. Intentar tomar tenedores (procedimiento del monitor)
                        pickup(id);
                        
                        // 3. Comer (solo si pickup tuvo éxito)
                        sleepRand(500, 900);
                        
                        // 4. Soltar tenedores (procedimiento del monitor)
                        drop(id);

                    } catch (InterruptedException e) {
                        return; // Salir del hilo si es interrumpido
                    }
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
        lock.lock(); // Entra al monitor [cite: 100]
        try {
            panel.state[id] = State.HUNGRY;
            test(id); // Intenta comer (podría cambiar state a EATING)
            
            // Si test() no lo puso a comer, espera en su condición
            if (panel.state[id] != State.EATING) {
                self[id].await(); // Libera el lock mientras espera [cite: 46, 109]
            }
        } finally {
            lock.unlock(); // Sale del monitor [cite: 100, 105]
        }
    }

    /**
     * Procedimiento del monitor para soltar los tenedores.
     */
    private void drop(int id) {
        lock.lock(); // Entra al monitor [cite: 100]
        try {
            panel.state[id] = State.THINKING;
            
            // Actualiza visualizador (suelta tenedor izq 'id' y der '(id+1)%N')
            panel.chopstickOwner[id] = -1;
            panel.chopstickOwner[(id + 1) % PhilosophersSim.N] = -1;

            // Verifica si los vecinos ahora pueden comer
            int left = (id + PhilosophersSim.N - 1) % PhilosophersSim.N;
            int right = (id + 1) % PhilosophersSim.N;
            test(left);
            test(right);
        } finally {
            lock.unlock(); // Sale del monitor [cite: 100, 105]
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