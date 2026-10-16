package snake2d.util.rnd;

import java.util.Random;

import snake2d.Printer;


public class RND {

	private static Random rnd;
	private static int seed;
	
	// [COOP-MOD START]
	public static volatile boolean inSimStep = false;
	public static volatile Thread simThread = null;
	private static final Random uiRnd = new Random();

	private static Random activeRnd() {
		if (inSimStep && (simThread == null || Thread.currentThread() == simThread)) {
			return rnd;
		}
		return uiRnd;
	}
	// [COOP-MOD END]

	static {
		rnd = new Random();
		seed = rnd.nextInt();
		rnd = new Random(seed);
		long seed = rnd.nextLong();
		Printer.ln("[RND] ---Initiating random, seed: " + seed);
		//rnd.setSeed(System.nanoTime());
		//seed stuff
	}
	
	public static int rInt(){
		// [COOP-MOD START]
		return activeRnd().nextInt();
		// [COOP-MOD END]
	}

	public static boolean rBoolean() {
		// [COOP-MOD START]
		return activeRnd().nextBoolean();
		// [COOP-MOD END]
	}
	
	public static int rInt(int max) {
		// [COOP-MOD START]
		return activeRnd().nextInt(max);
		// [COOP-MOD END]
	}

	public static int rInt0(int dist) {
		if (dist == 0)
			return 0;
		// [COOP-MOD START]
		return -dist + activeRnd().nextInt(dist*2 + 1);
		// [COOP-MOD END]
	}
	
	public static float rFloat() {
		// [COOP-MOD START]
		return activeRnd().nextFloat();
		// [COOP-MOD END]
	}
	
	public static float rFloatP(float exponent){
		float res = RND.rFloat();
		while (exponent > 1){
			res *= res;
			exponent --;
		}
		return res;
	}
	
	public static float rFloat(double d) {
		// [COOP-MOD START]
		return (float) (activeRnd().nextFloat()*d);
		// [COOP-MOD END]
	}

	/**
	 * 
	 * @param d
	 * @return get a float from 1-d to 1+d
	 */
	public static float rFloat1(double d) {
		// [COOP-MOD START]
		return (float) ((1.0 -d ) + activeRnd().nextFloat()*d*2.0);
		// [COOP-MOD END]
	}
	
	/**
	 * 
	 * @param d
	 * @return a float f, f >= -d, f < d
	 */
	public static float rFloat0(double d) {
		// [COOP-MOD START]
		return (float) (-d + activeRnd().nextFloat()*d*2.0);
		// [COOP-MOD END]
	}
	
	public static boolean oneIn(int what){
		if (what <= 1)
			return true;
		// [COOP-MOD START]
		return activeRnd().nextInt(what) == what-1;
		// [COOP-MOD END]
	}
	
	public static boolean oneIn(double what){
		return oneIn((int) what);
	}
	
	public static boolean oneInD(double what){
		return rFloat()*what < 1;
	}

	public static short rShort(int upperBound) {
		return (short) rInt(upperBound);
	}
	
	public static short rShort() {
		return rShort(Short.MAX_VALUE);
	}

	public static long rLong() {
		// [COOP-MOD START]
		return activeRnd().nextLong();
		// [COOP-MOD END]
	}
	
	public static float rExpo(){
		float f = rFloat();
		return f*f;
	}
	
	public static double rSign() {
		if (rBoolean())
			return 1.0;
		return -1.0;
	}

	public static int seed() {
		return seed;
	}
	
	public static void setSeed(int seed) {
		RND.seed = seed;
		rnd.setSeed(seed);
	}
	
//	public static final class UniqueGen {
//		
//		private long state = System.nanoTime();  // or any seed
//
//		public int nextUnique(int maxExclusive) {
//		    state = (state * 1664525L + 1013904223L) & 0xFFFFFFFFL;
//		    return (int)(state % maxExclusive);
//		}
//
//	}
//	
//	public static void main(String[] args) {
//		UniqueGen g = new UniqueGen();
//		for (int i = 0; i < 20; i++) {
//		    System.out.println(g.nextUnique(21));  // 0..10
//		}
//	}
		
}
