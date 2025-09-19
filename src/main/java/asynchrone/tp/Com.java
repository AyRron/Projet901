package asynchrone.tp;
import com.google.common.eventbus.Subscribe;

import java.util.HashSet;
import java.util.Set;

import static java.lang.Thread.sleep;


/**
 * Classe représentant un processus communicant dans un système distribué.
 * Gère la messagerie asynchrone et synchrone entre processus via EventBus.
 */
public class Com {

    // Attribus de l'identifiant et de la messagerie

    /** Horloge Lamport. */
    private Lamport lamport;
    /** Nombre total de processus créés. */
    private static int nbProcess = -1;
    /** Nombre total de processus dans le système. */
    private int nbTotalProcess;
    /** Identifiant unique du processus. */
    private int id;
    /** Boîte aux lettres pour stocker les messages reçus. */
    public MailBox mailbox;
    /** Service EventBus pour la communication inter-processus. */
    private EventBusService bus;
    /** Token pour la section critique. */
    private Token token;
    /** État actuel du processus par rapport à la section critique. */
    private EtatSC etatSC;
    /** Indique si le processus est en attente de réception d'un message synchrone. */
    private boolean attenteSync;
    /** Ensemble des identifiants des processus dont on attend la réception d'un message. */
    private Set<Integer> pendingReceives;
    /** Message du Broadcast. */
    private MessageBroadcastSync messageBroadcastSync;
    /** Message du sendToSync. */
    private MessageToSync messageToSync;



    /**
     * Constructeur du processus.
     * Initialise l'EventBus et l'identifiant du processus.
     */
    public Com() {
        this.bus = EventBusService.getInstance();
        this.bus.registerSubscriber(this);
        this.nbTotalProcess = Process.maxNbProcess;
        this.mailbox = new MailBox();
        this.nbProcess++;
        this.id = nbProcess;
        this.messageBroadcastSync = null;
        this.messageToSync = null;
        this.etatSC = EtatSC.NULL;
        this.attenteSync = false;
        this.pendingReceives = new HashSet<>();
        this.lamport = new Lamport();

        System.out.println(this.id);
        System.out.println(this.id == (this.nbTotalProcess)-1);

        if (this.id == (this.nbTotalProcess-1)) {
            this.token = new Token(this.nbTotalProcess);
            this.token.next();
            sendToken(this.token);
        };
    }

    // ----------------------- Message Broadcast Asynchrone -----------------------
    /**
     * Méthode appelée lors de la réception d'un message broadcast asynchrone.
     * @param m message broadcast reçu
     */
    @Subscribe
    public void onBroadcast(MessageBroadcast m) {
        if (m.getSender() != this.id) {
            this.lamport.inc_clock(m.getEstampillage());
            this.mailbox.add(m);
        }
    }

    /**
     * Envoie un message broadcast asynchrone à tous les processus.
     * @param o contenu du message à diffuser
     */
    public void broadcast(Object o) {
        MessageBroadcast message = new MessageBroadcast(o, this.lamport.inc_clock(), id);
        EventBusService.getInstance().postEvent(message);
    }



    // ----------------------- Message To Asynchrone -----------------------
    /**
     * Méthode appelée lors de la réception d'un message direct asynchrone.
     * @param m message direct reçu
     */
    @Subscribe
    public void onMessageTo(MessageTo m) {
        if (m.getDest() == this.id) {
            this.lamport.inc_clock(m.getEstampillage());
            this.mailbox.add(m);
        }
    }

    /**
     * Envoie un message direct asynchrone à un processus cible.
     * @param o contenu du message
     * @param dest identifiant du destinataire
     */
    public void sendTo(Object o, int dest) {
        MessageTo message = new MessageTo(o, this.lamport.inc_clock(), id, dest);
        EventBusService.getInstance().postEvent(message);
    }

    // ----------------------- Message Broadcast Synchrone -----------------------

