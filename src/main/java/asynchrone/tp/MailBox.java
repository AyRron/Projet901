package asynchrone.tp;
import java.util.*;

public class MailBox {
    private List<Message> messages;

    public MailBox() {
        this.messages = new ArrayList<>();
    }

    public synchronized void addMessage(Message message) {
        messages.add(message);
        notifyAll(); // Notify any waiting threads that a new message has arrived
    }
}
