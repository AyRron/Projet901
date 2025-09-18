package asynchrone.tp;

import com.google.common.eventbus.Subscribe;

import java.util.List;

public class Com {

    private int nbProcess = 0;
    private int id;
    public MailBox mailbox;
    private EventBusService bus;

    public Com() {
        this.nbProcess++;
    }

    public void inc_clock() {
        // TODO Auto-generated method stub

    }


    @Subscribe
    public void onBroadcast(BroadcastMessage m) {
        if (m.getSenderId() != this.id) {
            mailbox.add(m);
            System.out.println(Thread.currentThread().getName() + " receives broadcast: " + m.getPayload() + " pour process " + this.id + " avec l'estampille " + m.getEstampillage());
        }
    }

    @Subscribe
    public void onMessageTo(MessageTo m) {
        if (m.getSender() == this.id) {
            mailbox.add(m);
            System.out.println(Thread.currentThread().getName() + " receives message to: " + m.getPayload() + " pour process " + this.id + " avec l'estampille " + m.getEstampillage());
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

    public void setId(int id) {
        this.id = id;
    }
}
