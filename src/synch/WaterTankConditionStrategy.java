package synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

// NOTA: Esta es la implementación del patrón MONITOR (Mutex + Condition)
public class WaterTankConditionStrategy implements SynchronizationStrategy {
    private final WaterTankSim panel;
    private Thread producer, consumer;
    private ReentrantLock mtxPC;
    private Condition notEmpty, notFull;

    public WaterTankConditionStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mtxPC = new ReentrantLock(true);
        notEmpty = mtxPC.newCondition();
        notFull = mtxPC.newCondition();

        producer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    mtxPC.lock();
                    try {
                        while (panel.level == WaterTankSim.SLOTS) {
                            notFull.await(); // Espera si está lleno
                        }
                        panel.level++;
                        notEmpty.signal(); // Avisa que ya no está vacío
                    } finally {
                        mtxPC.unlock();
                    }
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Producer-Condition"); // Nombre del Hilo cambiado

        consumer = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    mtxPC.lock();
                    try {
                        while (panel.level == 0) {
                            notEmpty.await(); // Espera si está vacío
                        }
                        panel.level--;
                        notFull.signal(); // Avisa que ya no está lleno
                    } finally {
                        mtxPC.unlock();
                    }
                    Thread.sleep(180 + (int) (Math.random() * 220));
                } catch (InterruptedException ignored) { return; }
            }
        }, "Consumer-Condition"); // Nombre del Hilo cambiado

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