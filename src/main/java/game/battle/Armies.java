package game.battle;


import java.io.IOException;

import game.GAME;
import game.GAME.GameResource;
import game.battle.div.Div;
import game.battle.factors.DivFactors;
import game.battle.formation.DivDeployerUser;
import game.battle.setting.BattleSettings;
import game.debug.Profiler;
import game.save.Savable;
import init.constant.C;
import init.constant.Config;
import settlement.entity.EntityIterator;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import snake2d.LOG;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.ACTION;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap2D;
import snake2d.util.sets.LIST;
import util.updating.IUpdater;
import view.interrupter.IDebugPanel;
import view.main.VIEW;

public class Armies extends GameResource{

	public static final int ARMIES = 2;
	public static final int ARMIES_BITS = 0b011;
	public static final int DIVISIONS = ARMIES*Config.battle().DIVISIONS_PER_ARMY;
	private final ArrayList<Div> divisions = new ArrayList<>(DIVISIONS);
	private final ArrayList<ArmyDiv> adivisions = new ArrayList<>(DIVISIONS);
	private final ArrayList<Army> armies = new ArrayList<>(ARMIES);
	private final PrevMen prevMen = new PrevMen();
	public final DivDeployerUser placer;
	
	public final DivisionBanners banners = new DivisionBanners();
	public final TargetMap map = new TargetMap();
	public final DivFactors factors = new DivFactors(this);
	public final BattleSettings settings;
	
	public final ArmySounds sound = new ArmySounds();
	
	public Armies(GAME game) throws IOException{
		super("ARMIES", true);
		
		for (int i = 0; i < ARMIES; i++) {
			
			new Army(armies, divisions);
		}
		
		placer = new DivDeployerUser(armies) {
			@Override
			protected boolean blocked(int x, int y, Army a) {
				if (VIEW.b().state() != null && VIEW.b().state().deploying()) {
					if (a == GAME.ARMIES().player())
						return !VIEW.b().state().deploymentBounds().holdsPoint(x>>C.T_SCROLL, y>>C.T_SCROLL);
					return VIEW.b().state().deploymentBounds().holdsPoint(x>>C.T_SCROLL, y>>C.T_SCROLL);
				}
				return false;
			}
		};
		
		for (Div d : divisions)
			adivisions.add(d);
		
		settings = new BattleSettings(this);
		
		IDebugPanel.add("checkDivisionSpotOrder", new ACTION() {
			
			@Override
			public void exe() {
				
				Bitmap2D check = new Bitmap2D(Config.battle().MEN_PER_DIVISION, Config.battle().DIVISIONS_PER_BATTLE, false);
				
				new EntityIterator.Humans() {
					
					@Override
					protected boolean processAndShouldBreakH(Humanoid h, int ie) {
						if (h.division() != null) {
							check.set(h.division().reporter.positionSpot(h), h.division().index(), true);
						}
						return false;
					}
				}.iterate();
				
				
				
				for (Div d : divisions) {
					
					for (int i = 0; i < d.menNrOf(); i++) {
						if (!check.is(i, d.index())) {
							LOG.ln("errors in division " + d.index());
							break;
						}
					}
					
					
				
					if (d.menNrOf() > 0) {
						
						GAME.Notify(d.index());
						
						for (int i = 0; i < d.menNrOf(); i++) {
							check.set(i, d.index(), false);
						}
						
						new EntityIterator.Humans() {
							
							@Override
							protected boolean processAndShouldBreakH(Humanoid h, int ie) {
								
								for (int i = 0; i < d.menNrOf(); i++) {
									check.set(i, d.index(), false);
								}
								
								if (h.division() == d) {
									if (h.divSpot() != d.reporter.positionSpot(h))
										LOG.ln(h.divSpot() + " -> " + d.reporter.positionSpot(h));
									
									
									
									check.set(h.division().reporter.positionSpot(h), h.division().index(), true);
								}
								
								
								
								return false;
							}
						}.iterate();
						LOG.ln();
						for (int i = 0; i < d.menNrOf(); i++) {
							int pi = d.reporter.positionSpot(i);
							if (i != pi)
								LOG.ln(i + " -> " + pi);
						}
					}
				}
				GAME.Notify("test completed");
				
				
				
			}
		});
		
	}
	
	

	

	public void clear() {
		
		for (Army t : armies)
			t.saver.clear();
		for (ArmyDiv d : adivisions)
			d.clear();
		banners.clear();
		for (int i = 0; i < 4; i++) {
			GAME.ARMIES().divisions.get(i).info.menSet(50);
		}
		
	}
	
	@Override
	protected void save(FilePutter file) {
		
		for (Army t : armies)
			t.saver.save(file);
		for (ArmyDiv d : adivisions)
			d.save(file);
		banners.save(file);
	}
	
	@Override
	protected void load(FileGetter file) throws IOException {
		for (Army t : armies)
			t.saver.load(file);
		for (ArmyDiv d : adivisions)
			d.load(file);
		banners.load(file);
	}
	
	@Override
	protected void loadFail() {
		clear();
	}
	
	private double ti = 0;
	@Override
	protected void update(double ds, Profiler profiler) {

		profiler.logStart(Div.class);
		profiler.logEnd(Div.class);
		profiler.logStart(DivFactors.class);
		factors.update(ds);
		profiler.logEnd(DivFactors.class);
		profiler.logStart(BattleSettings.class);
		settings.update(ds);
		profiler.logEnd(BattleSettings.class);
		
		ti += ds;
		if (ti > 0.1) {
			ti-= 0.1;
			SETT.BATTLE().info.update();
		}
		
		prevMen.update(ds);
	}
	
	public Army player() {
		// [COOP-MOD START]
		if (coop.battle.CoopBattleBridge.isLocalPlayerOpponent()) {
			return armies.get(1);
		}
		// [COOP-MOD END]
		return armies.get(0);
	}
	
	public Army enemy() {
		// [COOP-MOD START]
		if (coop.battle.CoopBattleBridge.isLocalPlayerOpponent()) {
			return armies.get(0);
		}
		// [COOP-MOD END]
		return armies.get(1);
	}

	public Div division(short armyDivisionID) {
		return divisions.get(armyDivisionID);
	}
	
	public LIST<Div> divisions(){
		return divisions;
	}
	
	public LIST<Army> armies(){
		return armies;
	}
	
	public void initAndTeleport(LIST<Div> divs) {
		for (Div d : divs) {
			settings.init(d);
		}
		GAME.BATTLE_THREADS().initAndTeleport(divs);
		for (Div d : divs) {
			factors.init(d);
			prevMen.men[d.index()] = d.menNrOf();
		}
		
	}
	
	public int prevMen(Div div) {
		return prevMen.men[div.index()];
	}
	
	
	private static class PrevMen extends IUpdater {
		
		private int[] men = Alloc.ii(Config.battle().DIVISIONS_PER_BATTLE);
		
		PrevMen(){
			super(Config.battle().DIVISIONS_PER_BATTLE, 10);
			new Savable("BATTLE_DIV_PREVIOUS_MEN") {
				
				@Override
				protected void save(FilePutter file) {
					file.is(men);
				}
				
				@Override
				protected void load(FileGetter file) throws IOException {
					file.is(men);
				}
			};
		}

		@Override
		protected void update(int i, double timeSinceLast) {
			men[i] = GAME.ARMIES().division((short) i).men();
		}
		
	}
	
}
