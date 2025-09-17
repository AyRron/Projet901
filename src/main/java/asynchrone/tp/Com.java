package asynchrone.tp;

public class Com {

    private int nbProcess = 0;
    private int id;
    private EventBusService bus;

    public Com() {
        this.nbProcess++;
    }

    public void inc_clock() {
        // TODO Auto-generated method stub

    }

    public void broadcast(Object o) {
        BroadcastMessage message = new BroadcastMessage(o, 1, id);
        EventBusService.getInstance().postEvent(message);

    }

    public void sendTo(Object o, int dest) {
        // TODO Auto-generated method stub

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
