package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberSemaphoreStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;

    // Lista para rastrear TODOS los hilos activos
    private final List<Thread> activeThreads = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger waiting = new AtomicInteger(0);
    private final Semaphore customersSem = new Semaphore(0);
    private final Semaphore barberSem = new Semaphore(0);
    private final Semaphore accessSeats = new Semaphore(1);

    public SleepingBarberSemaphoreStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        Thread generator = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                sleepRand(800, 1800);
                Thread customerThread = new Thread(this::customerLife);
                customerThread.setDaemon(true);
                activeThreads.add(customerThread); // Rastrear hilo de cliente
                customerThread.start();
            }
        }, "GeneratorSem");
        activeThreads.add(generator); // Rastrear hilo generador

        Thread barberLoop = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (waiting.get() == 0) {
                        panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                    }
                    customersSem.acquire();

                    accessSeats.acquire();
                    waiting.decrementAndGet();
                    panel.barberState = SleepingBarberSim.BarberState.CUTTING;

                    Customer c = findNextCustomerVisual();
                    if (c != null) {
                        panel.seats[findCustomerSeatIndex(c)] = null;
                        panel.inChair = c;
                        moveCustomerToChair(c);
                    }

                    barberSem.release();
                    accessSeats.release();

                    sleepRand(1200, 2000);

                    if (c != null) {
                        c.state = CustState.LEAVING;
                        setTarget(c, panel.getWidth() + 60, c.y);
                        panel.inChair = null;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Salir del bucle
                }
            }
        }, "BarberSem");
        activeThreads.add(barberLoop); // Rastrear hilo del barbero

        // Iniciar los hilos principales
        generator.setDaemon(true);
        barberLoop.setDaemon(true);
        generator.start();
        barberLoop.start();
    }

    private void customerLife() {
        Customer c = spawnCustomer();
        try {
            accessSeats.acquire();
            if (waiting.get() < SleepingBarberSim.MAX_WAIT_CHAIRS) {
                int seatIndex = findNextFreeSeatVisual();
                if (seatIndex != -1) {
                    panel.seats[seatIndex] = c;
                    setTarget(c, panel.seatPos(seatIndex).x, panel.seatPos(seatIndex).y);
                    c.state = CustState.WAITING;
                }
                waiting.incrementAndGet();
                customersSem.release();
                accessSeats.release();
                barberSem.acquire();
            } else {
                accessSeats.release();
                c.state = CustState.LEAVING;
                setTarget(c, panel.getWidth() + 60, c.y);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Un hilo de cliente debe eliminarse a sí mismo de la lista al terminar.
            activeThreads.remove(Thread.currentThread());
        }
    }

    @Override
    public void stop() {
        // Interrumpir TODOS los hilos que esta estrategia ha creado.
        List<Thread> toInterrupt = new ArrayList<>(activeThreads);
        for (Thread t : toInterrupt) {
            t.interrupt();
        }
        activeThreads.clear();
    }

    // --- MÉTODOS AUXILIARES (sin cambios) ---
    private int findNextFreeSeatVisual() {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private Customer findNextCustomerVisual() {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] != null) {
                return panel.seats[i];
            }
        }
        return null;
    }

    private int findCustomerSeatIndex(Customer c) {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private Customer spawnCustomer() {
        Customer c = new Customer();
        c.x = -40;
        c.y = panel.getHeight() * 0.65;
        c.state = CustState.ENTERING;
        c.color = new Color(50 + rnd(180), 50 + rnd(180), 50 + rnd(180));
        panel.customers.add(c);
        setTarget(c, 40, c.y);
        return c;
    }

    private void moveCustomerToChair(Customer c) {
        c.state = CustState.CUTTING;
        Point p = panel.chairPos();
        setTarget(c, p.x, p.y);
    }

    private void setTarget(Customer c, double tx, double ty) {
        c.tx = tx;
        c.ty = ty;
    }

    private int rnd(int n) {
        return (int) (Math.random() * n);
    }

    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + rnd(b - a));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // (Pega aquí los métodos auxiliares de la versión anterior para completar la clase)
}