    /**
     *
     * @return
     */
    public MessageBroadcastSync onBroadcastSync(){
        while (this.messageBroadcastSync == null) {
            try{
                sleep(100);
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
        MessageBroadcastSync res = this.messageBroadcastSync;
        this.messageBroadcastSync = null;
        return res;
    }



    /**
     * Méthode appelée lors de la réception d'un message broadcast synchrone.
     * @param m message broadcast synchrone reçu
     */
    @Subscribe
    public void onBroadcastSyncSystem(MessageBroadcastSync m) {
        if (m.getSender() != this.id) {
            System.out.println("Mon message est: " + m.getPayload());
            this.lamport.inc_clock(m.getEstampillage());
            this.messageBroadcastSync = m;
            sendReceive(m.getSender());
        }
    }

    /**
     * Envoie un message broadcast synchrone à tous les processus.
     * @param o contenu du message à diffuser
     * @return le contenu du message envoyé
     */
    public void broadcastSync(Object o) {
        MessageBroadcastSync message = new MessageBroadcastSync(o, this.lamport.inc_clock(), id);
        EventBusService.getInstance().postEvent(message);

        // Initialiser la liste des destinataires attendus (sauf soi-même)
        pendingReceives.clear();
        for (int i = 0; i < nbTotalProcess; i++) {
            if (i != id) pendingReceives.add(i);
        }
        attenteSync = true;
    }

    // ----------------------- Message de Receive -----------------------

    @Subscribe
    private synchronized void onReceive(Receive m) {
        if (m.getDest() == this.id) {
            pendingReceives.remove(m.getSender());
            if (pendingReceives.isEmpty()) {
                attenteSync = false; // Tous les Receive reçus
                notifyAll();
            }
        }
    }


    private synchronized void sendReceive(int dest) {
        Receive message = new Receive(null, 1, id, dest);
        EventBusService.getInstance().postEvent(message);
    }


    private synchronized void waitSyncComplete() {
        while (attenteSync) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ----------------------- Message To Synchrone -----------------------

    public MessageToSync receivFromSync() {
        while (messageToSync == null){
            try{
                sleep(100);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
        MessageToSync res = messageToSync;
        messageToSync = null;
        return res;
    }



    @Subscribe
    public void recevFromSyncSystem(MessageToSync m) {
        if (m.getSender() != this.id) {
            System.out.println("Mon message est: " + m.getPayload());
            this.lamport.inc_clock(m.getEstampillage());
            this.messageToSync = m;
            sendReceive(m.getSender());
        }
    }

    /**
     * Envoie un message direct synchrone à un processus cible.
     * Bloque jusqu'à réception de la réponse.
     * @param o contenu du message
     * @param dest identifiant du destinataire
     */
    public void sendToSync(Object o, int dest) {
        MessageToSync message = new MessageToSync(o, this.lamport.inc_clock(), id, dest);
        EventBusService.getInstance().postEvent(message);

        // On attend le Receive du destinataire
        pendingReceives.clear();
        pendingReceives.add(dest);
        attenteSync = true;

        waitSyncComplete();
    }


    // ----------------------- Synchronize -----------------------
    public void synchronize() {
        // Le processus appelant envoie un message de synchronisation à tous
        broadcastSync("SYNC_REQUEST");

        // Il attend que tous les processus répondent (bloquant)
        waitSyncComplete();

        // Une fois toutes les réponses reçues, il débloque tous les processus
        broadcast("SYNC_RELEASE");
    }

    // Méthode pour gérer la réception des messages de déblocage
    @Subscribe
    public void onSyncRelease(MessageBroadcast m) {
        if (m.getSender() != this.id && "SYNC_RELEASE".equals(m.getPayload())) {
            // Si on reçoit le message SYNC_RELEASE, on débloque
            synchronized(this) {
                notifyAll(); // Réveiller les threads en attente
            }
        }
    }



    // ----------------------- Token ------------------------
    @Subscribe
    private void onToken(Token t) {
        if (t.getDest() == this.id) {
            System.out.println(this.id);
            this.token = t;

            if (this.etatSC == EtatSC.REQUEST) {
                this.etatSC = EtatSC.SC;
            }
            while (this.etatSC == EtatSC.SC) {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            /*System.out.println("Je sors de la section critique " + this.id);*/
            try{
                sleep(100);
                token.next();
                sendToken(token);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }



            if (this.etatSC == EtatSC.RELEASE) {
                this.etatSC = EtatSC.NULL;
            }
        }
    }

    private void sendToken(Token t) {
        EventBusService.getInstance().postEvent(t);
        this.token = null;
    }

    // ----------------------- Barrières Bloquantes -----------------------

    public void requestSC() {
        this.etatSC = EtatSC.REQUEST;
        while (this.etatSC != EtatSC.SC) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void releaseSC() {
        this.etatSC = EtatSC.RELEASE;
    }



    // ----------------------- Getters & Setters -------------------------
    /**
     * Retourne l'identifiant du processus.
     * @return identifiant du processus
     */
    public int getId() {
        return id;
    }

    /**
     * Retourne le nombre de messages présents dans la boîte aux lettres.
     * @return nombre de messages reçus
     */
    public int getNbMessages() {
        return mailbox.size();
    }

}
