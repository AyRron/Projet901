package asynchrone.tp;

public abstract class Message {

    private String payload;
    private int estampillage;
    private String senderId;

    public Message(String payload, int estampillage, String senderId) {
        this.payload = payload;
        this.estampillage = estampillage;
        this.senderId = senderId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getEstampillage() {
        return estampillage;
    }

    public void setEstampillage(int estampillage) {
        this.estampillage = estampillage;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
}
