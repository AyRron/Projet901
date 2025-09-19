package asynchrone.tp;

public class Lamport {

    private int horloge;

    public Lamport() {
        this.horloge = 0;
    }


    public int inc_clock(){
        this.horloge += 1;
        return this.horloge;
    }

    public int inc_clock(int In){
        this.horloge = Math.max(this.horloge, In) + 1;
        return this.horloge;
    }

    public int getHorloge() {
        return horloge;
    }
}