package synch;

import problemas.VirtualAssistantsSim;

/**
 * Estrategia de sincronización para el problema de Asistentes Virtuales.
 */
public interface VirtualAssistantsStrategy extends SynchronizationStrategy {

    /**
     * Solicita acceso al servidor y a un token de prioridad.
     * Debe bloquear hasta que existan recursos disponibles conforme a la política de la estrategia.
     */
    Grant requestAccess(VirtualAssistantsSim.AssistantContext ctx) throws InterruptedException;

    /**
     * Libera los recursos previamente asignados a un asistente.
     */
    void releaseAccess(VirtualAssistantsSim.AssistantContext ctx, Grant grant);

    /**
     * Información de asignación regresada por las estrategias.
     */
    class Grant {
        public final int slotIndex;
        public final int tokenIndex;

        public Grant(int slotIndex, int tokenIndex) {
            this.slotIndex = slotIndex;
            this.tokenIndex = tokenIndex;
        }
    }
}
