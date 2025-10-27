package synch;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock; // Aún necesitamos un lock para proteger el acceso a 'level'
import problemas.WaterTankSim;

/**
 * Implementación "forzada" de Barreras para el Productor-Consumidor. Utiliza
 * CyclicBarrier para hacer que productor y consumidor esperen el uno al otro
 * después de cada operación. Esto NO es eficiente ni la forma natural de
 * resolver este problema, pero cumple el requisito de usar barreras. Se
 * necesita un lock adicional para proteger el acceso concurrente a la variable
 * 'level'.
 */
public class WaterTankBarrierStrategy implements SynchronizationStrategy {

    private final WaterTankSim panel;
    private Thread producer, consumer;

    // Barrera para 2 participantes (productor y consumidor)
    private CyclicBarrier barrier;

    // Mutex para proteger el acceso directo a panel.level
    // (Necesario porque la barrera no protege la modificación en sí)
    private final ReentrantLock levelLock = new ReentrantLock();
    private static final long VISUALIZATION_DELAY = 420L;

    public WaterTankBarrierStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(2); // Barrera para Productor y Consumidor

        // --- Hilo Productor ---
        producer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    panel.updateGraphProducerWorkingBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);

                    levelLock.lockInterruptibly();
                    boolean locked = true;
                    try {
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                        Thread.sleep(VISUALIZATION_DELAY);
                    } finally {
                        if (locked) {
                            levelLock.unlock();
                            locked = false;
                        }
                    }

                    panel.updateGraphProducerWaitingBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);
                    barrier.await();
                    panel.updateGraphProducerReleasedBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);

                    panel.updateGraphProducerIdleBarrier();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphProducerIdleBarrier();
            }
        }, "Producer-Barrier-Visual");

        // --- Hilo Consumidor ---
        consumer = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    levelLock.lockInterruptibly();
                    boolean locked = true;
                    try {
                        if (panel.level > 0) {
                            panel.level--;
                        }
                        panel.updateGraphConsumerWorkingBarrier();
                        Thread.sleep(VISUALIZATION_DELAY);
                    } finally {
                        if (locked) {
                            levelLock.unlock();
                            locked = false;
                        }
                    }

                    Thread.sleep(VISUALIZATION_DELAY);
                    panel.updateGraphConsumerWaitingBarrier();
                    barrier.await();
                    panel.updateGraphConsumerReleasedBarrier();
                    Thread.sleep(VISUALIZATION_DELAY);

                    panel.updateGraphConsumerIdleBarrier();
                    Thread.sleep(260 + (int) (Math.random() * 260));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            } finally {
                panel.updateGraphConsumerIdleBarrier();
            }
        }, "Consumer-Barrier-Visual");

        producer.setDaemon(true);
        consumer.setDaemon(true);
        producer.start();
        consumer.start();
    }

    @Override
    public void stop() {
        // Interrumpir los hilos hará que fallen en barrier.await() o Thread.sleep()
        // y terminen su ejecución.
        if (producer != null) {
            producer.interrupt();
        }
        if (consumer != null) {
            consumer.interrupt();
        }
    }
}
