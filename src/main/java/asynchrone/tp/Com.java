package asynchrone.tp;

import com.google.common.eventbus.Subscribe;

import java.util.List;

public class Com {

    private static int nbProcess = -1;
    private int id;
    public MailBox mailbox = new MailBox();
    private EventBusService bus;

    public Com() {
        this.bus = EventBusService.getInstance();
        this.bus.registerSubscriber(this);
        this.nbProcess++;
        this.id = nbProcess;
    }

    public void inc_clock() {
        // TODO Auto-generated method stub

    }


    @Subscribe
    public void onBroadcast(BroadcastMessage m) {
        if (m.getSender() != this.id) {
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
            mailbox.add(m);
/*
            System.out.println("");
            System.out.println("Réception d'un message privé");
            System.out.println(Thread.currentThread().getName() + " receives message to: " + m.getPayload() + " pour process " + this.id + " avec l'estampille " + m.getEstampillage());
*/
        }
    }

    public void broadcast(Object o) {
        BroadcastMessage message = new BroadcastMessage(o, 1, id);
        EventBusService.getInstance().postEvent(message);
    }

    public void sendTo(Object o, int dest) {
        MessageTo message = new MessageTo(o, 1, id, dest);
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
}
