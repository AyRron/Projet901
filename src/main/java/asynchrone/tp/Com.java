package asynchrone.tp;

import com.google.common.eventbus.Subscribe;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

public class Com {

    private int id;

    // Numérotation automatique sans variables de classe (utilise synchronized)
    private static int nextProcessId = 0;
    private static final Object processIdLock = new Object();
    public MailBox mailbox = new MailBox();
    private EventBusService bus;

//    private int lamportClock;
    private Lamport lamportClock = new Lamport();
    private Semaphore clockSemaphore;

    // Section critique distribuée
    private volatile boolean hasToken;
    private volatile SCState criticalSectionState;

    // Synchronisation globale
    private static int barrierCounter = 0;
    private static CountDownLatch barrierLatch = null;
    private static final Object barrierLock = new Object();

    // Communication synchrone
    private static int syncIdGenerator = 0;
    private static final Object syncIdLock = new Object();
    private ConcurrentHashMap<Integer, CountDownLatch> syncLatches = new ConcurrentHashMap<>();

    // Système heartbeat
    private Thread heartbeatThread;
    private static ConcurrentHashMap<Integer, Long> lastHeartbeats = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_INTERVAL = 1000; // 1 seconde
    private static final long HEARTBEAT_TIMEOUT = 3000;  // 3 secondes

    /**
     * Gets the next available process ID in a thread-safe manner.
     * @return The next process ID
     */
    private static synchronized int getNextProcessId() {
        synchronized (processIdLock) {
            return nextProcessId++;
        }
    }

    public Com() {
        this.bus = EventBusService.getInstance();
        this.bus.registerSubscriber(this);
        this.id = getNextProcessId();

//        this.lamportClock = 0;
        this.clockSemaphore = new Semaphore(1);

        // Initialisation section critique
        this.hasToken = (this.id == 0); // Processus 0 commence avec le jeton
        this.criticalSectionState = SCState.NULL;

        // S'enregistrer auprès du gestionnaire de jeton centralisé
        TokenManager.getInstance().registerCommunicator(this.id, this);

        // Démarrer le système heartbeat
        startHeartbeat();
    }

