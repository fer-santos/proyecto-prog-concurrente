package synch;

import problemas.SyncMethod;
import problemas.VirtualAssistantsSim.AssistantAgent;




public interface VirtualAssistantsStrategy extends SynchronizationStrategy {

    SyncMethod getMethod();

    int acquirePriorityToken(AssistantAgent agent) throws InterruptedException;

    int acquireServerSlot(AssistantAgent agent) throws InterruptedException;

    void releaseResources(AssistantAgent agent, int tokenIndex, int slotIndex);
}
