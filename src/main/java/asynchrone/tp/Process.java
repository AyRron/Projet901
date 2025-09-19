package asynchrone.tp;

import com.google.common.eventbus.Subscribe;

public class Process  implements Runnable {
    private Thread thread;
    private boolean alive;
    private boolean dead;
    private Com com;
    public static final int maxNbProcess = 4;
    private int id;

    // Configuration pour choisir le type de test
    private static final String TEST_MODE = "LAMPORT_CLOCK"; // ou "CRITICAL_SECTION" ou "SYNC_COMMUNICATION" ou "LAMPORT_CLOCK"

    public Process(String name){
        this.com = new Com();
        this.id = com.getId();

        this.thread = new Thread(this);
        this.thread.setName(name);
        this.alive = true;
        this.dead = false;
        this.thread.start();
    }

    public String getName() {
        return Thread.currentThread().getName();
    }

    public void run(){
        int loop = 0;

        System.out.println(Thread.currentThread().getName() + " id :" + this.id);

        while(this.alive){
            System.out.println(Thread.currentThread().getName() + " Loop : " + loop);
            try{
                Thread.sleep(500);



                // Choix du test selon la configuration
                if (TEST_MODE.equals("CRITICAL_SECTION")) {
                    runCriticalSectionTest(loop);
                } else if (TEST_MODE.equals("SYNC_COMMUNICATION")) {
                    runSyncCommunicationTest(loop);
                } else if (TEST_MODE.equals("LAMPORT_CLOCK")) {
                    runLamportClockTest(loop);
                }

                // Affichage des messages reçus des autres processus (sauf messages synchrones)
                while (!this.com.mailbox.isEmpty()) {
                    try {
                        Message msg = this.com.mailbox.getMsg();
                        if (msg != null && !msg.isSystemMessage()) {
                            // Ne pas afficher les messages synchrones qui seront traités par recevFromSync
                            if (!(msg.getClass().getSimpleName().startsWith("Sync"))) {
                                System.out.println(this.getName() + " - Message reçu: " + msg.getPayload());
                            } else {
                                // Remettre le message synchrone dans la mailbox pour recevFromSync
                                this.com.mailbox.add(msg);
                                break; // Sortir de la boucle pour éviter une boucle infinie
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                /*if (this.getName() == "P0"){
                    this.com.sendTo("j'appelle 2 et je te recontacte après", 1);

                    this.com.sendToSync("J'ai laissé un message à 2, je le rappellerai après, on se sychronise tous et on attaque la partie ?", 2);
                    this.com.recevFromSync(msg, 2);

                    this.com.sendToSync("2 est OK pour jouer, on se synchronise et c'est parti!",1);

                    this.com.synchronize();

                    this.com.requestSC();
                    if (this.com.mailbox.isEmpty()){
                        print("Catched !");
                        this.com.broadcast("J'ai gagné !!!");
                    }else{
                        msg = this.com.mailbox.getMsg();
                        System.out.println(msg.getSender() + " à eu le jeton en premier");
                    }
                    this.com.releaseSC();

                }
                if (this.getName() == "P1"){
                    if (!this.com.mailbox.isEmpty()){
                        msg = this.com.mailbox.getMsg();
                        this.com.recevFromSync(msg, 0);

                        this.com.synchronize();

                        this.com.requestSC();
                        if (this.com.mailbox.isEmpty()){
                            print("Catched !");
                            this.com.broadcast("J'ai gagné !!!");
                        }else{
                            msg = this.com.mailbox.getMsg();
                            print(str(msg.getSender())+" à eu le jeton en premier");
                        }
                        this.com.releaseSC();
                    }
                }
                if(this.getName() == "P2"){
                    this.com.recevFromSync(msg, 0);
                    this.com.sendToSync("OK", 0);

                    this.com.synchronize();

                    this.com.requestSC();
                    if (this.com.mailbox.isEmpty()){
                        print("Catched !");
                        this.com.broadcast("J'ai gagné !!!");
                    }else{
                        msg = this.com.mailbox.getMsg();
                        print(str(msg.getSender())+" à eu le jeton en premier");
                    }
                    this.com.releaseSC();
                }*/


            }catch(Exception e){
                e.printStackTrace();
            }
            loop++;
        }

        // Cleanup resources before stopping
        this.com.cleanup();

        System.out.println(Thread.currentThread().getName() + " stopped");
        this.dead = true;
    }

    public void waitStoped(){
        while(!this.dead){
            try{
                Thread.sleep(500);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    public void stop(){
        this.alive = false;
    }

    // ========== MÉTHODES DE TEST ==========

    /**
     * Test de la section critique distribuée
     */
    private void runCriticalSectionTest(int loop) {
        try {
            // Synchronisation initiale pour que tous démarrent en même temps
            if (loop == 0) {
                System.out.println(this.getName() + " - Attente synchronisation initiale...");
                this.com.synchronize();
                System.out.println(this.getName() + " - Tous les processus sont synchronisés !");
            }

            // Test de la section critique - chaque processus essaie d'y accéder
            if (loop >= 1 && loop <= 3) {  // 3 tentatives par processus
                System.out.println(this.getName() + " - Demande d'accès à la section critique (tentative " + loop + ")");

                long startTime = System.currentTimeMillis();
                this.com.requestSC();  // SECTION CRITIQUE DEMANDÉE
                long waitTime = System.currentTimeMillis() - startTime;

                System.out.println("*** " + this.getName() + " - ENTRÉE EN SECTION CRITIQUE après " + waitTime + "ms ***");
                System.out.println(this.getName() + " - État SC actuel: " + this.com.getSCStateString());

                // Simulation du travail en section critique
                System.out.println(this.getName() + " - Travail en section critique...");
                Thread.sleep(200);  // Simule du travail

                // Broadcast pour informer les autres qu'on est en SC
                this.com.broadcast(this.getName() + " était en section critique !");

                System.out.println("*** " + this.getName() + " - SORTIE DE SECTION CRITIQUE ***");
                this.com.releaseSC();  // LIBÉRATION DE LA SECTION CRITIQUE

                // Attendre un peu avant la prochaine tentative
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(this.getName() + " - Interruption pendant le test SC");
        }
    }

    /**
     * Test de la communication synchrone
     */
    private void runSyncCommunicationTest(int loop) {
        try {
            // Synchronisation initiale
            if (loop == 0) {
                System.out.println(this.getName() + " - Attente synchronisation initiale...");
                this.com.synchronize();
                System.out.println(this.getName() + " - Tous les processus sont synchronisés !");
                return;
            }

            // Tests de communication synchrone selon les processus
            if (loop == 1) {
                testBroadcastSync();
            } else if (loop == 2) {
                testSendToSync();
            } else if (loop == 3) {
                testRecevFromSync();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(this.getName() + " - Interruption pendant le test sync");
        }
    }

    /**
     * Test du broadcast synchrone
     */
    private void testBroadcastSync() throws InterruptedException {
        if (this.getName().equals("P0")) {
            System.out.println("=== TEST BROADCAST SYNC ===");
            System.out.println(this.getName() + " - Envoi d'un broadcastSync...");
            long startTime = System.currentTimeMillis();

            this.com.broadcastSync("Message broadcast synchrone de P0", 0);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println(this.getName() + " - BroadcastSync terminé après " + duration + "ms");

        } else {
            System.out.println(this.getName() + " - Attente du broadcastSync de P0...");
            this.com.broadcastSync("", 0); // Les autres attendent le message de P0
            System.out.println(this.getName() + " - BroadcastSync reçu de P0");
        }
    }

    /**
     * Test de sendToSync
     */
    private void testSendToSync() throws InterruptedException {
        if (this.getName().equals("P1")) {
            System.out.println("=== TEST SEND TO SYNC ===");
            System.out.println(this.getName() + " - Envoi sendToSync à P2...");
            long startTime = System.currentTimeMillis();

            this.com.sendToSync("Message privé synchrone de P1 à P2", 2);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println(this.getName() + " - SendToSync vers P2 terminé après " + duration + "ms");
        }
    }

    /**
     * Test de recevFromSync
     */
    private void testRecevFromSync() throws InterruptedException {
        if (this.getName().equals("P2")) {
            System.out.println("=== TEST RECEV FROM SYNC ===");
            System.out.println(this.getName() + " - Attente d'un message de P1...");
            long startTime = System.currentTimeMillis();

            Object message = this.com.recevFromSync(1);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println(this.getName() + " - Message reçu de P1 : \"" + message + "\" après " + duration + "ms");
        }
    }

    /**
     * Test de l'horloge de Lamport
     */
    private void runLamportClockTest(int loop) {
        try {
            // Synchronisation initiale
            if (loop == 0) {
                System.out.println(this.getName() + " - Synchronisation initiale pour test Lamport...");
                this.com.synchronize();
                System.out.println(this.getName() + " - Début du test de l'horloge de Lamport");
                return;
            }

            if (loop >= 1 && loop <= 5) {
                // Chaque processus affiche son horloge et envoie des messages
                long timestamp = System.currentTimeMillis();
                System.out.println(this.getName() + " - Loop " + loop + " - Timestamp: " + timestamp + " - Horloge Lamport: " + this.com.getLamportClock());

                if (loop == 1) {
                    // P0 envoie un message à P1, P2 envoie à P3
                    if (this.getName().equals("P0")) {
                        long ts1 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Envoi message à P1 (horloge avant: " + this.com.getLamportClock() + ")");
                        this.com.sendTo("Message de P0 à P1 (loop 1)", 1);
                        long ts2 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge après envoi: " + this.com.getLamportClock());
                    } else if (this.getName().equals("P2")) {
                        long ts1 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Envoi message à P3 (horloge avant: " + this.com.getLamportClock() + ")");
                        this.com.sendTo("Message de P2 à P3 (loop 1)", 3);
                        long ts2 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge après envoi: " + this.com.getLamportClock());
                    }
                }

                if (loop == 2) {
                    // P1 envoie à P2, P3 envoie à P0
                    if (this.getName().equals("P1")) {
                        long ts1 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Envoi message à P2 (horloge avant: " + this.com.getLamportClock() + ")");
                        this.com.sendTo("Message de P1 à P2 (loop 2)", 2);
                        long ts2 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge après envoi: " + this.com.getLamportClock());
                    } else if (this.getName().equals("P3")) {
                        long ts1 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Envoi message à P0 (horloge avant: " + this.com.getLamportClock() + ")");
                        this.com.sendTo("Message de P3 à P0 (loop 2)", 0);
                        long ts2 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge après envoi: " + this.com.getLamportClock());
                    }
                }

                if (loop == 3) {
                    // Broadcast depuis P0
                    if (this.getName().equals("P0")) {
                        long ts1 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Broadcast (horloge avant: " + this.com.getLamportClock() + ")");
                        this.com.broadcast("Broadcast de P0 à tous (loop 3)");
                        long ts2 = System.currentTimeMillis();
                        System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge après broadcast: " + this.com.getLamportClock());
                    }
                }

                if (loop == 4) {
                    // Événements internes simulés
                    long ts1 = System.currentTimeMillis();
                    System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Événement interne (horloge avant: " + this.com.getLamportClock() + ")");
                    this.com.getLamportClock(); // Lecture de l'horloge = événement interne
                    long ts2 = System.currentTimeMillis();
                    System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge après événement interne: " + this.com.getLamportClock());
                }

                if (loop == 5) {
                    // Dernière synchronisation
                    long ts1 = System.currentTimeMillis();
                    System.out.println(this.getName() + " - Timestamp: " + ts1 + " - Synchronisation finale (horloge avant: " + this.com.getLamportClock() + ")");
                    this.com.synchronize();
                    long ts2 = System.currentTimeMillis();
                    System.out.println(this.getName() + " - Timestamp: " + ts2 + " - Horloge finale après synchronisation: " + this.com.getLamportClock());
                }

                Thread.sleep(300); // Petite pause entre les loops
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(this.getName() + " - Interruption pendant le test Lamport");
        }
    }
}