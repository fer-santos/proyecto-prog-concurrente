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
 * Implementación del patrón Monitor (Mutex + Variable Condición) para el
 * problema Lectores-Escritores. Permite múltiples lectores concurrentes, pero
 * solo un escritor a la vez. Da cierta preferencia a los escritores para evitar
 * inanición (starvation).
 */
public class ReadersWritersConditionStrategy implements ReadersWritersStrategy {

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;

    // --- Componentes del Monitor ---
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition okToRead = lock.newCondition();
    private final Condition okToWrite = lock.newCondition();

    // --- Estado Protegido por el Monitor ---
    private int readersActive = 0; // Número de lectores LEYENDO ahora
    private boolean writerActive = false; // Hay un escritor ESCRIBIENDO ahora?
    private int writersWaiting = 0; // Número de escritores ESPERANDO

    public ReadersWritersConditionStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        // Reiniciar estado
        readersActive = 0;
        writerActive = false;
        writersWaiting = 0;

        // Executor para los hilos de lectores/escritores
        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // Hilo que genera nuevos lectores/escritores
        spawner = new Thread(() -> {
            try { // <-- ENVOLVER TODO EL BUCLE EN UN TRY
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) { // <-- AÑADIR CHEQUEO DE INTERRUPCIÓN
                    if (panel.actors.size() < 20) {
                        spawn(Math.random() < 0.7 ? Role.READER : Role.WRITER);
                    }
                    // Llama al método que puede lanzar la excepción
                    sleepRandInterruptibly(400, 1000);
                }
            } catch (InterruptedException e) { // <-- CAPTURAR LA EXCEPCIÓN AQUÍ
                // El hilo fue interrumpido (probablemente para detenerse). 
                // Simplemente terminamos. Podemos re-interrumpir si es necesario.
                Thread.currentThread().interrupt();
            }
        }, "Spawner-Cond"); // <-- Cambié el nombre para claridad
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

    // Método llamado por ReadersWritersSim cuando un Actor llega a la zona de espera
    @Override
    public void requestAccess(Actor a) {
        exec.submit(() -> {
            try {
                if (a.role == Role.READER) {
                    startRead(a); // Lógica de entrada del lector
                    // --- Sección Crítica (simulada) del Lector ---
                    sleepRandInterruptibly(800, 1500);
                    // --- Fin Sección Crítica ---
                    endRead(a); // Lógica de salida del lector
                } else { // WRITER
                    startWrite(a); // Lógica de entrada del escritor
                    // --- Sección Crítica (simulada) del Escritor ---
                    sleepRandInterruptibly(1000, 1800);
                    // --- Fin Sección Crítica ---
                    endWrite(a); // Lógica de salida del escritor
                }
            } catch (InterruptedException e) {
                // Si el hilo es interrumpido (al parar sim), simplemente termina.
                Thread.currentThread().interrupt();
            }
        });
    }

    // --- Procedimientos del Monitor ---
    private void startRead(Actor a) throws InterruptedException {
        lock.lock();
        try {
            // Un lector debe esperar si:
            // 1. Hay un escritor activo.
            // 2. Hay escritores esperando (para darles preferencia).
            while (writerActive || writersWaiting > 0) {
                panel.readersWaiting++; // Actualiza contador visual
                okToRead.await();
                panel.readersWaiting--; // Actualiza contador visual
            }
            // Entra a leer
            readersActive++;
            panel.readersActive = readersActive; // Actualiza visualización

            // Mueve el actor visualmente a la zona de lectura
            a.state = ReadersWritersSim.AState.READING;
            a.tx = panel.docCenter().x + (Math.random() * 80 - 40);
            a.ty = panel.docCenter().y + (Math.random() * 80 - 40);

        } finally {
            lock.unlock();
        }
    }

    private void endRead(Actor a) {
        lock.lock();
        try {
            readersActive--;
            panel.readersActive = readersActive; // Actualiza visualización

            // Si soy el ÚLTIMO lector en salir, aviso a UN escritor
            if (readersActive == 0) {
                okToWrite.signal();
            }

            // Mueve el actor visualmente a la zona de salida
            a.state = ReadersWritersSim.AState.LEAVING;
            a.tx = panel.getWidth() + 40;

        } finally {
            lock.unlock();
        }
    }

    private void startWrite(Actor a) throws InterruptedException {
        lock.lock();
        try {
            writersWaiting++;
            panel.writersWaiting = writersWaiting; // Actualiza visualización

            // Un escritor debe esperar si:
            // 1. Hay lectores activos.
            // 2. Hay otro escritor activo.
            while (readersActive > 0 || writerActive) {
                okToWrite.await();
            }
            // Sale de la espera
            writersWaiting--;
            panel.writersWaiting = writersWaiting; // Actualiza visualización

            // Entra a escribir
            writerActive = true;
            panel.writerActive = true; // Actualiza visualización

            // Mueve el actor visualmente a la zona de escritura
            a.state = ReadersWritersSim.AState.WRITING;
            a.tx = panel.docCenter().x;
            a.ty = panel.docCenter().y;

        } finally {
            lock.unlock();
        }
    }

    private void endWrite(Actor a) {
        lock.lock();
        try {
            writerActive = false;
            panel.writerActive = false; // Actualiza visualización

            // Al terminar, decide a quién despertar:
            // Si hay escritores esperando, despierta a UN escritor (les da prioridad).
            // Si NO hay escritores esperando, despierta a TODOS los lectores.
            if (writersWaiting > 0) {
                okToWrite.signal();
            } else {
                okToRead.signalAll();
            }

            // Mueve el actor visualmente a la zona de salida
            a.state = ReadersWritersSim.AState.LEAVING;
            a.tx = -40;

        } finally {
            lock.unlock();
        }
    }

    // --- Métodos Auxiliares ---
    private void spawn(Role r) {
        Actor a = new Actor();
        a.role = r;
        a.color = (r == Role.READER) ? new Color(90, 160, 255) : new Color(230, 90, 90);
        a.x = (r == Role.READER) ? panel.getWidth() + 40 : -40;
        a.y = panel.getHeight() * 0.75 + (Math.random() * 40 - 20);
        a.tx = (r == Role.READER) ? panel.getWidth() - 80 : 80; // Target inicial (zona espera)
        a.ty = a.y;
        panel.actors.add(a);
    }

    // Versión de sleepRand que propaga InterruptedException
    private void sleepRandInterruptibly(int a, int b) throws InterruptedException {
        Thread.sleep(a + (int) (Math.random() * (b - a)));
    }
}
