package asynchrone.tp;

public class Token {

    private int nbProcess;
    private int destId;

    public Token(int nbProcess) {
        this.nbProcess = nbProcess;
        this.destId = nbProcess;
    }

    public int getDest() {
        return destId;
    }

    public void next(){
        destId = (destId + 1) % nbProcess;
    }
}
