package synch;

import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import problemas.SleepingBarberSim;
import problemas.SleepingBarberSim.Customer;
import problemas.SleepingBarberSim.CustState;

/**
 * Implementación "forzada" de Barreras para el Barbero Dormilón. Utiliza
 * CyclicBarrier(2) para sincronizar el hilo generador y el hilo barbero después
 * de que cada uno completa una "ronda" (generar/sentar o atender/dormir). ***
 * ADVERTENCIA: Esto es muy ineficiente y no es la forma natural de resolver el
 * problema. Se necesita un lock adicional para proteger las sillas. ***
 */
public class SleepingBarberBarrierStrategy implements SynchronizationStrategy {

    private final SleepingBarberSim panel;
    private Thread generator, barberLoop;

    // Barrera para sincronizar Generador y Barbero
    private CyclicBarrier barrier;

    // Lock para proteger el acceso a las sillas (seats) y la silla principal (inChair)
    private final ReentrantLock chairLock = new ReentrantLock();

    public SleepingBarberBarrierStrategy(SleepingBarberSim panel) {
        this.panel = panel;
    }

    @Override
    public void start() {
        barrier = new CyclicBarrier(2); // Barrera para Generador y Barbero

        // --- Hilo Generador de Clientes ---
        generator = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1. Generar cliente (fuera del lock y barrera)
                    Customer c = spawnCustomer();

                    // 2. Intentar sentar al cliente (protegido por lock)
                    chairLock.lock();
                    try {
                        int idx = nextFreeSeat();
                        if (idx >= 0) { // Si hay silla
                            panel.seats[idx] = c;
                            c.seatIndex = idx;
                            setTarget(c, panel.seatPos(idx).x, panel.seatPos(idx).y);
                            c.state = CustState.WAITING;
                        } else { // Si no hay silla
                            c.state = CustState.LEAVING;
                            setTarget(c, panel.getWidth() + 60, c.y);
                        }
                    } finally {
                        chairLock.unlock();
                    }

                    // 3. Esperar al barbero en la barrera ANTES de dormir
                    barrier.await();

                    // 4. Dormir antes de generar el siguiente (después de la barrera)
                    Thread.sleep(800 + rnd(1000));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Generator-Barrier");

        // --- Hilo del Barbero ---
        barberLoop = new Thread(() -> {
            while (panel.running.get() && !Thread.currentThread().isInterrupted()) {
                Customer customerToCut = null;
                boolean wasSleeping = false;

                try {
                    // 1. Decidir qué hacer (protegido por lock)
                    chairLock.lock();
                    try {
                        int idx = nextOccupiedSeat();
                        if (idx >= 0) { // Hay cliente esperando?
                            panel.barberState = SleepingBarberSim.BarberState.CUTTING;
                            customerToCut = panel.seats[idx];
                            panel.seats[idx] = null; // Libera silla de espera
                            panel.inChair = customerToCut; // Ocupa silla principal
                            moveCustomerToChair(customerToCut);
                        } else { // No hay clientes esperando
                            panel.barberState = SleepingBarberSim.BarberState.SLEEPING;
                            wasSleeping = true;
                        }
                    } finally {
                        chairLock.unlock();
                    }

                    // 2. Ejecutar acción (FUERA del lock)
                    if (customerToCut != null) {
                        // Cortar pelo
                        Thread.sleep(1200 + rnd(800));

                        // Terminar corte (requiere lock)
                        chairLock.lock();
                        try {
                            if (panel.inChair == customerToCut) { // Asegurarse
                                panel.inChair.state = CustState.LEAVING;
                                setTarget(panel.inChair, panel.getWidth() + 60, panel.inChair.y);
                                panel.inChair = null;
                            }
                        } finally {
                            chairLock.unlock();
                        }
                    } else if (wasSleeping) {
                        // Dormir un poco si no había clientes
                        Thread.sleep(100 + rnd(150)); // Pequeña pausa para no ciclar tan rápido
                    }

                    // 3. Esperar al generador en la barrera
                    barrier.await();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Barber-Barrier");

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

    // sleepRand renombrado para evitar conflicto con InterruptedException
    private void sleepRandSimple(int a, int b) {
        try {
            Thread.sleep(a + rnd(b - a));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
