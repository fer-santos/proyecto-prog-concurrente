package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SmokersSim;
import problemas.SmokersSim.Ing;
import problemas.SmokersSim.SState;

/**
 * Implementación "forzada" de Barreras para el problema de los Fumadores.
 * Utiliza CyclicBarrier(4) para sincronizar al agente y los 3 fumadores al
 * final de cada "ronda". *** ADVERTENCIA: Esto es ineficiente y no es la forma
 * natural de resolver el problema. Se necesita un lock adicional para proteger
 * la mesa. ***
 */
public class SmokersBarrierStrategy implements SynchronizationStrategy {

    private final SmokersSim panel;
    private Thread agentThread;
    private final Thread[] smokerThreads = new Thread[3];

    // Barrera para sincronizar Agente + 3 Fumadores
    private CyclicBarrier barrier;

    // Lock para proteger el acceso a los ingredientes en la mesa (i1, i2)
    private final ReentrantLock tableLock = new ReentrantLock();

    public SmokersBarrierStrategy(SmokersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(4); // Barrera para Agente + 3 Fumadores

        // --- Hilo del Agente ---
        agentThread = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1. Intentar poner ingredientes (protegido por lock)
                    tableLock.lock();
                    try {
                        if (panel.i1 == null) { // Solo si la mesa está vacía
                            putRandomPair();
                        }
                    } finally {
                        tableLock.unlock();
                    }

                    // 2. Esperar a los fumadores en la barrera
                    barrier.await();

                    // 3. Dormir un poco antes de la siguiente ronda
                    Thread.sleep(400 + rnd(400));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Agent-Barrier");

        // --- Hilos de los Fumadores ---
        for (int id = 0; id < 3; id++) {
            final int smokerId = id; // El ID del fumador (0=T, 1=P, 2=C)

            smokerThreads[smokerId] = new Thread(() -> {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    boolean smokedThisRound = false;
                    try {
                        // 1. Intentar tomar ingredientes (protegido por lock)
                        tableLock.lock();
                        try {
                            if (canSmokeNow(smokerId)) {
                                panel.i1 = panel.i2 = null; // Tomar ingredientes
                                panel.activeSmoker = smokerId;
                                panel.sstate[smokerId] = SState.ARMANDO;
                                smokedThisRound = true;
                            }
                        } finally {
                            tableLock.unlock();
                        }

                        // 2. Armar y Fumar si tomó ingredientes (FUERA del lock)
                        if (smokedThisRound) {
                            Thread.sleep(500 + rnd(400)); // Armando
                            panel.sstate[smokerId] = SState.FUMANDO;
                            Thread.sleep(800 + rnd(600)); // Fumando

                            // Resetear estado (necesita lock brevemente, aunque no es estrictamente
                            // necesario ya que la barrera sincroniza antes de la próxima acción)
                            tableLock.lock();
                            try {
                                panel.sstate[smokerId] = SState.ESPERANDO;
                                panel.activeSmoker = -1;
                            } finally {
                                tableLock.unlock();
                            }
                        } else {
                            // Si no fumó, quizás dormir un poco para no ciclar tan rápido
                            Thread.sleep(50 + rnd(100));
                        }

                        // 3. Esperar al agente y los otros fumadores en la barrera
                        barrier.await();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Smoker-Barrier-" + smokerId);
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
        if (agentThread != null) {
            agentThread.interrupt();
        }
        for (Thread t : smokerThreads) {
            if (t != null) {
                t.interrupt();
            }
        }
    }

    // --- MÉTODOS AUXILIARES (iguales que antes) ---
    private boolean canSmokeNow(int me) {
        return (me == 0 && pairIs(Ing.PAPEL, Ing.CERILLOS))
                || (me == 1 && pairIs(Ing.TABACO, Ing.CERILLOS))
                || (me == 2 && pairIs(Ing.TABACO, Ing.PAPEL));
    }

    private boolean pairIs(Ing a, Ing b) {
        // Verifica si los ingredientes a y b están en la mesa (en cualquier orden)
        // IMPORTANTE: Esta verificación DEBE hacerse dentro del tableLock
        return (panel.i1 == a && panel.i2 == b) || (panel.i1 == b && panel.i2 == a);
    }

    private void putRandomPair() {
        // Pone dos ingredientes aleatorios, asegurando que siempre falte uno
        // IMPORTANTE: Esta modificación DEBE hacerse dentro del tableLock
        int pick = (int) (Math.random() * 3);
        switch (pick) {
            case 0:
                panel.i1 = Ing.PAPEL;
                panel.i2 = Ing.CERILLOS;
                break;
            case 1:
                panel.i1 = Ing.TABACO;
                panel.i2 = Ing.CERILLOS;
                break;
            default:
                panel.i1 = Ing.TABACO;
                panel.i2 = Ing.PAPEL;
                break;
        }
    }

    private int rnd(int n) {
        return (int) (Math.random() * n);
    }
}
