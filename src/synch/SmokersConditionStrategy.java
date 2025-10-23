package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

// NOTA: Esta es la implementación del patrón MONITOR (Mutex + Condition)
public class SmokersConditionStrategy implements SynchronizationStrategy {

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];

    // El "Monitor"
    private ReentrantLock mtx;
    private Condition tableEmpty; // Condición para que el agente espere
    private Condition canSmoke;   // Condición para que los fumadores esperen

    public SmokersConditionStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mtx = new ReentrantLock(true);
        tableEmpty = mtx.newCondition();
        canSmoke = mtx.newCondition();

        // --- Hilo del Agente ---
        agentThread = new Thread(() -> {
            while (panel.running.get()) {
                mtx.lock();
                try {
                    // Espera mientras la mesa NO esté vacía
                    while (panel.i1 != null) {
                        tableEmpty.await();
                    }
                    // Pone ingredientes y avisa a los fumadores
                    putRandomPair();
                    canSmoke.signalAll();
                } catch (InterruptedException e) { return; }
                finally {
                    mtx.unlock();
                }
                sleepRand(400, 800); // Duerme fuera del lock
            }
        }, "Agent-Cond"); // Nombre cambiado

        // --- Hilos de los Fumadores ---
        for (int id = 0; id < 3; id++) {
            final int who = id;
            smokerThreads[id] = new Thread(() -> {
                while (panel.running.get()) {
                    mtx.lock();
                    try {
                        // Espera mientras NO pueda fumar (no están sus ingredientes)
                        while (!canSmokeNow(who)) {
                            canSmoke.await();
                        }
                        
                        // Toma los ingredientes y empieza a armar
                        panel.i1 = panel.i2 = null;
                        panel.activeSmoker = who;
                        panel.sstate[who] = SState.ARMANDO;

                    } catch (InterruptedException e) { return; }
                    finally {
                        mtx.unlock();
                    }

                    // --- Armar y Fumar (FUERA DEL LOCK) ---
                    sleepRand(500, 900); // Armando...
                    panel.sstate[who] = SState.FUMANDO;
                    sleepRand(800, 1400); // Fumando...
                    
                    // --- Terminar (Requiere lock brevemente) ---
                    mtx.lock();
                    try {
                        panel.sstate[who] = SState.ESPERANDO;
                        panel.activeSmoker = -1;
                        tableEmpty.signal(); // Avisa al agente que la mesa está libre
                    } finally {
                        mtx.unlock();
                    }
                }
            }, "Smoker-Cond-" + who); // Nombre cambiado
        }
        
        // Iniciar hilos
        agentThread.setDaemon(true);
        agentThread.start();
        for (Thread t : smokerThreads) {
            t.setDaemon(true);
            t.start();
        }
    }
    
    // --- MÉTODOS AUXILIARES Y stop() (iguales que antes) ---

    @Override
    public void stop() {
        if (agentThread != null) agentThread.interrupt();
        for (Thread t : smokerThreads) {
            if (t != null) t.interrupt();
        }
    }
    
    private boolean canSmokeNow(int me) {
        return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS)) ||
               (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS)) ||
               (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));
    }

    private boolean pairIs(Ing a, Ing b) {
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }
    
    private void putRandomPair() {
        int pick = (int) (Math.random() * 3);
        switch (pick) {
            case 0 -> { panel.i1 = Ing.PAPEL; panel.i2 = Ing.CERILLOS; }
            case 1 -> { panel.i1 = Ing.TABACO; panel.i2 = Ing.CERILLOS; }
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