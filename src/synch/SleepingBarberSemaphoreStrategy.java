package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

public class SleepingBarberSemaphoreStrategy implements SynchronizationStrategy {
    private final SleepingBarberSim panel;
    private Thread generator, barberLoop;

    private final AtomicInteger waiting = new AtomicInteger(0);
    private final Semaphore customersSem = new Semaphore(0);
    private final Semaphore barberSem = new Semaphore(0);
    private final Semaphore accessSeats = new Semaphore(1);

    public SleepingBarberSemaphoreStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        generator = new Thread(() -> {
            while (panel.running.get()) {
                sleepRand(800, 1800);
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
                        customersSem.release(); // Llama al barbero
                        accessSeats.release();
                        
                        barberSem.acquire(); // Cliente espera a que el barbero lo atienda
                        // En este punto, el barbero ya tomó al cliente de la "cola" lógica
                        
                    } else {
                        accessSeats.release(); // No hay asientos, se va
                        c.state = CustState.LEAVING;
                        setTarget(c, panel.getWidth() + 60, c.y);
                    }
                } catch (InterruptedException e) { return; }
            }
        }, "GeneratorSem");

        barberLoop = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    if (waiting.get() == 0) panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                    customersSem.acquire(); // Barbero duerme si no hay clientes
                    
                    accessSeats.acquire();
                    waiting.decrementAndGet();
                    panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                    
                    // Lógica visual para mover al cliente
                    Customer c = findNextCustomerVisual();
                    if(c != null) {
                        panel.seats[findCustomerSeatIndex(c)] = null;
                        panel.inChair = c;
                        moveCustomerToChair(c);
                    }
                    
                    barberSem.release(); // Barbero está listo para el siguiente
                    accessSeats.release();
                    
                    sleepRand(1200, 2000); // Cortando el pelo
                    
                    if(c != null) {
                        c.state = CustState.LEAVING;
                        setTarget(c, panel.getWidth() + 60, c.y);
                        panel.inChair = null;
                    }
                    
                } catch (InterruptedException e) { return; }
            }
        }, "BarberSem");

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
    
    // Métodos auxiliares visuales
    private int findNextFreeSeatVisual() {
        for(int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] == null) return i;
        }
        return -1;
    }
    
    private Customer findNextCustomerVisual() {
        for(int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] != null) return panel.seats[i];
        }
        return null;
    }
    
    private int findCustomerSeatIndex(Customer c) {
        for(int i = 0; i < panel.seats.length; i++) {
            if (panel.seats[i] == c) return i;
        }
        return -1;
    }

    // Métodos de control de Customer
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
}