package asynchrone.tp;
import com.google.common.eventbus.Subscribe;

import static java.lang.Thread.sleep;


/**
 * Classe représentant un processus communicant dans un système distribué.
 * Gère la messagerie asynchrone et synchrone entre processus via EventBus.
 */
public class Com {

    // Attribus de l'identifiant et de la messagerie

    /** Nombre total de processus créés. */
    private static int nbProcess = -1;
    /** Identifiant unique du processus. */
    private int id;
    /** Boîte aux lettres pour stocker les messages reçus. */
    public MailBox mailbox = new MailBox();
    /** Service EventBus pour la communication inter-processus. */
    private EventBusService bus;
    /** Token pour la section critique. */
    private Token token;

    private EtatSC etatSC = EtatSC.NULL;


    // Attributs pour gérer la communication synchrone
    /** Réponse reçue lors d'une communication synchrone. */
    private Object syncResponse = null;
    /** Indique si une réponse synchrone a été reçue. */
    private boolean responseReceived = false;
    /** Indique si un message synchrone a été envoyé. */
    private boolean messageSent = false;
    /** Identifiant du processus dont on attend la réponse. */
    private int waitingForResponseFrom = -1;


    /**
     * Constructeur du processus.
     * Initialise l'EventBus et l'identifiant du processus.
     */
    public Com() {
        this.bus = EventBusService.getInstance();
        this.bus.registerSubscriber(this);
        this.nbProcess++;
        this.id = nbProcess;

        System.out.println(this.id);
        System.out.println(this.id == (Process.maxNbProcess)-1);

        if (this.id == (Process.maxNbProcess-1)) {
            this.token = new Token(Process.maxNbProcess);
            token.next();
            sendToken(token);
        };
    }

    // ----------------------- Lamport Clock -----------------------
    /**
     * Incrémente l'horloge logique (Lamport).
     * À implémenter selon le protocole choisi.
     */
    public void inc_clock() {
        // TODO Auto-generated method stub

    }

    // ----------------------- Message Broadcast Asynchrone -----------------------
    /**
     * Méthode appelée lors de la réception d'un message broadcast asynchrone.
     * @param m message broadcast reçu
     */
    @Subscribe
    public void onBroadcast(MessageBroadcast m) {
        if (m.getSender() != this.id) {
            mailbox.add(m);
        }
    }

    /**
     * Envoie un message broadcast asynchrone à tous les processus.
     * @param o contenu du message à diffuser
     */
    public void broadcast(Object o) {
        MessageBroadcast message = new MessageBroadcast(o, 1, id);
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
            mailbox.add(m);
        }
    }

    /**
     * Envoie un message direct asynchrone à un processus cible.
     * @param o contenu du message
     * @param dest identifiant du destinataire
     */
    public void sendTo(Object o, int dest) {
        MessageTo message = new MessageTo(o, 1, id, dest);
        EventBusService.getInstance().postEvent(message);
    }

    // ----------------------- Message Broadcast Synchrone -----------------------
    /**
     * Méthode appelée lors de la réception d'un message broadcast synchrone.
     * @param m message broadcast synchrone reçu
     */
    @Subscribe
    public synchronized void onBroadcastSync(MessageBroadcastSync m) {
        if (m.getSender() != this.id) {
            mailbox.add(m);
            notifyAll();
        }
    }

    /**
     * Envoie un message broadcast synchrone à tous les processus.
     * @param o contenu du message à diffuser
     * @return le contenu du message envoyé
     */
    public synchronized Object broadcastSync(Object o) {
        MessageBroadcastSync message = new MessageBroadcastSync(o, 1, id);
        EventBusService.getInstance().postEvent(message);
        return message.getPayload();
    }

    // ----------------------- Message To Synchrone -----------------------
    /**
     * Méthode appelée lors de la réception d'un message direct synchrone.
     * Bloque le processus jusqu'à réception ou traitement du message.
     * @param dest identifiant du destinataire attendu
     * @return le message reçu ou traité
     */
    @Subscribe
    public synchronized MessageToSync recevFromSync(int dest) {
        if (m.getDest() == this.id) {
            // Traitement du message reçu
            Object response = processMessage(m.getPayload());

            // Envoyer réponse directement à l'émetteur
            MessageToSync responseMsg = new MessageToSync(response, m.getClock() + 1, this.id, m.getSender());
            EventBusService.getInstance().postEvent(responseMsg);

            // Notifier que le message a été traité
            messageSent = true;
            notifyAll();
        } else if (m.getDest() == -1 && m.getSender() == waitingForResponseFrom) {
            // On reçoit la réponse qu'on attendait
            syncResponse = m.getPayload();
            responseReceived = true;
            notifyAll();
        }
    }

    /**
     * Envoie un message direct synchrone à un processus cible.
     * Bloque jusqu'à réception de la réponse.
     * @param o contenu du message
     * @param dest identifiant du destinataire
     * @return la réponse reçue du destinataire
     */
    public synchronized Object sendToSync(Object o, int dest) {
        // Réinitialiser les variables d'état
        syncResponse = null;
        responseReceived = false;
        waitingForResponseFrom = dest;

        // Envoyer le message
        MessageToSync message = new MessageToSync(o, 1, id, dest);
        EventBusService.getInstance().postEvent(message);

        // Attendre la réponse
        while (!responseReceived) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Réinitialiser l'état d'attente
        waitingForResponseFrom = -1;

        return syncResponse;
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

    private synchronized void sendToken(Token t) {
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
