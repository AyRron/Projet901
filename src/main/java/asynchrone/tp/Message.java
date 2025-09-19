package asynchrone.tp;

public abstract class Message {

    private Object payload;
    private int estampillage;
    private int senderId;
    private boolean isSystemMessage;

    public Message(Object payload, int estampillage, int senderId) {
        this(payload, estampillage, senderId, false);
    }

    public Message(Object payload, int estampillage, int senderId, boolean isSystemMessage) {
        this.payload = payload;
        this.estampillage = estampillage;
        this.senderId = senderId;
        this.isSystemMessage = isSystemMessage;
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

    public int getSender() {
        return senderId;
    }

    public void setSender(int senderId) {
        this.senderId = senderId;
    }

    public boolean isSystemMessage() {
        return isSystemMessage;
    }

    public void setSystemMessage(boolean systemMessage) {
        isSystemMessage = systemMessage;
    }
}
