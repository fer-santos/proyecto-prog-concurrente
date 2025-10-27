package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberBarrierStrategy implements SynchronizationStrategy {

    private static final long VISUALIZATION_DELAY = 420L;

    private final SleepingBarberSim panel;
    private Thread generatorThread;
    private Thread barberThread;

    private CyclicBarrier barrier;
    private final ReentrantLock chairLock = new ReentrantLock(true);

    public SleepingBarberBarrierStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(2);

        generatorThread = new Thread(this::runGenerator, "Generator-Barrier");
        generatorThread.setDaemon(true);
        generatorThread.start();

        barberThread = new Thread(this::runBarber, "Barber-Barrier");
        barberThread.setDaemon(true);
        barberThread.start();
    }

    @Override
    public void stop() {
        if (generatorThread != null) {
            generatorThread.interrupt();
        }
        if (barberThread != null) {
            barberThread.interrupt();
        }
        if (barrier != null) {
            barrier.reset();
        }
    }

    private void runGenerator() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customer = spawnCustomer();

                boolean lockHeld = false;
                try {
                    chairLock.lockInterruptibly();
                    lockHeld = true;

                    int seatIdx = nextFreeSeat();
                    if (seatIdx >= 0) {
                        panel.seats[seatIdx] = customer;
                        customer.seatIndex = seatIdx;
                        Point seatPoint = panel.seatPos(seatIdx);
                        setTarget(customer, seatPoint.x, seatPoint.y);
                        customer.state = CustState.WAITING;
                    } else {
                        customer.state = CustState.LEAVING;
                        setTarget(customer, exitX(), customer.y);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (lockHeld) {
                        chairLock.unlock();
                    }
                }

                panel.updateGraphGeneratorRequestingBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                panel.updateGraphGeneratorWaitingBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                if (!awaitBarrier()) {
                    break;
                }

                panel.updateGraphGeneratorReleasedBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                panel.updateGraphGeneratorFinishedCycle();
                if (!sleepVisualization()) {
                    break;
                }

                sleepRand(800, 1800);
            }
        } finally {
            panel.updateGraphGeneratorFinishedCycle();
        }
    }

    private void runBarber() {
        try {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customer = null;
                boolean lockHeld = false;
                try {
                    chairLock.lockInterruptibly();
                    lockHeld = true;

                    int seatIdx = nextOccupiedSeat();
                    if (seatIdx >= 0) {
                        panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                        customer = panel.seats[seatIdx];
                        panel.seats[seatIdx] = null;
                        if (customer != null) {
                            customer.seatIndex = -1;
                            panel.inChair = customer;
                            moveCustomerToChair(customer);
                        }
                    } else {
                        panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (lockHeld) {
                        chairLock.unlock();
                    }
                }

                if (customer != null) {
                    sleepRand(1200, 2000);

                    boolean finishLocked = false;
                    try {
                        chairLock.lockInterruptibly();
                        finishLocked = true;

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
                            chairLock.unlock();
                        }
                    }

                    panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                } else {
                    sleepRand(100, 250);
                }

                panel.updateGraphBarberRequestingBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                panel.updateGraphBarberWaitingBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                if (!awaitBarrier()) {
                    break;
                }

                panel.updateGraphBarberReleasedBarrier();
                if (!sleepVisualization()) {
                    break;
                }

                panel.updateGraphBarberFinishedCycle();
                if (!sleepVisualization()) {
                    break;
                }
            }
        } finally {
            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
            panel.updateGraphBarberFinishedCycle();
        }
    }

    private boolean awaitBarrier() {
        if (!panel.running.get() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            barrier.await();
            return panel.running.get() && !Thread.currentThread().isInterrupted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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

    private int nextFreeSeat() {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private int nextOccupiedSeat() {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] != null) {
                return i;
            }
        }
        return -1;
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
            Thread.sleep(min + rnd(Math.max(1, max - min)));
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
