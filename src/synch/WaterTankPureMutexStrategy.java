package synch;

import java.util.concurrent.locks.ReentrantLock;
import problemas.WaterTankSim;

/**
 * Esta es una implementación de "Mutex Puro" para el Productor-Consumidor.
 * NO usa Variables de Condición, solo el lock.
 * * Utiliza "espera activa" (busy-waiting): los hilos bloquean, 
 * comprueban la condición (si está lleno/vacío), y si no pueden
 * trabajar, desbloquean y se duermen un rato antes de volver a intentar.
 * * Es funcional pero muy ineficiente.
 */
public class WaterTankPureMutexStrategy implements SynchronizationStrategy {
    
    private final WaterTankSim panel;
    private Thread producer, consumer;

    // Un ÚNICO lock compartido por ambos hilos.
    private final ReentrantLock mutex = new ReentrantLock();

    public WaterTankPureMutexStrategy(WaterTankSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        producer = new Thread(() -> {
            while (panel.running.get()) {
                mutex.lock(); // 1. Tomar el lock
                try {
                    // 2. Sección Crítica: Comprobar y actuar
                    if (panel.level < WaterTankSim.SLOTS) {
                        panel.level++;
                    }
                    // Si el tanque está lleno, no hace nada,
                    // simplemente libera el lock y vuelve a dormir.
                } finally {
                    mutex.unlock(); // 3. Soltar el lock
                }
                
                // 4. Dormir un tiempo FUERA del lock
                sleepRand(180, 400); 
            }
        }, "Producer-PureMutex");

        consumer = new Thread(() -> {
            while (panel.running.get()) {
                mutex.lock(); // 1. Tomar el lock
                try {
                    // 2. Sección Crítica: Comprobar y actuar
                    if (panel.level > 0) {
                        panel.level--;
                    }
                    // Si el tanque está vacío, no hace nada,
                    // simplemente libera el lock y vuelve a dormir.
                } finally {
                    mutex.unlock(); // 3. Soltar el lock
                }
                
                // 4. Dormir un tiempo FUERA del lock
                sleepRand(180, 400);
            }
        }, "Consumer-PureMutex");

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

    // Método auxiliar para dormir
    private void sleepRand(int min, int max) {
        try {
            // Dormir un tiempo aleatorio
            Thread.sleep(min + (int)(Math.random() * (max-min)));
        } catch (InterruptedException e) {
            // Si el hilo es interrumpido (al parar la sim), termina
            Thread.currentThread().interrupt();
        }
    }
}