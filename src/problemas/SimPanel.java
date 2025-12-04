
package problemas;

import core.DrawingPanel;
import javax.swing.JComponent;


public interface SimPanel {
    void showSkeleton();
    void startWith(SyncMethod method);
    void stopSimulation();
    JComponent getComponent();
    
    void setDrawingPanel(DrawingPanel drawingPanel);
}