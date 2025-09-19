package asynchrone.tp;
import com.google.common.eventbus.Subscribe;

import java.util.HashSet;
import java.util.Set;

import static java.lang.Thread.sleep;

/**
 * Classe représentant un processus communicant dans un système distribué.
 *
 * Cette classe implémente un système de communication inter-processus utilisant
 * le pattern EventBus de Google Guava. Elle fournit des mécanismes pour :
 * - La messagerie asynchrone (broadcast et point-à-point)
 * - La messagerie synchrone avec accusés de réception
 * - La synchronisation globale des processus
 * - La gestion d'une section critique basée sur un token ring
 * - L'horloge logique de Lamport pour l'ordonnancement des événements
 *
 * Le processus maintient une boîte aux lettres (MailBox) pour stocker les messages
 * reçus et utilise un système d'état pour gérer l'accès à la section critique.
 *
 * @author Cyril Pitar
 * @author Mathis Feltrin
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
     * Constructeur du processus communicant.
     *
     * Initialise tous les composants nécessaires au fonctionnement du processus :
     * - Enregistrement au service EventBus pour recevoir les messages
     * - Création de la boîte aux lettres (MailBox)
     * - Attribution d'un identifiant unique
     * - Initialisation de l'horloge de Lamport
     * - Configuration de l'état initial pour la section critique
     * - Création et envoi du token si c'est le dernier processus créé
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
     * Gestionnaire d'événement pour la réception d'un message broadcast asynchrone.
     *
     * Cette méthode est automatiquement appelée par EventBus lors de la réception
     * d'un MessageBroadcast. Elle filtre les messages pour ignorer ceux envoyés
     * par le processus lui-même, met à jour l'horloge de Lamport et stocke
     * le message dans la boîte aux lettres.
     *
     * @param m le message broadcast reçu
     */
    @Subscribe
    public void onBroadcast(MessageBroadcast m) {
        if (m.getSender() != this.id) {
            this.lamport.inc_clock(m.getEstampillage());
            this.mailbox.add(m);
        }
    }

    /**
     * Envoie un message broadcast asynchrone à tous les processus du système.
     *
     * Cette méthode diffuse un message à tous les processus sans attendre
     * d'accusé de réception. Le message est estampillé avec l'horloge de Lamport
     * mise à jour et publié via EventBus.
     *
     * @param o le contenu du message à diffuser (peut être de n'importe quel type)
     */
    public void broadcast(Object o) {
        MessageBroadcast message = new MessageBroadcast(o, this.lamport.inc_clock(), id);
        EventBusService.getInstance().postEvent(message);
    }



    // ----------------------- Message To Asynchrone -----------------------
    /**
     * Gestionnaire d'événement pour la réception d'un message point-à-point asynchrone.
     *
     * Cette méthode est automatiquement appelée par EventBus lors de la réception
     * d'un MessageTo. Elle vérifie que le message est bien destiné à ce processus,
     * met à jour l'horloge de Lamport et stocke le message dans la boîte aux lettres.
     *
     * @param m le message point-à-point reçu
     */
    @Subscribe
    public void onMessageTo(MessageTo m) {
        if (m.getDest() == this.id) {
            this.lamport.inc_clock(m.getEstampillage());
            this.mailbox.add(m);
        }
    }

    /**
     * Envoie un message point-à-point asynchrone à un processus spécifique.
     *
     * Cette méthode envoie un message directement à un processus désigné
     * sans attendre d'accusé de réception. Le message est estampillé avec
     * l'horloge de Lamport mise à jour.
     *
     * @param o le contenu du message à envoyer
     * @param dest l'identifiant du processus destinataire
     */
    public void sendTo(Object o, int dest) {
        MessageTo message = new MessageTo(o, this.lamport.inc_clock(), id, dest);
        EventBusService.getInstance().postEvent(message);
    }

    // ----------------------- Message Broadcast Synchrone -----------------------

    /**
     * Attend et retourne le prochain message broadcast synchrone reçu.
     *
     * Cette méthode bloque l'exécution jusqu'à ce qu'un message broadcast
     * synchrone soit reçu. Une fois le message récupéré, il est supprimé
     * de la variable d'instance pour éviter les lectures multiples.
     *
     * @return le message broadcast synchrone reçu
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
     * Gestionnaire d'événement pour la réception d'un message broadcast synchrone.
     *
     * Cette méthode traite la réception d'un MessageBroadcastSync en filtrant
     * les messages du processus lui-même, en mettant à jour l'horloge de Lamport,
     * en stockant le message et en envoyant automatiquement un accusé de réception
     * à l'expéditeur.
     *
     * @param m le message broadcast synchrone reçu
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
     * Envoie un message broadcast synchrone à tous les processus du système.
     *
     * Cette méthode diffuse un message à tous les processus et initialise
     * la liste des accusés de réception attendus. Elle ne bloque pas l'exécution
     * mais prépare le système pour attendre les confirmations via waitSyncComplete().
     *
     * @param o le contenu du message à diffuser
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

    /**
     * Gestionnaire d'événement pour la réception d'un accusé de réception.
     *
     * Cette méthode traite les messages Receive qui servent d'accusés de réception
     * pour les communications synchrones. Elle retire l'expéditeur de la liste
     * des accusés attendus et débloque les threads en attente si tous les accusés
     * ont été reçus.
     *
     * @param m le message d'accusé de réception
     */
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


    /**
     * Envoie un accusé de réception à un processus spécifique.
     *
     * Cette méthode est utilisée pour confirmer la réception d'un message
     * synchrone en envoyant un message Receive au processus expéditeur.
     *
     * @param dest l'identifiant du processus destinataire de l'accusé
     */
    private synchronized void sendReceive(int dest) {
        Receive message = new Receive(null, 1, id, dest);
        EventBusService.getInstance().postEvent(message);
    }


    /**
     * Attend que tous les accusés de réception soient reçus pour une opération synchrone.
     *
     * Cette méthode bloque l'exécution du thread appelant jusqu'à ce que tous
     * les processus aient envoyé leur accusé de réception (variable attenteSync
     * devient false).
     */
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

    /**
     * Attend et retourne le prochain message point-à-point synchrone reçu.
     *
     * Cette méthode bloque l'exécution jusqu'à ce qu'un message point-à-point
     * synchrone soit reçu. Une fois le message récupéré, il est supprimé
     * de la variable d'instance.
     *
     * @return le message point-à-point synchrone reçu
     */
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



    /**
     * Gestionnaire d'événement pour la réception d'un message point-à-point synchrone.
     *
     * Cette méthode traite la réception d'un MessageToSync en filtrant les messages
     * du processus lui-même, en mettant à jour l'horloge de Lamport, en stockant
     * le message et en envoyant automatiquement un accusé de réception.
     *
     * @param m le message point-à-point synchrone reçu
     */
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
     * Envoie un message point-à-point synchrone à un processus spécifique.
     *
     * Cette méthode envoie un message directement à un processus désigné
     * et bloque l'exécution jusqu'à réception de l'accusé de réception.
     * L'estampillage avec l'horloge de Lamport est automatiquement effectué.
     *
     * @param o le contenu du message à envoyer
     * @param dest l'identifiant du processus destinataire
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
    /**
     * Synchronise tous les processus du système.
     *
     * Cette méthode implémente une barrière de synchronisation globale :
     * 1. Envoie une demande de synchronisation à tous les processus
     * 2. Attend que tous les processus confirment leur réception
     * 3. Envoie un signal de déblocage à tous les processus
     *
     * Tous les processus qui appellent cette méthode seront bloqués jusqu'à
     * ce que tous aient atteint le point de synchronisation.
     */
    public void synchronize() {
        // Le processus appelant envoie un message de synchronisation à tous
        broadcastSync("SYNC_REQUEST");

        // Il attend que tous les processus répondent (bloquant)
        waitSyncComplete();

        // Une fois toutes les réponses reçues, il débloque tous les processus
        broadcast("SYNC_RELEASE");
    }

    /**
     * Gestionnaire d'événement pour les messages de déblocage de synchronisation.
     *
     * Cette méthode traite les messages SYNC_RELEASE qui signalent la fin
     * d'une opération de synchronisation globale. Elle débloque tous les
     * threads en attente de synchronisation.
     *
     * @param m le message broadcast contenant le signal de déblocage
     */
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
    /**
     * Gestionnaire d'événement pour la réception du token de section critique.
     *
     * Cette méthode implémente l'algorithme du token ring pour l'accès exclusif
     * à la section critique :
     * 1. Vérifie que le token est destiné à ce processus
     * 2. Autorise l'entrée en section critique si demandée
     * 3. Attend la fin de l'utilisation de la section critique
     * 4. Transmet le token au processus suivant
     *
     * @param t le token de section critique reçu
     */
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

    /**
     * Envoie le token de section critique au processus suivant.
     *
     * Cette méthode transmet le token via EventBus et libère la référence
     * locale au token pour indiquer que ce processus ne le possède plus.
     *
     * @param t le token à transmettre
     */
    private void sendToken(Token t) {
        EventBusService.getInstance().postEvent(t);
        this.token = null;
    }

    // ----------------------- Barrières Bloquantes -----------------------

    /**
     * Demande l'accès à la section critique.
     *
     * Cette méthode bloque l'exécution jusqu'à ce que le processus obtienne
     * le token et puisse entrer en section critique. L'état du processus
     * passe à REQUEST puis à SC une fois le token reçu.
     */
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

    /**
     * Libère l'accès à la section critique.
     *
     * Cette méthode signale que le processus a terminé son utilisation
     * de la section critique en changeant son état à RELEASE. Le token
     * sera automatiquement transmis au processus suivant.
     */
    public void releaseSC() {
        this.etatSC = EtatSC.RELEASE;
    }



    // ----------------------- Getters & Setters -------------------------
    /**
     * Retourne l'identifiant unique du processus.
     *
     * Cet identifiant est attribué de manière séquentielle lors de la création
     * du processus et permet de distinguer les processus dans le système distribué.
     *
     * @return l'identifiant unique du processus (entier positif)
     */
    public int getId() {
        return id;
    }

    /**
     * Retourne le nombre de messages actuellement stockés dans la boîte aux lettres.
     *
     * Cette méthode permet de connaître le nombre de messages asynchrones
     * (broadcast et point-à-point) en attente de traitement dans la MailBox.
     *
     * @return le nombre de messages en attente dans la boîte aux lettres
     */
    public int getNbMessages() {
        return mailbox.size();
    }

    /**
     * Désinscrit le processus de l'EventBus.
     *
     * Cette méthode doit être appelée lors de l'arrêt du processus pour éviter
     * que les handlers continuent de recevoir des événements après l'arrêt.
     */
    public void unregister() {
        this.bus.unRegisterSubscriber(this);
        // Débloquer les threads en attente lors de l'arrêt
        synchronized(this) {
            this.attenteSync = false;
            notifyAll();
        }
    }

}
