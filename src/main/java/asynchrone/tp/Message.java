package asynchrone.tp;

public class Message {

    private String payload;
    private int estampillage;

    public Message(String payload, int estampillage) {
        this.payload = payload;
        this.estampillage = estampillage;
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
}
