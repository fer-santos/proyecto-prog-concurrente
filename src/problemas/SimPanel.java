// Archivo: SimPanel.java (dentro del paquete problemas)
// --------------------------------------------------------
package problemas; // <-- AÑADE ESTA LÍNEA

import core.DrawingPanel;
import javax.swing.JComponent;
// import ProyectoPCyP.SyncMethod; // <-- AÑADE ESTE IMPORT

// Interfaz para unificar todos los paneles de simulación
public interface SimPanel {
    void showSkeleton();
    void startWith(SyncMethod method); // <-- Usa el tipo calificado
    void stopSimulation();
    JComponent getComponent();
    
    void setDrawingPanel(DrawingPanel drawingPanel);
}