package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.BarberState;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberMonitorStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generatorThread;
    private Thread barberThread;

    private static final long VISUALIZATION_DELAY = 420L;

    private ReentrantLock monitorLock;
    private Condition seatsChanged;

    public SleepingBarberMonitorStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        monitorLock = new ReentrantLock(true);
        seatsChanged = monitorLock.newCondition();

        generatorThread = new Thread(this::runGenerator, "Generator-Monitor");
        generatorThread.setDaemon(true);
        generatorThread.start();

        barberThread = new Thread(this::runBarber, "Barber-Monitor");
        barberThread.setDaemon(true);
        barberThread.start();
    }

    private void runGenerator() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customer = spawnCustomer();

                panel.updateGraphCustomerRequestingMonitor();
                if (!sleepVisualization()) {
                    break;
                }

                boolean locked = false;
                try {
                    monitorLock.lockInterruptibly();
                    locked = true;

                    panel.updateGraphCustomerInsideMonitor();
                    if (!sleepVisualization()) {
                        break;
                    }

                    boolean seated = seatCustomer(customer);
                    if (seated) {
                        panel.updateGraphCustomerSeatedMonitor();
                        if (!sleepVisualization()) {
                            break;
                        }

                        panel.updateGraphCustomerSignalingMonitor();
                        seatsChanged.signal();
                        if (!sleepVisualization()) {
                            break;
                        }
                    } else {
                        panel.updateGraphCustomerQueueFullMonitor();
                        customer.state = CustState.LEAVING;
                        setTarget(customer, exitX(), customer.y);
                        if (!sleepVisualization()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (locked) {
                        panel.updateGraphCustomerExitMonitor();
                        monitorLock.unlock();
                        locked = false;
                    }
                }

                panel.updateGraphCustomerIdleMonitor();
                if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                sleepRand(800, 1800);
            }
        } finally {
            panel.updateGraphCustomerIdleMonitor();
        }
    }

    private void runBarber() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customer = null;
                boolean locked = false;
                try {
                    panel.updateGraphBarberRequestingMonitor();
                    if (!sleepVisualization()) {
                        break;
                    }

                    monitorLock.lockInterruptibly();
                    locked = true;

                    panel.updateGraphBarberInsideMonitor();
                    if (!sleepVisualization()) {
                        break;
                    }

                    customer = takeNextCustomer();
                    while (customer == null && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.barberState = BarberState.SLEEPING;
                        panel.updateGraphBarberWaitingMonitor();
                        if (!sleepVisualization()) {
                            break;
                        }
                        seatsChanged.await();
                        panel.updateGraphBarberSignaledMonitor();
                        if (!sleepVisualization()) {
                            break;
                        }
                        customer = takeNextCustomer();
                    }

                    if (customer == null) {
                        continue;
                    }

                    panel.barberState = BarberState.CUTTING;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (locked) {
                        panel.updateGraphBarberExitMonitor();
                        monitorLock.unlock();
                        locked = false;
                    }
                }

                if (customer != null) {
                    sleepRand(1200, 2000);

                    boolean finishLocked = false;
                    try {
                        panel.updateGraphBarberRequestingMonitor();
                        if (!sleepVisualization()) {
                            break;
                        }

                        monitorLock.lockInterruptibly();
                        finishLocked = true;

                        panel.updateGraphBarberInsideMonitor();
                        if (!sleepVisualization()) {
                            break;
                        }

                        if (panel.inChair == customer) {
                            panel.inChair.state = CustState.LEAVING;
                            setTarget(panel.inChair, exitX(), panel.inChair.y);
                            panel.inChair = null;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        if (finishLocked) {
                            panel.updateGraphBarberExitMonitor();
                            monitorLock.unlock();
                        }
                    }

                    panel.barberState = BarberState.SLEEPING;
                    panel.updateGraphBarberIdleMonitor();
                    if (!sleepVisualization()) {
                        break;
                    }
                }
            }
        } finally {
            panel.barberState = BarberState.SLEEPING;
            panel.updateGraphBarberIdleMonitor();
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
        if (monitorLock != null) {
            monitorLock.lock();
            try {
                seatsChanged.signalAll();
            } finally {
                monitorLock.unlock();
            }
        }
    }

    private boolean seatCustomer(Customer customer) {
        synchronized (panel.seats) {
            for (int i = 0; i < panel.seats.length; i++) {
                if (panel.seats[i] == null) {
                    panel.seats[i] = customer;
                    customer.seatIndex = i;
                    Point seatPoint = panel.seatPos(i);
                    setTarget(customer, seatPoint.x, seatPoint.y);
                    customer.state = CustState.WAITING;
                    return true;
                }
            }
        }
        return false;
    }

    private Customer takeNextCustomer() {
        Customer chosen = null;
        synchronized (panel.seats) {
            for (int i = 0; i < panel.seats.length; i++) {
                Customer waiting = panel.seats[i];
                if (waiting != null) {
                    panel.seats[i] = null;
                    waiting.seatIndex = -1;
                    chosen = waiting;
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

    private void sleepRand(int min, int max) {
        try {
            Thread.sleep(min + rnd(max - min));
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