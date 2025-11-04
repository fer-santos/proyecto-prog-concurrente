package core;

import problemas.SimPanel;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import problemas.*; // Importamos todas las clases de problemas
import problemas.SimPanel; // <-- AÑADE ESTE IMPORT
import problemas.Problem;       // <-- AÑADE ESTA LÍNEA
import problemas.SyncMethod;  // <-- AÑADE ESTA LÍNEA
import core.DrawingPanel;

public class ProyectoPCyP extends JFrame {

    private Problem selectedProblem = Problem.NONE;

    // ===== Menús =====
    private JMenuItem mutex, semaforos, varCon, monitores, barreras;
    private JMenuItem prodConsum, cenaFilosofos, barberoDormilon, fumadores, lectoresEscritores;
    private JMenuItem deadlockRun, deadlockEdit;
    private JMenuItem graficaAcordeon, graficaCarrusel, graficaScroll;

    // ===== UI general =====
    private final JPanel leftPanel;
    private final DrawingPanel drawing;
    private final JFileChooser chooser = new JFileChooser();
    private SimPanel currentSim = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ProyectoPCyP fr = new ProyectoPCyP();
            fr.setVisible(true);
            fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        });
    }

    public ProyectoPCyP() {
        setSize(1800, 1000);
        setTitle("Proyecto Programación Concurrente y Paralela Otoño 2025");
        setLocationRelativeTo(null);

        setupMenus();

        // ---- Paneles 30% | 70% ----
        leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(240, 240, 240));
        drawing = new DrawingPanel();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, drawing);
        split.setResizeWeight(0.30);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        setupActions();
    }

    private void setupMenus() {
        JMenuBar barra = new JMenuBar();
        JMenu archivo = new JMenu("Archivo");
        JMenuItem nuevo = new JMenuItem("Nuevo");
        JMenuItem abrir = new JMenuItem("Abrir");
        JMenuItem guardar = new JMenuItem("Guardar");
        JMenuItem cerrar = new JMenuItem("Cerrar");
        archivo.add(nuevo);
        archivo.add(abrir);
        archivo.add(guardar);
        archivo.addSeparator();
        archivo.add(cerrar);

        JMenu synch = new JMenu("Synch");
        mutex = new JMenuItem("Mutex");
        semaforos = new JMenuItem("Semáforos");
        varCon = new JMenuItem("Variable Condición");
        monitores = new JMenuItem("Monitores");
        barreras = new JMenuItem("Barreras");
        synch.add(mutex);
        synch.add(semaforos);
        synch.add(varCon);
        synch.add(monitores);
        synch.add(barreras);

        JMenu problemasMenu = new JMenu("Problemas");
        prodConsum = new JMenuItem("Productores-Consumidores");
        cenaFilosofos = new JMenuItem("Cena de los Filosofos");
        barberoDormilon = new JMenuItem("Barbero Dormilón");
        fumadores = new JMenuItem("Fumadores");
        lectoresEscritores = new JMenuItem("Lectores-Escritores");
        problemasMenu.add(prodConsum);
        problemasMenu.add(cenaFilosofos);
        problemasMenu.add(barberoDormilon);
        problemasMenu.add(fumadores);
        problemasMenu.add(lectoresEscritores);

    JMenu graficaMenu = new JMenu("Gráfica");
        graficaAcordeon = new JMenuItem("Acordeón");
        graficaCarrusel = new JMenuItem("Carrusel");
        graficaScroll = new JMenuItem("Scroll");
        graficaMenu.add(graficaAcordeon);
        graficaMenu.add(graficaCarrusel);
        graficaMenu.add(graficaScroll);

    JMenu deadlockMenu = new JMenu("Deadlock");
    deadlockRun = new JMenuItem("Ejecutar");
    deadlockEdit = new JMenuItem("Editar");
    deadlockMenu.add(deadlockRun);
    deadlockMenu.add(deadlockEdit);

        barra.add(archivo);
        barra.add(synch);
        barra.add(problemasMenu);
        barra.add(graficaMenu);
    barra.add(deadlockMenu);
        setJMenuBar(barra);

        // Acciones Archivo
        nuevo.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this, "¿Iniciar nuevo documento? Se perderán cambios no guardados.", "Nuevo", JOptionPane.OK_CANCEL_OPTION);
            if (r == JOptionPane.OK_OPTION) {
                if (drawing != null) {
                    drawing.clearGraph();
                    drawing.setData(new GraphData());
                }
            }
        });
        guardar.addActionListener(e -> saveToFile());
        abrir.addActionListener(e -> openFromFile());
        cerrar.addActionListener(e -> dispose());
        chooser.setFileFilter(new FileNameExtensionFilter("Diagramas (*.diag)", "diag"));
    }

    private void setupActions() {
        // ---- Menú Problemas ----
        prodConsum.addActionListener(e -> selectProblem(Problem.PRODUCERS, new WaterTankSim()));
        cenaFilosofos.addActionListener(e -> selectProblem(Problem.PHILOSOPHERS, new PhilosophersSim()));
        barberoDormilon.addActionListener(e -> selectProblem(Problem.BARBER, new SleepingBarberSim()));
        fumadores.addActionListener(e -> selectProblem(Problem.SMOKERS, new SmokersSim()));
        lectoresEscritores.addActionListener(e -> selectProblem(Problem.READERS_WRITERS, new ReadersWritersSim()));

        // ---- Menú Synch ----
        mutex.addActionListener(e -> selectMethod(SyncMethod.MUTEX));
        semaforos.addActionListener(e -> selectMethod(SyncMethod.SEMAPHORES));
        varCon.addActionListener(e -> selectMethod(SyncMethod.VAR_COND));
        monitores.addActionListener(e -> selectMethod(SyncMethod.MONITORS)); // <-- LÍNEA MODIFICADA
        barreras.addActionListener(e -> selectMethod(SyncMethod.BARRIERS)); // <-- LÍNEA MODIFICADA

        // ---- Menú Gráfica ----
        graficaAcordeon.addActionListener(e -> drawing.showSampleChart(DrawingPanel.ChartKind.ACORDEON));
        graficaCarrusel.addActionListener(e -> drawing.showSampleChart(DrawingPanel.ChartKind.CARROUSEL));
        graficaScroll.addActionListener(e -> drawing.showSampleChart(DrawingPanel.ChartKind.SCROLL));

        // ---- Menú Deadlock ----
        deadlockRun.addActionListener(e -> runDeadlockScenario(false));
        deadlockEdit.addActionListener(e -> runDeadlockScenario(true));
    }

    private void methodNotImplementedYet(String name) {
        // Ya no debería llamarse si todos los botones están conectados
        if (selectedProblem == Problem.NONE) {
            JOptionPane.showMessageDialog(this, "Primero selecciona un problema.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            if (drawing != null) {
                drawing.clearGraph();
            }
            return;
        }
        JOptionPane.showMessageDialog(this, name + " aún no está implementado.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
        if (drawing != null) {
            drawing.clearGraph();
        }
    }

    private void selectProblem(Problem problem, SimPanel sim) {
        if (currentSim != null) {
            currentSim.stopSimulation();
        }
        leftPanel.removeAll();

        selectedProblem = problem;
        currentSim = sim;

        if (currentSim != null) {
            currentSim.setDrawingPanel(this.drawing); // Pasa la referencia
        }

        leftPanel.add(currentSim.getComponent(), BorderLayout.CENTER);
        leftPanel.revalidate();
        leftPanel.repaint();

        if (drawing != null) {
            drawing.clearGraph(); // Limpia grafo
        }
        if (currentSim != null) {
            currentSim.showSkeleton(); // Muestra esqueleto (que también limpia grafo)
        }
    }

    private void selectMethod(SyncMethod method) {
        if (selectedProblem == Problem.NONE || currentSim == null) {
            JOptionPane.showMessageDialog(this, "Primero selecciona un problema (menú Problemas).", "Selecciona un problema", JOptionPane.WARNING_MESSAGE);
            if (drawing != null) {
                drawing.clearGraph(); // Limpia grafo
            }
            return;
        }
        // El setup del grafo se hará dentro de startWith del SimPanel
        currentSim.startWith(method);
    }

    private void runDeadlockScenario(boolean preventDeadlock) {
        PhilosophersSim philosophersSim;
        if (currentSim instanceof PhilosophersSim existing) {
            existing.stopSimulation();
            philosophersSim = existing;
        } else {
            if (currentSim != null) {
                currentSim.stopSimulation();
            }
            philosophersSim = new PhilosophersSim();
            currentSim = philosophersSim;
            currentSim.setDrawingPanel(this.drawing);
        }

        selectedProblem = Problem.DEADLOCK_DEMO;
    philosophersSim.setDrawingPanel(this.drawing);

        leftPanel.removeAll();
        leftPanel.add(philosophersSim.getComponent(), BorderLayout.CENTER);
        leftPanel.revalidate();
        leftPanel.repaint();

        philosophersSim.showSkeleton();
        SyncMethod method = preventDeadlock ? SyncMethod.PHIL_HOARE : SyncMethod.PHIL_DEADLOCK;
        philosophersSim.startWith(method);
    }

    private void saveToFile() {
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".diag")) {
                f = new File(f.getParentFile(), f.getName() + ".diag");
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
                oos.writeObject(drawing.data);
                JOptionPane.showMessageDialog(this, "Guardado:\n" + f.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openFromFile() {
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                GraphData g = (GraphData) ois.readObject();
                drawing.setData(g);
                JOptionPane.showMessageDialog(this, "Abierto:\n" + f.getAbsolutePath());
            } catch (IOException | ClassNotFoundException ex) {
                JOptionPane.showMessageDialog(this, "Error al abrir: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
