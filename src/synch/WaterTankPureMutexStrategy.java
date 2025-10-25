package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

/**
 * Mutex Puro con DELAYS ARTIFICIALES para visualización del RAG.
 */
public class WaterTankPureMutexStrategy implements SynchronizationStrategy {

    private final WaterTankSim simPanel;
    private Thread producer, consumer;
    private final ReentrantLock mutex = new ReentrantLock();

    // --- TIEMPO DE DELAY ARTIFICIAL (en milisegundos) ---
    // Ajusta este valor si las flechas aún parpadean muy rápido o duran demasiado
    private static final long VISUALIZATION_DELAY = 500; // 100ms

    public WaterTankPureMutexStrategy(WaterTankSim panel) {
        this.simPanel = panel;
    }

    @Override
    public void start() {
        producer = new Thread(() -> {
            while (simPanel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    simPanel.updateGraphProducerRequestingMutex();
                    mutex.lock(); // 1. Tomar lock
                    try {
                        simPanel.updateGraphProducerHoldingMutex();

                        // --- DELAY ARTIFICIAL MIENTRAS SE TIENE EL LOCK ---
                        Thread.sleep(VISUALIZATION_DELAY);

                        // 2. Sección Crítica
                        if (simPanel.level < WaterTankSim.SLOTS) {
                            simPanel.level++;
                        } else {
                            simPanel.updateGraphProducerBlockedByBuffer();
                            // --- DELAY ADICIONAL SI ESTÁ BLOQUEADO ---
                            Thread.sleep(VISUALIZATION_DELAY / 2); // Un poco más para ver el bloqueo
                        }
                    } finally {
                        simPanel.updateGraphProducerReleasingMutex();
                        mutex.unlock(); // 3. Soltar lock
                    }
                    // 4. Dormir fuera del lock
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Producer-PureMutex-Delayed"); // Nombre cambiado

        consumer = new Thread(() -> {
            while (simPanel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    simPanel.updateGraphConsumerRequestingMutex();
                    mutex.lock(); // 1. Tomar lock
                    try {
                        simPanel.updateGraphConsumerHoldingMutex();

                        // --- DELAY ARTIFICIAL MIENTRAS SE TIENE EL LOCK ---
                        Thread.sleep(VISUALIZATION_DELAY);

                        // 2. Sección Crítica
                        if (simPanel.level > 0) {
                            simPanel.level--;
                        } else {
                            simPanel.updateGraphConsumerBlockedByBuffer();
                            // --- DELAY ADICIONAL SI ESTÁ BLOQUEADO ---
                            Thread.sleep(VISUALIZATION_DELAY / 2);
                        }
                    } finally {
                        simPanel.updateGraphConsumerReleasingMutex();
                        mutex.unlock(); // 3. Soltar lock
                    }
                    // 4. Dormir fuera del lock
                    //Thread.sleep(180 + (int) (Math.random() * 220));
                    Thread.sleep(300 + (int) (Math.random() * 400));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Consumer-PureMutex-Delayed"); // Nombre cambiado

        producer.setDaemon(true);
        consumer.setDaemon(true);
        producer.start();
        consumer.start();
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.interrupt();
        }
        if (consumer != null) {
            consumer.interrupt();
        }
    }

} // Fin de la clase
