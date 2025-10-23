package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

/**
 * Implementación del patrón Monitor (según Hoare) para el Productor-Consumidor.
 * Utiliza ReentrantLock para la exclusión mutua y Condition para wait/signal.
 */
public class WaterTankMonitorStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;

    // --- Componentes del Monitor ---
    private ReentrantLock lock; // Mutex para exclusión
    private Condition notEmpty; // Condición para esperar si está vacío
    private Condition notFull;  // Condición para esperar si está lleno

    public WaterTankMonitorStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        lock = new ReentrantLock(true);
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();

        // --- Hilo Productor ---
        producer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    lock.lock(); // Entra al monitor
                    try {
                        // Espera mientras el tanque esté lleno
                        while (panel.level == WaterTankSim.SLOTS) {
                            notFull.await(); // Equivalente a condition.wait
                        }
                        // Produce: incrementa el nivel
                        panel.level++;
                        // Avisa a un consumidor que podría estar esperando
                        notEmpty.signal(); // Equivalente a condition.signal
                    } finally {
                        lock.unlock(); // Sale del monitor
                    }
                    // Simula tiempo de producción (fuera del monitor)
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Producer-Monitor"); // Nombre del hilo

        // --- Hilo Consumidor ---
        consumer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    lock.lock(); // Entra al monitor
                    try {
                        // Espera mientras el tanque esté vacío
                        while (panel.level == 0) {
                            notEmpty.await(); // Equivalente a condition.wait
                        }
                        // Consume: decrementa el nivel
                        panel.level--;
                        // Avisa a un productor que podría estar esperando
                        notFull.signal(); // Equivalente a condition.signal
                    } finally {
                        lock.unlock(); // Sale del monitor
                    }
                     // Simula tiempo de consumo (fuera del monitor)
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Consumer-Monitor"); // Nombre del hilo

        producer.setDaemon(true);
        consumer.setDaemon(true);
        producer.start();
        consumer.start();
    }

    @Override
    public void stop() {
        if (producer != null) producer.interrupt();
        if (consumer != null) consumer.interrupt();
    }
}