package synch;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

/**
 * Implementación del patrón Monitor (según Hoare) para Lectores-Escritores.
 * Utiliza ReentrantLock y dos Condition (okToRead, okToWrite). Permite
 * múltiples lectores concurrentes y da cierta preferencia a escritores.
 */
public class ReadersWritersMonitorStrategy implements ReadersWritersStrategy {

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;

    // --- Componentes del Monitor ---
    private final ReentrantLock lock = new ReentrantLock(true); // Mutex [cite: 99]
    private final Condition okToRead = lock.newCondition(); // Condición lectores [cite: 53, 440]
    private final Condition okToWrite = lock.newCondition();// Condición escritores [cite: 53, 440]

    // --- Estado Protegido por el Monitor ---
    private int readersActive = 0;      // Lectores activos [cite: 434]
    private boolean writerActive = false; // Escritor activo [cite: 436]
    private int writersWaiting = 0;     // Escritores esperando

    public ReadersWritersMonitorStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        // Reiniciar estado
        readersActive = 0;
        writerActive = false;
        writersWaiting = 0;

        // Executor para hilos de actores
        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // Hilo que genera actores
        spawner = new Thread(() -> {
            try { // Manejo de InterruptedException
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    if (panel.actors.size() < 20) {
                        spawn(Math.random() < 0.7 ? Role.READER : Role.WRITER);
                    }
                    sleepRandInterruptibly(400, 1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaura estado interrumpido
            }
        }, "Spawner-Monitor"); // Nombre del hilo
        spawner.setDaemon(true);
        spawner.start();
    }

    @Override
    public void stop() {
        if (spawner != null) {
            spawner.interrupt();
        }
        if (exec != null) {
            exec.shutdownNow();
        }
    }

    @Override
    public void requestAccess(Actor a) {
        exec.submit(() -> {
            try {
                if (a.role == Role.READER) {
                    startRead(a);
                    sleepRandInterruptibly(800, 1500); // Simula lectura
                    endRead(a);
                } else { // WRITER
                    startWrite(a);
                    sleepRandInterruptibly(1000, 1800); // Simula escritura
                    endWrite(a);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // --- Procedimientos del Monitor (según Hoare) ---
    private void startRead(Actor a) throws InterruptedException {
        lock.lock(); // Entra al monitor [cite: 100]
        try {
            // Espera si hay escritor activo o escritores esperando [cite: 425, 449]
            while (writerActive || writersWaiting > 0) {
                panel.readersWaiting++; // Actualiza contador visual
                okToRead.await(); // Espera en la condición [cite: 46, 449]
                panel.readersWaiting--; // Actualiza contador visual
            }
            // Entra a leer
            readersActive++;
            panel.readersActive = readersActive; // Actualiza visualización [cite: 451]

            // Mueve actor visualmente
            a.state = ReadersWritersSim.AState.READING;
            a.tx = panel.docCenter().x + (Math.random() * 80 - 40);
            a.ty = panel.docCenter().y + (Math.random() * 80 - 40);

            // Hoare sugiere signal al final, pero signal aquí permite entrada en cascada
            // okToRead.signal(); // Opcional: despertar a otro lector [cite: 452]
        } finally {
            lock.unlock(); // Sale del monitor [cite: 100, 105]
        }
    }

    private void endRead(Actor a) {
        lock.lock(); // Entra al monitor [cite: 100]
        try {
            readersActive--;
            panel.readersActive = readersActive; // Actualiza visualización [cite: 455]

            // Si es el último lector, despierta a UN escritor [cite: 456]
            if (readersActive == 0) {
                okToWrite.signal(); // Despierta a un escritor esperando [cite: 47, 456]
            }

            // Mueve actor visualmente
            a.state = ReadersWritersSim.AState.LEAVING;
            a.tx = panel.getWidth() + 40;

        } finally {
            lock.unlock(); // Sale del monitor [cite: 100, 105]
        }
    }

    private void startWrite(Actor a) throws InterruptedException {
        lock.lock(); // Entra al monitor [cite: 100]
        try {
            writersWaiting++;
            panel.writersWaiting = writersWaiting; // Actualiza visualización

            // Espera si hay lectores activos o un escritor activo [cite: 457]
            while (readersActive > 0 || writerActive) {
                okToWrite.await(); // Espera en la condición [cite: 46, 457]
            }
            // Sale de la espera
            writersWaiting--;
            panel.writersWaiting = writersWaiting; // Actualiza visualización

            // Entra a escribir
            writerActive = true;
            panel.writerActive = true; // Actualiza visualización [cite: 457]

            // Mueve actor visualmente
            a.state = ReadersWritersSim.AState.WRITING;
            a.tx = panel.docCenter().x;
            a.ty = panel.docCenter().y;

        } finally {
            lock.unlock(); // Sale del monitor [cite: 100, 105]
        }
    }

    private void endWrite(Actor a) {
        lock.lock(); // Entra al monitor [cite: 100]
        try {
            writerActive = false;
            panel.writerActive = false; // Actualiza visualización [cite: 459]

            // Decide a quién despertar, dando prioridad a escritores [cite: 426, 459]
            if (writersWaiting > 0) { // Si hay escritores esperando
                okToWrite.signal(); // Despierta a UN escritor [cite: 47, 459]
            } else { // Si no hay escritores, despierta a TODOS los lectores
                okToRead.signalAll(); // Despierta a todos los lectores [cite: 47, 459]
            }

            // Mueve actor visualmente
            a.state = ReadersWritersSim.AState.LEAVING;
            a.tx = -40;

        } finally {
            lock.unlock(); // Sale del monitor [cite: 100, 105]
        }
    }

    // --- Métodos Auxiliares ---
    private void spawn(Role r) {
        Actor a = new Actor();
        a.role = r;
        a.color = (r == Role.READER) ? new Color(90, 160, 255) : new Color(230, 90, 90);
        a.x = (r == Role.READER) ? panel.getWidth() + 40 : -40;
        a.y = panel.getHeight() * 0.75 + (Math.random() * 40 - 20);
        a.tx = (r == Role.READER) ? panel.getWidth() - 80 : 80;
        a.ty = a.y;
        panel.actors.add(a);
    }

    private void sleepRandInterruptibly(int a, int b) throws InterruptedException {
        Thread.sleep(a + (int) (Math.random() * (b - a)));
    }
}
