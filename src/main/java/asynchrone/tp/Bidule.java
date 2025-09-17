package asynchrone.tp;



public class Bidule {
	private String machin=null;

	public Bidule(String machin){
		this.machin = machin;
	}

	public String getMachin(){
		return this.machin;
	}

	@Override
	public String toString(){
		return "Ga Bu Zo Meu: " + this.machin;
	}
}
