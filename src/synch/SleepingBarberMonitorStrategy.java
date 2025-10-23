package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

/**
 * Implementación del patrón Monitor (según Hoare) para el Barbero Dormilón.
 * Utiliza ReentrantLock para la exclusión mutua y Condition para wait/signal.
 */
public class SleepingBarberMonitorStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generator, barberLoop;

    // --- Componentes del Monitor ---
    private ReentrantLock lock; // Mutex para exclusión
    private Condition seatsChanged; // Condición para despertar al barbero

    public SleepingBarberMonitorStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        lock = new ReentrantLock(true);
        seatsChanged = lock.newCondition();

        // --- Hilo Generador de Clientes ---
        generator = new Thread(() -> {
            while (panel.running.get()) {
                try {
                    Customer c = spawnCustomer();
                    lock.lock(); // Entra al monitor
                    try {
                        int idx = nextFreeSeat();
                        if (idx >= 0) { // Si hay silla
                            panel.seats[idx] = c;
                            c.seatIndex = idx;
                            setTarget(c, panel.seatPos(idx).x, panel.seatPos(idx).y);
                            c.state = CustState.WAITING;
                            seatsChanged.signalAll(); // Avisa al barbero que llegó alguien
                        } else { // Si no hay silla, el cliente se va
                            c.state = CustState.LEAVING;
                            setTarget(c, panel.getWidth() + 60, c.y);
                        }
                    } finally {
                        lock.unlock(); // Sale del monitor
                    }
                    sleepRand(800, 1800); // Espera antes de generar el próximo
                } catch (InterruptedException e) {
                    return; // Termina si es interrumpido
                }
            }
        }, "Generator-Monitor"); // Nombre del hilo

        // --- Hilo del Barbero ---
        barberLoop = new Thread(() -> {
            while (panel.running.get()) {
                Customer customerToCut = null;
                try {
                    lock.lock(); // Entra al monitor
                    try {
                        int idx = nextOccupiedSeat();
                        // Mientras NO haya clientes en las sillas de espera
                        while (idx < 0) {
                            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                            seatsChanged.await(); // El barbero se duerme (libera lock)
                            idx = nextOccupiedSeat(); // Al despertar, vuelve a checar
                        }

                        // Si llega aquí, hay un cliente esperando
                        panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                        customerToCut = panel.seats[idx];
                        panel.seats[idx] = null; // Libera la silla de espera

                        panel.inChair = customerToCut;
                        moveCustomerToChair(customerToCut); // Lo mueve visualmente

                    } finally {
                        lock.unlock(); // Sale del monitor (para permitir corte)
                    }

                    // --- Cortar Pelo (Simulación FUERA DEL LOCK) ---
                    sleepRand(1200, 2000); // Simula el tiempo de corte

                    // --- Terminar con el cliente (Requiere lock brevemente) ---
                    lock.lock(); // Vuelve a entrar al monitor
                    try {
                        // Asegura que sea el mismo cliente antes de hacerlo irse
                        if (panel.inChair == customerToCut) {
                            panel.inChair.state = CustState.LEAVING;
                            setTarget(panel.inChair, panel.getWidth() + 60, panel.inChair.y);
                            panel.inChair = null;
                        }
                        // NOTA: No necesita hacer signal aquí, porque al liberar el lock,
                        // si hay otro cliente esperando, el barbero lo tomará en la
                        // siguiente iteración del while.
                    } finally {
                        lock.unlock(); // Sale del monitor
                    }

                } catch (InterruptedException e) {
                    return; // Termina si es interrumpido
                }
            }
        }, "Barber-Monitor"); // Nombre del hilo

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