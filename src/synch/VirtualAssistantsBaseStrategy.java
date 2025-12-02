package synch;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Funcionalidad compartida por las estrategias de Asistentes Virtuales.
 */
public abstract class VirtualAssistantsBaseStrategy implements VirtualAssistantsStrategy {

    protected final int slots;
    protected final int tokens;
    protected final boolean[] slotBusy;
    protected final boolean[] tokenBusy;
    protected final AtomicBoolean running = new AtomicBoolean(false);

    protected VirtualAssistantsBaseStrategy(int slots, int tokens) {
        this.slots = Math.max(1, slots);
        this.tokens = Math.max(1, tokens);
        this.slotBusy = new boolean[this.slots];
        this.tokenBusy = new boolean[this.tokens];
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    protected int takeSlot() {
        for (int i = 0; i < slotBusy.length; i++) {
            if (!slotBusy[i]) {
                slotBusy[i] = true;
                return i;
            }
        }
        return -1;
    }

    protected int takeToken() {
        for (int i = 0; i < tokenBusy.length; i++) {
            if (!tokenBusy[i]) {
                tokenBusy[i] = true;
                return i;
            }
        }
        return -1;
    }

    protected void releaseSlot(int index) {
        if (index >= 0 && index < slotBusy.length) {
            slotBusy[index] = false;
        }
    }

    protected void releaseToken(int index) {
        if (index >= 0 && index < tokenBusy.length) {
            tokenBusy[index] = false;
        }
    }

    protected boolean hasFreeSlot() {
        for (boolean busy : slotBusy) {
            if (!busy) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasFreeToken() {
        for (boolean busy : tokenBusy) {
            if (!busy) {
                return true;
            }
        }
        return false;
    }
}
