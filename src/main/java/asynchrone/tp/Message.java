package asynchrone.tp;

public abstract class Message {

    private Object payload;
    private int estampillage;
    private int senderId;

    public Message(Object payload, int estampillage, int senderId) {
        this.payload = payload;
        this.estampillage = estampillage;
        this.senderId = senderId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public int getEstampillage() {
        return estampillage;
    }

    public void setEstampillage(int estampillage) {
        this.estampillage = estampillage;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }
}
