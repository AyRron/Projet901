package asynchrone.tp;

public class Recieve extends Message {

    private int destId;

    public Recieve(Object payload, int estampillage, int senderId, int destId) {
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
