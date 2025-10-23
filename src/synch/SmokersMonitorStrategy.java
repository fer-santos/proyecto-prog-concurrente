package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

/**
 * Implementación del patrón Monitor (según Hoare) para el problema de los
 * Fumadores. Utiliza ReentrantLock y dos Condition: una para el agente
 * (tableEmpty) y otra para los fumadores (canSmoke).
 */
public class SmokersMonitorStrategy implements SynchronizationStrategy {

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];

    // --- Componentes del Monitor ---
    private ReentrantLock lock;       // Mutex para exclusión
    private Condition tableEmpty;     // Agente espera aquí si la mesa está ocupada
    private Condition canSmoke;       // Fumadores esperan aquí si no están sus ingredientes

    public SmokersMonitorStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        lock = new ReentrantLock(true);
        tableEmpty = lock.newCondition();
        canSmoke = lock.newCondition();

        // --- Hilo del Agente ---
        agentThread = new Thread(() -> {
            while (panel.running.get()) {
                lock.lock(); // Entra al monitor
                try {
                    // Espera mientras la mesa NO esté vacía
                    while (panel.i1 != null) {
                        tableEmpty.await(); // Espera en su condición
                    }
                    // Mesa vacía: pone ingredientes y avisa a los fumadores
                    putRandomPair();
                    canSmoke.signalAll(); // Despierta a todos los fumadores
                } catch (InterruptedException e) {
                    return;
                } finally {
                    lock.unlock(); // Sale del monitor
                }
                sleepRand(400, 800); // Espera fuera del monitor
            }
        }, "Agent-Monitor"); // Nombre del hilo

        // --- Hilos de los Fumadores ---
        for (int id = 0; id < 3; id++) {
            final int who = id;
            smokerThreads[id] = new Thread(() -> {
                while (panel.running.get()) {
                    lock.lock(); // Entra al monitor
                    try {
                        // Espera mientras NO pueda fumar (no están sus ingredientes)
                        while (!canSmokeNow(who)) {
                            canSmoke.await(); // Espera en la condición compartida
                        }

                        // Si llega aquí, es su turno: toma ingredientes y vacía la mesa
                        panel.i1 = panel.i2 = null;
                        panel.activeSmoker = who;
                        panel.sstate[who] = SState.ARMANDO;

                    } catch (InterruptedException e) {
                        return;
                    } finally {
                        lock.unlock(); // Sale del monitor (para armar/fumar)
                    }

                    // --- Armar y Fumar (FUERA DEL LOCK) ---
                    sleepRand(500, 900); // Armando...
                    panel.sstate[who] = SState.FUMANDO;
                    sleepRand(800, 1400); // Fumando...

                    // --- Terminar (Requiere lock brevemente) ---
                    lock.lock(); // Vuelve a entrar al monitor
                    try {
                        panel.sstate[who] = SState.ESPERANDO;
                        panel.activeSmoker = -1;
                        tableEmpty.signal(); // Avisa al agente que la mesa está libre
                    } finally {
                        lock.unlock(); // Sale del monitor
                    }
                }
            }, "Smoker-Monitor-" + who); // Nombre del hilo
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
        if (agentThread != null) {
            agentThread.interrupt();
        }
        for (Thread t : smokerThreads) {
            if (t != null) {
                t.interrupt();
            }
        }
    }

    private boolean canSmokeNow(int me) {
        // Solo puede fumar si los ingredientes en la mesa son los complementarios
        return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS))
                || // Fumador 0 necesita Papel y Cerillos
                (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS))
                || // Fumador 1 necesita Tabaco y Cerillos
                (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));      // Fumador 2 necesita Tabaco y Papel
    }

    private boolean pairIs(Ing a, Ing b) {
        // Verifica si los ingredientes a y b están en la mesa (en cualquier orden)
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }

    private void putRandomPair() {
        // Pone dos ingredientes aleatorios, asegurando que siempre falte uno
        int pick = (int) (Math.random() * 3);
        switch (pick) {
            case 0: // Pone Papel y Cerillos (Falta Tabaco)
                panel.i1 = Ing.PAPEL;
                panel.i2 = Ing.CERILLOS;
                break;
            case 1: // Pone Tabaco y Cerillos (Falta Papel)
                panel.i1 = Ing.TABACO;
                panel.i2 = Ing.CERILLOS;
                break;
            default: // Pone Tabaco y Papel (Faltan Cerillos)
                panel.i1 = Ing.TABACO;
                panel.i2 = Ing.PAPEL;
                break;
        }
    }

    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + (int) (Math.random() * (b - a)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Re-interrumpe el hilo si estaba durmiendo
        }
    }
}
