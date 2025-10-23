package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.PhilosophersSim;
import problemas.PhilosophersSim.State;

/**
 * Implementación del patrón Monitor (Mutex + Variable Condición) 
 * para la Cena de los Filósofos.
 * * Esta es la solución clásica de Hoare/Tanenbaum.
 * - Un mutex (`lock`) protege todo el estado.
 * - Un array de Condition (`self[N]`), uno para cada filósofo.
 * - Un array de State (`panel.state[N]`) que rastrea quién está
 * PENSANDO, HAMBRIENTO o COMIENDO.
 * * Un filósofo solo come si está HAMBRIENTO y sus vecinos NO están COMIENDO.
 * Si no puede comer, hace `await()` en su propia condición.
 * Cuando un filósofo termina de comer, "prueba" (test) a sus vecinos
 * para ver si ahora pueden comer, y si es así, les hace `signal()`.
 */
public class PhilosophersConditionStrategy implements SynchronizationStrategy {

    private final PhilosophersSim panel;
    private final Thread[] threads = new Thread[PhilosophersSim.N];
    
    // El "Monitor" se compone de este lock y las condiciones
    private ReentrantLock lock;
    private Condition[] self; // Una variable de condición por filósofo

    public PhilosophersConditionStrategy(PhilosophersSim panel) {
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
                        
                        // 2. Intentar tomar tenedores
                        pickup(id);
                        
                        // 3. Comer (solo llega aquí si pickup() tuvo éxito)
                        sleepRand(500, 900);
                        
                        // 4. Soltar tenedores
                        drop(id);

                    } catch (InterruptedException e) {
                        return; // Salir del hilo si es interrumpido
                    }
                }
            }, "Philosopher-Cond-" + id);
            threads[id].setDaemon(true);
            threads[id].start();
        }
    }

    /**
     * Procedimiento del monitor para tomar los tenedores.
     * El hilo (filósofo) 'id' intenta comer.
     */
    private void pickup(int id) throws InterruptedException {
        lock.lock();
        try {
            panel.state[id] = State.HUNGRY;
            test(id); // Intenta comer
            
            // Si el test falló (vecinos comiendo), se duerme
            if (panel.state[id] != State.EATING) {
                self[id].await(); 
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Procedimiento del monitor para soltar los tenedores.
     * El hilo (filósofo) 'id' termina de comer.
     */
    private void drop(int id) {
        lock.lock();
        try {
            panel.state[id] = State.THINKING;
            
            // Actualiza el visualizador para soltar tenedores
            // (El filósofo 'id' suelta su tenedor izq 'id' y su tenedor der '(id+1)%N')
            panel.chopstickOwner[id] = -1;
            panel.chopstickOwner[(id + 1) % PhilosophersSim.N] = -1;

            // Despierta a los vecinos para que re-intenten comer
            int left = (id + PhilosophersSim.N - 1) % PhilosophersSim.N;
            int right = (id + 1) % PhilosophersSim.N;
            test(left);
            test(right);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Método auxiliar (privado) del monitor.
     * Comprueba si el filósofo 'id' puede comer AHORA MISMO.
     * SOLO debe llamarse desde dentro del lock.
     */
    private void test(int id) {
        int left = (id + PhilosophersSim.N - 1) % PhilosophersSim.N;
        int right = (id + 1) % PhilosophersSim.N;

        // El filósofo 'id' puede comer si:
        // 1. Está HAMBRIENTO
        // 2. Su vecino izquierdo NO está comiendo
        // 3. Su vecino derecho NO está comiendo
        if (panel.state[id] == State.HUNGRY && 
            panel.state[left] != State.EATING && 
            panel.state[right] != State.EATING) 
        {
            panel.state[id] = State.EATING;
            
            // Actualiza el visualizador para tomar tenedores
            panel.chopstickOwner[id] = id;
            panel.chopstickOwner[(id + 1) % PhilosophersSim.N] = id;
            
            // Despierta al filósofo 'id' (que podría estar en await())
            self[id].signal();
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