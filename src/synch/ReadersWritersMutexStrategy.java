package synch;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import problemas.ReadersWritersSim;
import problemas.ReadersWritersSim.Actor;
import problemas.ReadersWritersSim.Role;

public class ReadersWritersMutexStrategy implements ReadersWritersStrategy {

    private final ReadersWritersSim panel;
    private Thread spawner;
    private ExecutorService exec;
    private ReentrantLock mutex;

    public ReadersWritersMutexStrategy(ReadersWritersSim panel) {
        this.panel = panel;
    }
    
    @Override
    public void start() {
        mutex = new ReentrantLock(true);
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
                if (a.role == Role.READER) panel.readersWaiting++; else panel.writersWaiting++;
                mutex.lock();
                try {
                    if (a.role == Role.READER) {
                        panel.readersWaiting--;
                        panel.readersActive++;
                        a.state = ReadersWritersSim.AState.READING;
                        a.tx = panel.docCenter().x + (Math.random() * 80 - 40);
                        a.ty = panel.docCenter().y + (Math.random() * 80 - 40);
                        sleepRand(800, 1500);
                        panel.readersActive--;
                        a.state = ReadersWritersSim.AState.LEAVING;
                        a.tx = panel.getWidth() + 40;
                    } else {
                        panel.writersWaiting--;
                        panel.writerActive = true;
                        a.state = ReadersWritersSim.AState.WRITING;
                        a.tx = panel.docCenter().x;
                        a.ty = panel.docCenter().y;
                        sleepRand(1000, 1800);
                        panel.writerActive = false;
                        a.state = ReadersWritersSim.AState.LEAVING;
                        a.tx = -40;
                    }
                } finally {
                    mutex.unlock();
                }
            } catch (Exception ignored) {}
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