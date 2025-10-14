package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberMutexStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generator, barberLoop;
    private ReentrantLock mutex;
    private Condition seatsChanged;

    public SleepingBarberMutexStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mutex = new ReentrantLock(true);
        seatsChanged = mutex.newCondition();

        generator = new Thread(() -> {
            while (panel.running.get()) {
                Customer c = spawnCustomer();
                mutex.lock();
                try {
                    int idx = nextFreeSeat();
                    if (idx >= 0) {
                        panel.seats[idx] = c;
                        c.seatIndex = idx;
                        Point p = panel.seatPos(idx);
                        setTarget(c, p.x, p.y);
                        c.state = CustState.WAITING;
                        seatsChanged.signalAll();
                    } else {
                        c.state = CustState.LEAVING;
                        setTarget(c, panel.getWidth() + 60, c.y);
                    }
                } finally {
                    mutex.unlock();
                }
                sleepRand(800, 1800);
            }
        }, "GeneratorMutex");

        barberLoop = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    mutex.lock();
                    try {
                        int idx = nextOccupiedSeat();
                        while (idx < 0) {
                            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                            seatsChanged.await();
                            idx = nextOccupiedSeat();
                        }

                        panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                        Customer c = panel.seats[idx];
                        panel.seats[idx] = null;
                        
                        panel.inChair = c;
                        moveCustomerToChair(c);

                    } finally {
                        mutex.unlock();
                    }
                    
                    // Simula el corte fuera del lock principal para permitir que lleguen clientes
                    sleepRand(1200, 2000);
                    
                    mutex.lock();
                    try {
                        if (panel.inChair != null) {
                            panel.inChair.state = CustState.LEAVING;
                            setTarget(panel.inChair, panel.getWidth() + 60, panel.inChair.y);
                            panel.inChair = null;
                        }
                    } finally {
                        mutex.unlock();
                    }
                    
                } catch (InterruptedException ignored) { return; }
            }
        }, "BarberMutex");

        generator.setDaemon(true);
        barberLoop.setDaemon(true);
        generator.start();
        barberLoop.start();
    }

    @Override
    public void stop() {
        if (generator != null) generator.interrupt();
        if (barberLoop != null) barberLoop.interrupt();
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
    
    private int nextFreeSeat() {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] == null) return i;
        }
        return -1;
    }
    
    private int nextOccupiedSeat() {
        for (int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] != null) return i;
        }
        return -1;
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
}