    /**
     * Increments the Lamport clock manually. This method can be called by the process
     * when it wants to increment its logical clock for internal events.
     * The access to the clock is protected by a semaphore to avoid concurrent modifications.
     */
    public void inc_clock() {
        try {
            clockSemaphore.acquire();
//            lamportClock++;
            this.lamportClock.inc_clock();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    /**
     * Updates the Lamport clock when receiving a message (estampIn operation).
     * Only updates for user messages, system messages don't affect the clock.
     * @param receivedTimestamp The timestamp received in the message
     */
    private void updateClockOnReceive(int receivedTimestamp, boolean isSystemMessage) {
        if (isSystemMessage) {
            return; // System messages don't affect Lamport clock
        }

        try {
            clockSemaphore.acquire();
//            lamportClock = Math.max(lamportClock, receivedTimestamp) + 1;
            this.lamportClock.inc_clock(receivedTimestamp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    /**
     * Gets the current timestamp and increments the clock for sending a message (estampOut operation).
     * Only increments for user messages, system messages don't affect the clock.
     * @param isSystemMessage Whether the message is a system message
     * @return The current timestamp to attach to the message
     */
    private int getTimestampForSend(boolean isSystemMessage) {
        if (isSystemMessage) {
            return 0; // System messages don't use real timestamps
        }

        try {
            clockSemaphore.acquire();
//            lamportClock++;
            this.lamportClock.inc_clock();
            return this.lamportClock.getHorloge();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return this.lamportClock.getHorloge();
        } finally {
            clockSemaphore.release();
        }
    }

    /**
     * Gets the current value of the Lamport clock.
     * @return The current Lamport clock value
     */
    public int getLamportClock() {
        try {
            clockSemaphore.acquire();
            return this.lamportClock.getHorloge();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return this.lamportClock.getHorloge();
        } finally {
            clockSemaphore.release();
        }
    }


    @Subscribe
    public void onBroadcast(BroadcastMessage m) {
        if (m.getSender() != this.id) {
            updateClockOnReceive(m.getEstampillage(), m.isSystemMessage());
            mailbox.add(m);
/*
            System.out.println("");
            System.out.println("Réception d'un message par broadcast");
            System.out.println(Thread.currentThread().getName() + " receives broadcast: " + m.getPayload() + " pour process " + this.id + " avec l'estampille " + m.getEstampillage());
*/
        }
    }

    @Subscribe
    public void onMessageTo(MessageTo m) {
        if (m.getDest() == this.id) {
            updateClockOnReceive(m.getEstampillage(), m.isSystemMessage());
            mailbox.add(m);
/*
            System.out.println("");
            System.out.println("Réception d'un message privé");
            System.out.println(Thread.currentThread().getName() + " receives message to: " + m.getPayload() + " pour process " + this.id + " avec l'estampille " + m.getEstampillage());
*/
        }
    }

    /**
     * Sends a broadcast message to all other processes.
     * Updates the Lamport clock before sending (user message).
     * @param o The payload to broadcast
     */
    public void broadcast(Object o) {
        int timestamp = getTimestampForSend(false); // false = user message
        BroadcastMessage message = new BroadcastMessage(o, timestamp, id);
        EventBusService.getInstance().postEvent(message);
    }

    /**
     * Sends a private message to a specific process.
     * Updates the Lamport clock before sending (user message).
     * @param o The payload to send
     * @param dest The destination process ID
     */
    public void sendTo(Object o, int dest) {
        int timestamp = getTimestampForSend(false); // false = user message
        MessageTo message = new MessageTo(o, timestamp, id, dest);
        EventBusService.getInstance().postEvent(message);
    }

    public int getId() {
        return id;
    }

    public int getNbMessages() {
        return mailbox.size();
    }

    public void setId(int id) {
        this.id = id;
    }

    // ========== SECTION CRITIQUE DISTRIBUÉE ==========

    /**
     * Requests access to the critical section. This method blocks until
     * the process obtains the token.
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    public void requestSC() throws InterruptedException {
        criticalSectionState = SCState.REQUEST;
        System.out.println("P" + id + " - État: REQUEST (demande la SC)");

        CountDownLatch latch = TokenManager.getInstance().requestSC(this.id);
        latch.await(); // Wait until token is granted

        criticalSectionState = SCState.SC;
        System.out.println("P" + id + " - État: SC (en section critique)");
    }

    /**
     * Releases the critical section.
     */
    public void releaseSC() {
        criticalSectionState = SCState.RELEASE;
        System.out.println("P" + id + " - État: RELEASE (libère la SC)");

        TokenManager.getInstance().releaseSC(this.id);

        criticalSectionState = SCState.NULL;
        System.out.println("P" + id + " - État: NULL (pas d'intérêt pour la SC)");
    }

    /**
     * Called by TokenManager when this process receives the token.
     * This is an internal method used by the centralized token manager.
     */
    public void receiveToken() {
        hasToken = true;
    }

    /**
     * Checks if this process currently holds the token.
     * @return true if this process has the token
     */
    public boolean hasToken() {
        return hasToken;
    }

    /**
     * Checks if this process is currently in critical section.
     * @return true if in critical section
     */
    public boolean isInCriticalSection() {
        return criticalSectionState == SCState.SC;
    }

    /**
     * Gets the current critical section state.
     * @return The current SCState
     */
    public SCState getCriticalSectionState() {
        return criticalSectionState;
    }

    /**
     * Gets a string representation of the current critical section state.
     * @return String representation of the state
     */
    public String getSCStateString() {
        switch (criticalSectionState) {
            case NULL: return "NULL (pas d'intérêt)";
            case REQUEST: return "REQUEST (demande SC)";
            case SC: return "SC (en section critique)";
            case RELEASE: return "RELEASE (libère SC)";
            default: return "UNKNOWN";
        }
    }

    // ========== SYNCHRONISATION GLOBALE ==========

    /**
     * Synchronizes all processes. This method blocks until all processes
     * in the system have called this method. Uses a barrier pattern.
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    public void synchronize() throws InterruptedException {
        synchronized (barrierLock) {
            barrierCounter++;

            // Si c'est le premier processus à arriver, créer le latch
            if (barrierCounter == 1) {
                barrierLatch = new CountDownLatch(Process.maxNbProcess);
            }

            // Le processus "vote" qu'il est arrivé à la barrière
            if (barrierLatch != null) {
                barrierLatch.countDown();
            }
        }

        // Attendre que tous les processus arrivent
        if (barrierLatch != null) {
            barrierLatch.await();
        }

        // Reset pour la prochaine synchronisation
        synchronized (barrierLock) {
            barrierCounter--;
            if (barrierCounter == 0) {
                barrierLatch = null; // Reset pour la prochaine fois
            }
        }
    }

    // ========== COMMUNICATION SYNCHRONE ==========

    /**
     * Generates a unique synchronous message ID in a thread-safe manner.
     * @return A unique sync ID
     */
    private static int getNextSyncId() {
        synchronized (syncIdLock) {
            return ++syncIdGenerator;
        }
    }

    /**
     * Handles synchronous broadcast messages and their acknowledgments.
     * @param msg The synchronous broadcast message
     */
    @Subscribe
    public void onSyncBroadcast(SyncBroadcastMessage msg) {
        if (msg.isAcknowledgment()) {
            // C'est un accusé de réception
            CountDownLatch latch = syncLatches.get(msg.getSyncId());
            if (latch != null) {
                latch.countDown();
            }
        } else {
            // C'est un message original, le traiter et envoyer l'accusé
            if (msg.getSender() != this.id) {
                updateClockOnReceive(msg.getEstampillage(), msg.isSystemMessage());
                mailbox.add(msg);

                // Envoyer l'accusé de réception
                SyncBroadcastMessage ack = new SyncBroadcastMessage("ACK", getTimestampForSend(false), this.id, msg.getSyncId(), true);
                EventBusService.getInstance().postEvent(ack);
            }
        }
    }

    /**
     * Handles synchronous point-to-point messages and their acknowledgments.
     * @param msg The synchronous message
     */
    @Subscribe
    public void onSyncMessageTo(SyncMessageTo msg) {
        if (msg.getDest() == this.id) {
            if (msg.isAcknowledgment()) {
                // C'est un accusé de réception pour un message qu'on a envoyé
                CountDownLatch latch = syncLatches.get(msg.getSyncId());
                if (latch != null) {
                    latch.countDown();
                }
            } else {
                // C'est un message qu'on reçoit - le mettre dans la mailbox pour recevFromSync
                updateClockOnReceive(msg.getEstampillage(), msg.isSystemMessage());
                mailbox.add(msg); // Ajouter à la mailbox pour que recevFromSync puisse le trouver

                // Envoyer l'accusé de réception
                SyncMessageTo ack = new SyncMessageTo("ACK", getTimestampForSend(false), this.id, msg.getSender(), msg.getSyncId(), true);
                EventBusService.getInstance().postEvent(ack);
            }
        }
    }

    /**
     * Synchronous broadcast. If this process has the specified ID 'from',
     * it sends the object to all other processes and waits for all acknowledgments.
     * If this process doesn't have the specified ID, it waits to receive the message.
     * @param o The payload to broadcast
     * @param from The ID of the process that should send the broadcast
     * @throws InterruptedException If interrupted while waiting
     */
    public void broadcastSync(Object o, int from) throws InterruptedException {
        if (this.id == from) {
            // Ce processus doit envoyer le broadcast et attendre les accusés
            int syncId = getNextSyncId();
            CountDownLatch ackLatch = new CountDownLatch(Process.maxNbProcess - 1); // Tous sauf nous
            syncLatches.put(syncId, ackLatch);

            // Envoyer le message
            int timestamp = getTimestampForSend(false);
            SyncBroadcastMessage syncMsg = new SyncBroadcastMessage(o, timestamp, id, syncId);
            EventBusService.getInstance().postEvent(syncMsg);

            // Attendre les accusés de réception
            ackLatch.await();
            syncLatches.remove(syncId);

        } else {
            // Ce processus doit attendre de recevoir le broadcast de 'from'
            // L'attente se fait automatiquement via onSyncBroadcast qui met le message dans mailbox
            while (true) {
                Message msg = mailbox.getMsg();
                if (msg instanceof SyncBroadcastMessage) {
                    SyncBroadcastMessage syncMsg = (SyncBroadcastMessage) msg;
                    if (syncMsg.getSender() == from && !syncMsg.isAcknowledgment()) {
                        break; // Message reçu
                    } else {
                        // Remettre le message s'il ne correspond pas
                        mailbox.add(msg);
                        Thread.sleep(10);
                    }
                } else {
                    // Remettre le message s'il n'est pas synchrone
                    mailbox.add(msg);
                    Thread.sleep(10);
                }
            }
        }
    }

    /**
     * Sends a synchronous message to a destination and waits for acknowledgment.
     * @param o The payload to send
     * @param dest The destination process ID
     * @throws InterruptedException If interrupted while waiting
     */
    public void sendToSync(Object o, int dest) throws InterruptedException {
        int syncId = getNextSyncId();
        CountDownLatch ackLatch = new CountDownLatch(1);
        syncLatches.put(syncId, ackLatch);

        // Envoyer le message
        int timestamp = getTimestampForSend(false);
        SyncMessageTo syncMsg = new SyncMessageTo(o, timestamp, id, dest, syncId);
        EventBusService.getInstance().postEvent(syncMsg);

        // Attendre l'accusé de réception
        ackLatch.await();
        syncLatches.remove(syncId);
    }

    /**
     * Waits to receive a synchronous message from a specific sender.
     * @param from The sender process ID to wait for
     * @return The received message payload
     * @throws InterruptedException If interrupted while waiting
     */
    public Object recevFromSync(int from) throws InterruptedException {
        // Attendre qu'un message synchrone arrive du bon expéditeur
        while (true) {
            // Vérifier s'il y a déjà un message dans la mailbox du bon expéditeur
            if (!mailbox.isEmpty()) {
                Message msg = mailbox.getMsg();
                if (msg instanceof SyncMessageTo) {
                    SyncMessageTo syncMsg = (SyncMessageTo) msg;
                    if (syncMsg.getSender() == from && !syncMsg.isAcknowledgment()) {
                        // C'est le message qu'on attend !
                        return syncMsg.getPayload();
                    } else {
                        // Pas le bon message, le remettre
                        mailbox.add(msg);
                    }
                } else if (msg != null) {
                    // Pas un message synchrone, le remettre
                    mailbox.add(msg);
                }
            }

            // Attendre un peu avant de réessayer
            Thread.sleep(10);
        }
    }

    // ========== SYSTÈME HEARTBEAT ==========

    /**
     * Starts the heartbeat system that sends periodic heartbeat messages
     * and monitors other processes for failures.
     */
    private void startHeartbeat() {
        // Enregistrer ce processus comme vivant
        lastHeartbeats.put(this.id, System.currentTimeMillis());

        heartbeatThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Envoyer un heartbeat
                    sendHeartbeat();

                    // Vérifier les processus morts
                    checkDeadProcesses();

                    Thread.sleep(HEARTBEAT_INTERVAL);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        heartbeatThread.setName("Heartbeat-P" + id);
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    /**
     * Sends a heartbeat message to all other processes.
     */
    private void sendHeartbeat() {
        HeartbeatMessage heartbeat = new HeartbeatMessage(this.id);
        EventBusService.getInstance().postEvent(heartbeat);
    }

    /**
     * Handles incoming heartbeat messages.
     * @param heartbeat The heartbeat message received
     */
    @Subscribe
    public void onHeartbeat(HeartbeatMessage heartbeat) {
        if (heartbeat.getSender() != this.id) {
            // Mettre à jour le timestamp du processus expéditeur
            lastHeartbeats.put(heartbeat.getSender(), System.currentTimeMillis());
        }
    }

    /**
     * Checks for dead processes and triggers reconfiguration if needed.
     */
    private void checkDeadProcesses() {
        long currentTime = System.currentTimeMillis();
        boolean foundDeadProcess = false;

        for (Integer processId : lastHeartbeats.keySet()) {
            long lastSeen = lastHeartbeats.get(processId);
            if (currentTime - lastSeen > HEARTBEAT_TIMEOUT) {
                System.out.println("Process P" + processId + " detected as DEAD (timeout)");
                lastHeartbeats.remove(processId);
                foundDeadProcess = true;
            }
        }

        if (foundDeadProcess) {
            reconfigureSystem();
        }
    }

    /**
     * Reconfigures the system after a process failure.
     * This would involve renumbering alive processes, but for simplicity,
     * we just print the current alive processes.
     */
    private void reconfigureSystem() {
        System.out.println("=== SYSTEM RECONFIGURATION ===");
        System.out.println("Alive processes: " + lastHeartbeats.keySet());
        System.out.println("Current process count: " + lastHeartbeats.size());
        System.out.println("==============================");

        // Note: Full renumbering would require more complex coordination
        // between all alive processes to agree on new IDs
    }

    /**
     * Gets the list of currently alive process IDs.
     * @return Set of alive process IDs
     */
    public java.util.Set<Integer> getAliveProcesses() {
        return lastHeartbeats.keySet();
    }

    /**
     * Checks if a specific process is alive.
     * @param processId The process ID to check
     * @return true if the process is alive, false otherwise
     */
    public boolean isProcessAlive(int processId) {
        return lastHeartbeats.containsKey(processId);
    }

    /**
     * Cleanup method to be called when the process is stopping.
     * Unregisters from TokenManager and stops threads.
     */
    public void cleanup() {
        // Unregister from TokenManager
        TokenManager.getInstance().unregisterCommunicator(this.id);

        // Stop heartbeat thread
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }

        // Unregister from EventBus
        if (bus != null) {
            bus.unRegisterSubscriber(this);
        }

        System.out.println("Com P" + id + " cleanup completed");
    }
}
