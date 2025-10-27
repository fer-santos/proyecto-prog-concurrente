package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.BarberState;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberConditionStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generatorThread;
    private Thread barberThread;

    private static final long VISUALIZATION_DELAY = 420L;

    private ReentrantLock mutex;
    private Condition seatsChanged;

    public SleepingBarberConditionStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mutex = new ReentrantLock(true);
        seatsChanged = mutex.newCondition();

        generatorThread = new Thread(this::runGenerator, "Generator-Cond");
        generatorThread.setDaemon(true);
        generatorThread.start();

        barberThread = new Thread(this::runBarber, "Barber-Cond");
        barberThread.setDaemon(true);
        barberThread.start();
    }

    private void runGenerator() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customer = spawnCustomer();

                panel.updateGraphCustomerRequestingLockCondition();
                if (!sleepVisualization()) {
                    panel.updateGraphCustomerIdleCondition();
                    break;
                }

                boolean locked = false;
                try {
                    mutex.lockInterruptibly();
                    locked = true;

                    panel.updateGraphCustomerHoldingLockCondition();
                    if (!sleepVisualization()) {
                        break;
                    }

                    boolean seated = seatCustomer(customer);
                    if (seated) {
                        panel.updateGraphCustomerSeatedCondition();
                        if (!sleepVisualization()) {
                            break;
                        }

                        panel.updateGraphCustomerSignalingCondition();
                        seatsChanged.signal();
                        if (!sleepVisualization()) {
                            break;
                        }
                    } else {
                        panel.updateGraphCustomerQueueFullCondition();
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
                        panel.updateGraphCustomerReleasingLockCondition();
                        mutex.unlock();
                        locked = false;
                    }
                }

                panel.updateGraphCustomerIdleCondition();
                if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                sleepRand(800, 1800);
            }
        } finally {
            panel.updateGraphCustomerIdleCondition();
        }
    }

    private void runBarber() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customer = null;
                boolean locked = false;
                try {
                    panel.updateGraphBarberRequestingLockCondition();
                    if (!sleepVisualization()) {
                        break;
                    }

                    mutex.lockInterruptibly();
                    locked = true;

                    panel.updateGraphBarberHoldingLockCondition();
                    if (!sleepVisualization()) {
                        break;
                    }

                    customer = takeNextCustomer();
                    while (customer == null && panel.running.get() && !Thread.currentThread().isInterrupted()) {
                        panel.barberState = BarberState.SLEEPING;
                        panel.updateGraphBarberWaitingCondition();
                        if (!sleepVisualization()) {
                            break;
                        }
                        seatsChanged.await();
                        if (!panel.running.get()) {
                            break;
                        }
                        panel.updateGraphBarberSignaledCondition();
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
                        panel.updateGraphBarberReleasingLockCondition();
                        mutex.unlock();
                        locked = false;
                    }
                }

                if (customer != null) {
                    sleepRand(1200, 2000);

                    boolean lockedFinish = false;
                    try {
                        panel.updateGraphBarberRequestingLockCondition();
                        if (!sleepVisualization()) {
                            break;
                        }

                        mutex.lockInterruptibly();
                        lockedFinish = true;

                        panel.updateGraphBarberHoldingLockCondition();
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
                        if (lockedFinish) {
                            panel.updateGraphBarberReleasingLockCondition();
                            mutex.unlock();
                        }
                    }

                    panel.barberState = BarberState.SLEEPING;
                    panel.updateGraphBarberIdleCondition();
                    if (!sleepVisualization()) {
                        break;
                    }
                }
            }
        } finally {
            panel.barberState = BarberState.SLEEPING;
            panel.updateGraphBarberIdleCondition();
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
        if (mutex != null) {
            mutex.lock();
            try {
                seatsChanged.signalAll();
            } finally {
                mutex.unlock();
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