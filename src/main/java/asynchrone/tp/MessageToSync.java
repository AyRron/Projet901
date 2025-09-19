package asynchrone.tp;

public class MessageToSync extends Message {

    private int destId;

    public MessageToSync(Object payload, int estampillage, int senderId, int destId) {
        super(payload, estampillage, senderId);
        this.destId = destId;
    }

    public int getSender() {
        return super.getSender();
    }

    public int getDest() {
        return destId;
    }
}
