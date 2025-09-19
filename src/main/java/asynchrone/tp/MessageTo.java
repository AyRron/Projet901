package asynchrone.tp;

public class MessageTo extends Message {

    private int destId;

    public MessageTo(Object payload, int estampillage, int senderId, int destId) {
        super(payload, estampillage, senderId);
        this.destId = destId;
    }

    public int getSender() {
        return super.getSender();
    }

    public void setDestId(int destId) {
        this.destId = destId;
    }

    public int getDest() {
        return destId;
    }
}
