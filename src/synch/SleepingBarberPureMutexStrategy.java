package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

/**
 * Implementación de "Mutex Puro" (espera activa) para el Barbero Dormilón. NO
 * usa Variables de Condición. Los hilos (barbero, generador) bloquean el mutex,
 * comprueban el estado de las sillas, y si no pueden actuar, liberan el mutex y
 * duermen un tiempo antes de volver a intentarlo.
 */
public class SleepingBarberPureMutexStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generator, barberLoop;
    private static final long VISUALIZATION_DELAY = 420L;

    // Un ÚNICO lock compartido
    private final ReentrantLock mutex = new ReentrantLock(true);

    public SleepingBarberPureMutexStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {

        // --- Hilo Generador de Clientes ---
        generator = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    Customer c = spawnCustomer();

                    panel.updateGraphGeneratorRequestingLock();
                    if (!sleepVisualization()) {
                        panel.updateGraphGeneratorReleasingLock();
                        break;
                    }

                    boolean locked = false;
                    try {
                        mutex.lockInterruptibly();
                        locked = true;

                        panel.updateGraphGeneratorHoldingLock();
                        if (!sleepVisualization()) {
                            break;
                        }

                        int seatIndex = seatCustomer(c);
                        if (seatIndex >= 0) {
                            Point seatPoint = panel.seatPos(seatIndex);
                            setTarget(c, seatPoint.x, seatPoint.y);
                            c.state = CustState.WAITING;
                        } else {
                            c.state = CustState.LEAVING;
                            setTarget(c, exitX(), c.y);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        panel.updateGraphGeneratorReleasingLock();
                        break;
                    } finally {
                        if (locked) {
                            panel.updateGraphGeneratorReleasingLock();
                            mutex.unlock();
                            locked = false;
                        }
                    }

                    if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    sleepRand(800, 1800);
                }
            } finally {
                panel.updateGraphGeneratorReleasingLock();
            }
        }, "GeneratorPureMutex");

        // --- Hilo del Barbero ---
        barberLoop = new Thread(() -> {
            try {
                while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                    panel.updateGraphBarberRequestingLock();
                    if (!sleepVisualization()) {
                        panel.updateGraphBarberReleasingLock();
                        break;
                    }

                    Customer customerToCut = null;
                    boolean locked = false;
                    try {
                        mutex.lockInterruptibly();
                        locked = true;

                        panel.updateGraphBarberHoldingLock();
                        if (!sleepVisualization()) {
                            break;
                        }

                        customerToCut = pollNextWaitingCustomer();
                        if (customerToCut != null) {
                            panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                            panel.inChair = customerToCut;
                            moveCustomerToChair(customerToCut);
                        } else {
                            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        panel.updateGraphBarberReleasingLock();
                        break;
                    } finally {
                        if (locked) {
                            panel.updateGraphBarberReleasingLock();
                            mutex.unlock();
                            locked = false;
                        }
                    }

                    if (customerToCut != null) {
                        if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                            customerToCut.state = CustState.LEAVING;
                            setTarget(customerToCut, exitX(), customerToCut.y);
                            panel.inChair = null;
                            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                            break;
                        }

                        sleepRand(1200, 2000);

                        customerToCut.state = CustState.LEAVING;
                        setTarget(customerToCut, exitX(), customerToCut.y);
                        panel.inChair = null;
                        panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                    } else {
                        if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        sleepRand(200, 400);
                    }
                }
            } finally {
                panel.updateGraphBarberReleasingLock();
                panel.inChair = null;
                panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
            }
        }, "BarberPureMutex");

        generator.setDaemon(true);
        barberLoop.setDaemon(true);
        generator.start();
        barberLoop.start();
    }

    @Override
    public void stop() {
        if (generator != null) {
            generator.interrupt();
        }
        if (barberLoop != null) {
            barberLoop.interrupt();
        }
    }

    // --- MÉTODOS AUXILIARES (copiados de tu implementación anterior) ---
    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + rnd(b - a));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean sleepVisualization() {
        if (!panel.running.get()) {
            return false;
        }
        try {
            Thread.sleep(VISUALIZATION_DELAY);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Customer spawnCustomer() {
        Customer c = new Customer();
        c.x = -40;
        int panelHeight = panel.getHeight() > 0 ? panel.getHeight() : 400;
        int panelWidth = panel.getWidth() > 0 ? panel.getWidth() : 600;
        c.y = panelHeight * 0.65;
        c.state = CustState.ENTERING;
        c.color = new Color(50 + rnd(180), 50 + rnd(180), 50 + rnd(180));
        panel.customers.add(c);
        double entryX = Math.max(40.0, panelWidth * 0.05);
        setTarget(c, entryX, c.y);
        return c;
    }

    private void moveCustomerToChair(Customer c) {
        c.state = CustState.CUTTING;
        Point p = panel.chairPos();
        setTarget(c, p.x, p.y);
    }

    private int seatCustomer(Customer c) {
        synchronized (panel.seats) {
            for (int i = 0; i < panel.seats.length; i++) {
                if (panel.seats[i] == null) {
                    panel.seats[i] = c;
                    c.seatIndex = i;
                    return i;
                }
            }
        }
        return -1;
    }

    private Customer pollNextWaitingCustomer() {
        synchronized (panel.seats) {
            for (int i = 0; i < panel.seats.length; i++) {
                Customer candidate = panel.seats[i];
                if (candidate != null) {
                    panel.seats[i] = null;
                    candidate.seatIndex = -1;
                    return candidate;
                }
            }
        }
        return null;
    }

    private double exitX() {
        int width = panel.getWidth();
        if (width <= 0) {
            width = 600;
        }
        return width + 60.0;
    }

    private void setTarget(Customer c, double tx, double ty) {
        c.tx = tx;
        c.ty = ty;
    }

    private int rnd(int n) {
        return (int) (Math.random() * n);
    }
}
