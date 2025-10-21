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

    // Un ÚNICO lock compartido
    private final ReentrantLock mutex = new ReentrantLock(true);

    public SleepingBarberPureMutexStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {

        // --- Hilo Generador de Clientes ---
        generator = new Thread(() -> {
            while (panel.running.get()) {
                // Genera un cliente nuevo
                Customer c = spawnCustomer();

                // Intenta sentar al cliente
                mutex.lock();
                try {
                    int idx = nextFreeSeat();
                    if (idx >= 0) {
                        // Sí hay silla: lo sienta y actualiza su estado
                        panel.seats[idx] = c;
                        c.seatIndex = idx;
                        setTarget(c, panel.seatPos(idx).x, panel.seatPos(idx).y);
                        c.state = CustState.WAITING;
                    } else {
                        // No hay silla: se va
                        c.state = CustState.LEAVING;
                        setTarget(c, panel.getWidth() + 60, c.y);
                    }
                } finally {
                    mutex.unlock();
                }

                // Espera un tiempo aleatorio para generar el próximo cliente
                sleepRand(800, 1800);
            }
        }, "GeneratorPureMutex");

        // --- Hilo del Barbero ---
        barberLoop = new Thread(() -> {
            while (panel.running.get()) {
                Customer customerToCut = null;

                mutex.lock();
                try {
                    int idx = nextOccupiedSeat();
                    if (idx >= 0) {
                        // Sí hay cliente: lo toma para cortar
                        panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                        customerToCut = panel.seats[idx];
                        panel.seats[idx] = null; // Libera la silla de espera

                        // Mueve al cliente a la silla principal
                        panel.inChair = customerToCut;
                        moveCustomerToChair(customerToCut);

                    } else {
                        // No hay clientes: se duerme
                        panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                    }
                } finally {
                    mutex.unlock();
                }

                // --- Sección de Corte (FUERA DEL LOCK) ---
                if (customerToCut != null) {
                    // Simula el tiempo de corte
                    sleepRand(1200, 2000);

                    // El cliente se va (requiere el lock brevemente)
                    mutex.lock();
                    try {
                        if (panel.inChair == customerToCut) {
                            panel.inChair.state = CustState.LEAVING;
                            setTarget(panel.inChair, panel.getWidth() + 60, panel.inChair.y);
                            panel.inChair = null;
                        }
                    } finally {
                        mutex.unlock();
                    }
                } else {
                    // Si no tuvo cliente, duerme un poco antes de volver a checar
                    sleepRand(200, 400);
                }
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

    private void setTarget(Customer c, double tx, double ty) {
        c.tx = tx;
        c.ty = ty;
    }

    private int rnd(int n) {
        return (int) (Math.random() * n);
    }
}
