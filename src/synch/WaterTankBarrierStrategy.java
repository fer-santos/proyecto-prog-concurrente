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

    public WaterTankBarrierStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(2); // Barrera para Productor y Consumidor

        // --- Hilo Productor ---
        producer = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1. Producir (simulado con sleep)
                    Thread.sleep(180 + (int) (Math.random() * 220));

                    // 2. Intentar agregar al tanque (protegido por lock)
                    levelLock.lock();
                    try {
                        if (panel.level < WaterTankSim.SLOTS) {
                            panel.level++;
                        }
                        // Si está lleno, simplemente no agrega
                    } finally {
                        levelLock.unlock();
                    }

                    // 3. Esperar al consumidor en la barrera
                    barrier.await();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Salir del bucle si se interrumpe
                } catch (BrokenBarrierException e) {
                    // La barrera se rompió (otro hilo fue interrumpido), salir
                    Thread.currentThread().interrupt();
                }
            }
        }, "Producer-Barrier");

        // --- Hilo Consumidor ---
        consumer = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1. Intentar quitar del tanque (protegido por lock)
                    levelLock.lock();
                    try {
                        if (panel.level > 0) {
                            panel.level--;
                        }
                        // Si está vacío, simplemente no quita
                    } finally {
                        levelLock.unlock();
                    }

                    // 2. Consumir (simulado con sleep)
                    Thread.sleep(180 + (int) (Math.random() * 220));

                    // 3. Esperar al productor en la barrera
                    barrier.await();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Salir del bucle si se interrumpe
                } catch (BrokenBarrierException e) {
                    // La barrera se rompió (otro hilo fue interrumpido), salir
                    Thread.currentThread().interrupt();
                }
            }
        }, "Consumer-Barrier");

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
