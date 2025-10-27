package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.BarberState;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberSemaphoreStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generatorThread;
    private Thread barberThread;
    private final List<Thread> customerThreads = Collections.synchronizedList(new ArrayList<>());

    private static final long VISUALIZATION_DELAY = 420L;

    private final AtomicInteger waiting = new AtomicInteger(0);
    private final Semaphore customersSem = new Semaphore(0);
    private final Semaphore barberSem = new Semaphore(0);
    private final Semaphore accessSeats = new Semaphore(1);

    public SleepingBarberSemaphoreStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        generatorThread = new Thread(this::runGenerator, "GeneratorSem");
        generatorThread.setDaemon(true);
        generatorThread.start();

        barberThread = new Thread(this::runBarber, "BarberSem");
        barberThread.setDaemon(true);
        barberThread.start();
    }

    private void runGenerator() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                launchCustomerThread();
                sleepRand(800, 1800);
            }
        } finally {
            // El hilo termina cuando stop() interrumpe la espera
        }
    }

    private void launchCustomerThread() {
        Thread customerThread = new Thread(this::customerLife, "CustomerSem-" + System.nanoTime());
        customerThread.setDaemon(true);
        customerThreads.add(customerThread);
        customerThread.start();
    }

    private void runBarber() {
        boolean accessHeld = false;
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                panel.updateGraphBarberWaitingCustomersSemaphore();
                if (!sleepVisualization()) {
                    break;
                }

                customersSem.acquire();
                panel.updateGraphBarberAcquiredCustomersSemaphore();
                if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                panel.updateGraphBarberRequestingAccessSemaphore();
                if (!sleepVisualization()) {
                    break;
                }

                accessSeats.acquire();
                accessHeld = true;
                panel.updateGraphBarberHoldingAccessSemaphore();
                if (!sleepVisualization()) {
                    break;
                }

                waiting.updateAndGet(value -> value > 0 ? value - 1 : 0);

                Customer customer = detachNextCustomer();
                panel.updateGraphBarberReleasingAccessSemaphore();
                accessSeats.release();
                accessHeld = false;

                panel.updateGraphBarberSignalingBarberSemaphore();
                barberSem.release();
                if (!sleepVisualization()) {
                    panel.barberState = BarberState.SLEEPING;
                    break;
                }

                if (customer != null) {
                    panel.barberState = BarberState.CUTTING;
                    sleepRand(1200, 2000);
                    customer.state = CustState.LEAVING;
                    setTarget(customer, exitX(), customer.y);
                    panel.inChair = null;
                }

                panel.barberState = BarberState.SLEEPING;
                panel.updateGraphBarberIdleSemaphore();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (accessHeld) {
                accessSeats.release();
            }
            panel.barberState = BarberState.SLEEPING;
            panel.updateGraphBarberIdleSemaphore();
        }
    }

    private void customerLife() {
        Customer customer = spawnCustomer();
        boolean accessHeld = false;
        try {
            panel.updateGraphCustomerRequestingAccessSemaphore();
            if (!sleepVisualization()) {
                return;
            }

            accessSeats.acquire();
            accessHeld = true;
            panel.updateGraphCustomerHoldingAccessSemaphore();
            if (!sleepVisualization()) {
                return;
            }

            if (waiting.get() < SleepingBarberSim.MAX_WAIT_CHAIRS && panel.running.get()) {
                int seatIndex = claimSeat(customer);
                if (seatIndex >= 0) {
                    waiting.incrementAndGet();
                } else {
                    panel.updateGraphCustomerQueueFullSemaphore();
                    customer.state = CustState.LEAVING;
                    setTarget(customer, exitX(), customer.y);
                    return;
                }

                panel.updateGraphCustomerReleasingAccessSemaphore();
                accessSeats.release();
                accessHeld = false;

                panel.updateGraphCustomerSignalingCustomersSemaphore();
                customersSem.release();
                sleepVisualization();

                panel.updateGraphCustomerWaitingBarberSemaphore();
                if (!sleepVisualization()) {
                    return;
                }

                barberSem.acquire();
                panel.updateGraphCustomerGrantedBarberSemaphore();
                sleepVisualization();
            } else {
                panel.updateGraphCustomerQueueFullSemaphore();
                panel.updateGraphCustomerReleasingAccessSemaphore();
                accessSeats.release();
                accessHeld = false;

                customer.state = CustState.LEAVING;
                setTarget(customer, exitX(), customer.y);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (accessHeld) {
                accessSeats.release();
            }
            panel.updateGraphCustomerIdleSemaphore();
            customerThreads.remove(Thread.currentThread());
        }
    }

    @Override
    public void stop() {
        if (generatorThread != null) {
            generatorThread.interrupt();
        }
        if (barberThread != null) {
            barberThread.interrupt();
        }

        List<Thread> snapshot;
        synchronized (customerThreads) {
            snapshot = new ArrayList<>(customerThreads);
            customerThreads.clear();
        }
        for (Thread t : snapshot) {
            if (t != null) {
                t.interrupt();
            }
        }

        customersSem.release(snapshot.size() + 1);
        barberSem.release(snapshot.size() + 1);
        accessSeats.release();
    }

    private int claimSeat(Customer customer) {
        synchronized (panel.seats) {
            for (int i = 0; i < panel.seats.length; i++) {
                if (panel.seats[i] == null) {
                    panel.seats[i] = customer;
                    customer.seatIndex = i;
                    Point seatPoint = panel.seatPos(i);
                    setTarget(customer, seatPoint.x, seatPoint.y);
                    customer.state = CustState.WAITING;
                    return i;
                }
            }
        }
        return -1;
    }

    private Customer detachNextCustomer() {
        Customer chosen = null;
        synchronized (panel.seats) {
            for (int i = 0; i < panel.seats.length; i++) {
                Customer seatCustomer = panel.seats[i];
                if (seatCustomer != null) {
                    panel.seats[i] = null;
                    seatCustomer.seatIndex = -1;
                    chosen = seatCustomer;
                    break;
                }
            }
        }
        if (chosen != null) {
            panel.inChair = chosen;
            moveCustomerToChair(chosen);
        }
        return chosen;
    }

    private Customer spawnCustomer() {
        Customer customer = new Customer();
        int height = panel.getHeight() > 0 ? panel.getHeight() : 400;
        int width = panel.getWidth() > 0 ? panel.getWidth() : 600;
        customer.x = -40;
        customer.y = height * 0.65;
        customer.state = CustState.ENTERING;
        customer.color = new Color(50 + rnd(180), 50 + rnd(180), 50 + rnd(180));
        panel.customers.add(customer);
        double entryX = Math.max(40.0, width * 0.05);
        setTarget(customer, entryX, customer.y);
        return customer;
    }

    private void moveCustomerToChair(Customer customer) {
        customer.state = CustState.CUTTING;
        Point chair = panel.chairPos();
        setTarget(customer, chair.x, chair.y);
    }

    private void setTarget(Customer customer, double tx, double ty) {
        customer.tx = tx;
        customer.ty = ty;
    }

    private boolean sleepVisualization() {
        if (!panel.running.get()) {
            return false;
        }
        try {
            Thread.sleep(VISUALIZATION_DELAY);
            return panel.running.get() && !Thread.currentThread().isInterrupted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void sleepRand(int a, int b) {
        try {
            Thread.sleep(a + rnd(b - a));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private double exitX() {
        int width = panel.getWidth();
        if (width <= 0) {
            width = 600;
        }
        return width + 60.0;
    }

    private int rnd(int n) {
        return (int) (Math.random() * n);
    }
}
