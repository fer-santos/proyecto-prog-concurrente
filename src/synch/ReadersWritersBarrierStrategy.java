package synch;

import java.awt.Color;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

/**
 * Implementación "forzada" de Barreras para Lectores-Escritores. Utiliza
 * CyclicBarrier(2) para sincronizar el Spawner con UN actor que completa su
 * operación. *** ADVERTENCIA: Esto es muy ineficiente, limita severamente la
 * concurrencia y no es la forma natural de resolver el problema. Se necesita un
 * lock adicional para gestionar el acceso correcto de lectura/escritura. ***
 */
public class ReadersWritersBarrierStrategy implements ReadersWritersStrategy {

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;

    // Barrera para sincronizar Spawner y UN actor a la vez
    private CyclicBarrier barrier;

    // Lock para proteger el estado (readersActive, writerActive) y acceso
    private final ReentrantLock accessLock = new ReentrantLock(true);
    private int readersActive = 0;
    private boolean writerActive = false;
    // Contadores visuales (ya existen en el panel, los actualizaremos)

    public ReadersWritersBarrierStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(2); // Barrera para Spawner y 1 Actor
        readersActive = 0;
        writerActive = false;
        panel.readersActive = 0; // Reset visual counters
        panel.writerActive = false;
        panel.readersWaiting = 0;
        panel.writersWaiting = 0;

        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // --- Hilo Spawner ---
        spawner = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1. Intentar Spawmear si hay espacio
                    if (panel.actors.size() < 20) {
                        spawn(Math.random() < 0.7 ? Role.READER : Role.WRITER);
                    } else {
                        // Si no spawneó, dormir un poco para no ciclar tan rápido
                        Thread.sleep(100);
                    }

                    // 2. Esperar a que UN actor termine su operación en la barrera
                    barrier.await();

                    // 3. Dormir antes de la siguiente ronda de spawn/espera
                    Thread.sleep(400 + rnd(600));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Spawner-Barrier");
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
        // Resetear barrera si es necesario (aunque shutdownNow debería interrumpir await)
        if (barrier != null && !barrier.isBroken()) {
            barrier.reset();
        }
    }

    @Override
    public void requestAccess(Actor a) {
        exec.submit(() -> {
            boolean accessGranted = false;
            panel.readersWaiting += (a.role == Role.READER ? 1 : 0);
            panel.writersWaiting += (a.role == Role.WRITER ? 1 : 0);

            try {
                // --- Bucle de Intento de Acceso (similar a espera activa) ---
                while (!accessGranted && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    accessLock.lock();
                    try {
                        if (a.role == Role.READER) {
                            if (!writerActive) { // Puede leer si no hay escritor
                                readersActive++;
                                panel.readersActive = readersActive;
                                accessGranted = true;
                                panel.readersWaiting--; // Deja de esperar visualmente

                                // Mover actor a zona de lectura
                                a.state = ReadersWritersSim.AState.READING;
                                a.tx = panel.docCenter().x + (Math.random() * 80 - 40);
                                a.ty = panel.docCenter().y + (Math.random() * 80 - 40);
                            }
                        } else { // WRITER
                            if (readersActive == 0 && !writerActive) { // Puede escribir si no hay nadie
                                writerActive = true;
                                panel.writerActive = true;
                                accessGranted = true;
                                panel.writersWaiting--; // Deja de esperar visualmente

                                // Mover actor a zona de escritura
                                a.state = ReadersWritersSim.AState.WRITING;
                                a.tx = panel.docCenter().x;
                                a.ty = panel.docCenter().y;
                            }
                        }
                    } finally {
                        accessLock.unlock();
                    }

                    if (!accessGranted) {
                        // Si no obtuvo acceso, dormir brevemente antes de reintentar
                        Thread.sleep(50 + rnd(100));
                    }
                } // Fin del while de intento de acceso

                // --- Sección Crítica Simulada (SI obtuvo acceso) ---
                if (accessGranted) {
                    if (a.role == Role.READER) {
                        Thread.sleep(800 + rnd(700)); // Simula lectura
                    } else {
                        Thread.sleep(1000 + rnd(800)); // Simula escritura
                    }
                }

            } catch (InterruptedException e) {
                // Si fue interrumpido, asegurarse de limpiar el estado si tenía acceso
                Thread.currentThread().interrupt(); // Restaurar flag
                accessGranted = false; // No considerarlo activo para el finally
            } finally {
                // --- Lógica de Salida y Barrera ---
                boolean needsToAwait = false;
                if (accessGranted) {
                    accessLock.lock();
                    try {
                        if (a.role == Role.READER) {
                            readersActive--;
                            panel.readersActive = readersActive;
                            a.state = ReadersWritersSim.AState.LEAVING;
                            a.tx = panel.getWidth() + 40;
                        } else { // WRITER
                            writerActive = false;
                            panel.writerActive = false;
                            a.state = ReadersWritersSim.AState.LEAVING;
                            a.tx = -40;
                        }
                        needsToAwait = true; // Solo espera en barrera si completó operación
                    } finally {
                        accessLock.unlock();
                    }
                } else {
                    // Si nunca obtuvo acceso (o fue interrumpido antes), limpiar contadores visuales
                    panel.readersWaiting -= (a.role == Role.READER ? 1 : 0);
                    panel.writersWaiting -= (a.role == Role.WRITER ? 1 : 0);
                    // Marcar como saliendo directamente
                    if (a.state == ReadersWritersSim.AState.WAITING) { // Evitar mover si ya se estaba moviendo
                        a.state = ReadersWritersSim.AState.LEAVING;
                        a.tx = (a.role == Role.READER) ? panel.getWidth() + 40 : -40;
                    }
                }

                // --- Esperar en la barrera SI completó la operación ---
                if (needsToAwait) {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt(); // Salir si hay problemas
                    }
                }
            } // Fin finally general
        }); // Fin exec.submit
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

    private int rnd(int n) {
        return (int) (Math.random() * n);
    }
}
