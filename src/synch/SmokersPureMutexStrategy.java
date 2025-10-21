package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

/**
 * Implementación de "Mutex Puro" (espera activa) para los Fumadores.
 * NO usa Variables de Condición.
 * El agente y los 3 fumadores compiten por el mismo lock.
 * Comprueban el estado de la mesa y, si no pueden actuar, 
 * liberan el lock y duermen un tiempo antes de volver a intentarlo.
 */
public class SmokersPureMutexStrategy implements SynchronizationStrategy {

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];
    
    // Un ÚNICO lock compartido por los 4 hilos
    private final ReentrantLock mutex = new ReentrantLock(true);

    public SmokersPureMutexStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {

        // --- Hilo del Agente ---
        agentThread = new Thread(() -> {
            while (panel.running.get()) {
                mutex.lock();
                try {
                    // Solo actúa si la mesa está vacía
                    if (panel.i1 == null) {
                        putRandomPair();
                    }
                    // Si la mesa no está vacía, no hace nada,
                    // solo libera el lock y duerme.
                } finally {
                    mutex.unlock();
                }
                sleepRand(400, 800); // Duerme FUERA del lock
            }
        }, "AgentPureMutex");

        // --- Hilos de los Fumadores ---
        for (int id = 0; id < 3; id++) {
            final int who = id; // El ID del fumador (0=Tabaco, 1=Papel, 2=Cerillos)
            
            smokerThreads[id] = new Thread(() -> {
                while (panel.running.get()) {
                    boolean didSmoke = false;
                    
                    mutex.lock();
                    try {
                        // Comprueba si los ingredientes en la mesa son los que necesita
                        if (canSmokeNow(who)) {
                            // ¡Sí! Los toma (vaciando la mesa)
                            panel.i1 = panel.i2 = null; 
                            panel.activeSmoker = who;
                            panel.sstate[who] = SState.ARMANDO;
                            didSmoke = true;
                        }
                        // Si no son sus ingredientes, no hace nada,
                        // solo libera el lock y duerme.
                    } finally {
                        mutex.unlock();
                    }

                    // --- Simulación de Fumar (FUERA DEL LOCK) ---
                    if (didSmoke) {
                        sleepRand(500, 900); // Armando...
                        panel.sstate[who] = SState.FUMANDO;
                        sleepRand(800, 1400); // Fumando...

                        // Termina de fumar, necesita el lock para resetear
                        mutex.lock();
                        try {
                            panel.sstate[who] = SState.ESPERANDO;
                            panel.activeSmoker = -1;
                        } finally {
                            mutex.unlock();
                        }
                    } else {
                        // No pudo fumar, duerme un poco antes de volver a checar
                        sleepRand(100, 250);
                    }
                }
            }, "SmokerPureMutex-" + who);
        }
        
        // Iniciar todos los hilos
        agentThread.setDaemon(true);
        agentThread.start();
        for (Thread t : smokerThreads) {
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    public void stop() {
        if (agentThread != null) agentThread.interrupt();
        for (Thread t : smokerThreads) {
            if (t != null) t.interrupt();
        }
    }

    // --- MÉTODOS AUXILIARES (Lógica del problema, no de sincro) ---

    // Comprueba si los ingredientes en la mesa son los que el fumador 'me' necesita
    private boolean canSmokeNow(int me) {
        return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS)) ||  // Fumador 0 (Tabaco)
               (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS)) || // Fumador 1 (Papel)
               (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));      // Fumador 2 (Cerillos)
    }

    // Comprueba si los ingredientes 'a' y 'b' están en la mesa
    private boolean pairIs(Ing a, Ing b) {
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }
    
    // Pone un par aleatorio de ingredientes en la mesa
    private void putRandomPair() {
        int pick = (int) (Math.random() * 3);
        switch (pick) {
            // Faltará Tabaco (para Fumador 0)
            case 0 -> { panel.i1 = Ing.PAPEL; panel.i2 = Ing.CERILLOS; } 
            // Faltará Papel (para Fumador 1)
            case 1 -> { panel.i1 = Ing.TABACO; panel.i2 = Ing.CERILLOS; } 
            // Faltarán Cerillos (para Fumador 2)
            default -> { panel.i1 = Ing.TABACO; panel.i2 = Ing.PAPEL; } 
        }
    }
    
    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + (int) (Math.random() * (b - a)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}