package problemas;

import core.DrawingPanel;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class VirtualAssistantsSim extends JPanel implements SimPanel {

    private DrawingPanel drawingPanel;

    public VirtualAssistantsSim() {
        // Placeholder constructor
    }

    @Override
    public void showSkeleton() {
        // TODO: Implement skeleton view
    }

    @Override
    public void startWith(SyncMethod method) {
        // TODO: Implement start logic per sync method
    }

    @Override
    public void stopSimulation() {
        // TODO: Implement stop logic
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void setDrawingPanel(DrawingPanel drawingPanel) {
        this.drawingPanel = drawingPanel;
    }

    public void handleChartSelection(DrawingPanel.ChartKind kind) {
        // TODO: Wire chart visualization for performance metrics
    }
}
