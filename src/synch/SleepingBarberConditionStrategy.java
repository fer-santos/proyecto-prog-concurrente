package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

// NOTA: Esta es la implementación del patrón MONITOR (Mutex + Condition)
public class SleepingBarberConditionStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generator, barberLoop;
    
    // El "Monitor"
    private ReentrantLock mutex;
    private Condition seatsChanged; // Condición para despertar al barbero

    public SleepingBarberConditionStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        mutex = new ReentrantLock(true);
        seatsChanged = mutex.newCondition();

        // --- Hilo Generador ---
        generator = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    Customer c = spawnCustomer();
                    mutex.lock();
                    try {
                        int idx = nextFreeSeat();
                        if (idx >= 0) { // Si hay silla
                            panel.seats[idx] = c;
                            c.seatIndex = idx;
                            setTarget(c, panel.seatPos(idx).x, panel.seatPos(idx).y);
                            c.state = CustState.WAITING;
                            seatsChanged.signalAll(); // Avisa al barbero
                        } else { // Si no hay silla
                            c.state = CustState.LEAVING;
                            setTarget(c, panel.getWidth() + 60, c.y);
                        }
                    } finally {
                        mutex.unlock();
                    }
                    sleepRand(800, 1800); // Espera antes de generar el próximo
                } catch (InterruptedException e) {
                    return; // Termina si es interrumpido
                }
            }
        }, "Generator-Cond"); // Nombre cambiado

        // --- Hilo del Barbero ---
        barberLoop = new Thread(() -> {
            while (panel.running.get()) {
                Customer customerToCut = null;
                try {
                    mutex.lock();
                    try {
                        int idx = nextOccupiedSeat();
                        // Mientras NO haya clientes, el barbero duerme
                        while (idx < 0) { 
                            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                            seatsChanged.await(); // Se duerme y libera el lock
                            idx = nextOccupiedSeat(); // Al despertar, vuelve a checar
                        }
                        
                        // Si llega aquí, hay un cliente
                        panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                        customerToCut = panel.seats[idx];
                        panel.seats[idx] = null; // Libera la silla
                        
                        panel.inChair = customerToCut;
                        moveCustomerToChair(customerToCut); // Lo mueve visualmente

                    } finally {
                        mutex.unlock();
                    }

                    // --- Cortar Pelo (FUERA DEL LOCK) ---
                    sleepRand(1200, 2000); // Simula el corte

                    // --- Terminar con el cliente (Requiere lock brevemente) ---
                    mutex.lock();
                    try {
                        if (panel.inChair == customerToCut) { // Asegura que sea el mismo cliente
                            panel.inChair.state = CustState.LEAVING;
                            setTarget(panel.inChair, panel.getWidth() + 60, panel.inChair.y);
                            panel.inChair = null;
                        }
                    } finally {
                        mutex.unlock();
                    }
                    
                } catch (InterruptedException e) {
                    return; // Termina si es interrumpido
                }
            }
        }, "Barber-Cond"); // Nombre cambiado

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

    // --- MÉTODOS AUXILIARES (iguales que antes) ---
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

    private void sleepRand(int a, int b) throws InterruptedException {
        Thread.sleep(a + rnd(b - a));
    }
}