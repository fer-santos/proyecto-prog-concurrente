package synch;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

public class ReadersWritersSemaphoreStrategy implements ReadersWritersStrategy {

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;
    private Semaphore rw_mutex;
    private ReentrantLock rcountMutex;

    public ReadersWritersSemaphoreStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        rw_mutex = new Semaphore(1, true);
        rcountMutex = new ReentrantLock(true);
        exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        spawner = new Thread(() -> {
            while (panel.running.get()) {
                if (panel.actors.size() < 20) {
                    spawn(Math.random() < 0.7 ? Role.READER : Role.WRITER);
                }
                sleepRand(400, 1000);
            }
        });
        spawner.setDaemon(true);
        spawner.start();
    }
    
    @Override
    public void stop() {
        if (spawner != null) spawner.interrupt();
        if (exec != null) exec.shutdownNow();
    }
    
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

    @Override
    public void requestAccess(Actor a) {
        exec.submit(() -> {
            try {
                if (a.role == Role.READER) {
                    panel.readersWaiting++;
                    rcountMutex.lock();
                    if (panel.readersActive == 0) {
                        rw_mutex.acquire(); // Primer lector bloquea a escritores
                    }
                    panel.readersActive++;
                    panel.readersWaiting--;
                    rcountMutex.unlock();
                    
                    // --- Sección Crítica del Lector ---
                    a.state = ReadersWritersSim.AState.READING;
                    a.tx = panel.docCenter().x + (Math.random() * 80 - 40);
                    a.ty = panel.docCenter().y + (Math.random() * 80 - 40);
                    sleepRand(800, 1500);
                    // --- Fin Sección Crítica ---
                    
                    rcountMutex.lock();
                    panel.readersActive--;
                    if (panel.readersActive == 0) {
                        rw_mutex.release(); // Último lector libera para escritores
                    }
                    rcountMutex.unlock();
                    a.state = ReadersWritersSim.AState.LEAVING;
                    a.tx = panel.getWidth() + 40;

                } else { // WRITER
                    panel.writersWaiting++;
                    rw_mutex.acquire();
                    panel.writersWaiting--;

                    // --- Sección Crítica del Escritor ---
                    panel.writerActive = true;
                    a.state = ReadersWritersSim.AState.WRITING;
                    a.tx = panel.docCenter().x;
                    a.ty = panel.docCenter().y;
                    sleepRand(1000, 1800);
                    panel.writerActive = false;
                    // --- Fin Sección Crítica ---
                    
                    rw_mutex.release();
                    a.state = ReadersWritersSim.AState.LEAVING;
                    a.tx = -40;
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }

    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + (int) (Math.random() * (b - a)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}