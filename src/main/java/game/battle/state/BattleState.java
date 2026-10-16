package game.battle.state;

import static world.WORLD.THEIGHT;
import static world.WORLD.TWIDTH;

import java.nio.file.Path;

import game.GAME;
import game.battle.div.Div;
import game.save.GameLoader;
import init.constant.C;
import init.paths.PATHS;
import init.type.CAUSE_LEAVES;
import init.type.HCLASSES;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.throne.THRONE;
import settlement.stats.STATS;
import snake2d.Errors;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;
import snake2d.util.gui.GuiSection;
import snake2d.util.sets.LIST;
import util.gui.misc.GBox;
import util.text.Dic;
import view.battle.UIBattleResult;
import view.interrupter.Interrupter;
import view.main.VIEW;
import view.sett.ui.minimap.UIMinimapSettConfig;
import view.subview.GameWindow;
import world.WORLD;
import world.battle.WBattles;
import world.battle.spec.BATTLE_RESULT;
import world.map.regions.centre.WCentre;
import world.map.road.WTRAV;

public class BattleState {

	private boolean deploying = true;
	private boolean concluded = false;
	private final Rec deploymentTiles = new Rec();
	private double throneTimer = 0;
	public static final int throneMax = 60*5;
	private final BattleStateExiter resolve;
	private final Path saveFile;
	
	public static String debugLoad = "__battledebug";
	
	public static void setGenerate(BattleStateExiter resolve, BattleStateSpec spec) {
		
		save("__beforeBattle");
		if (!PATHS.local().save().exists("__battle"))
			PATHS.local().save().create("__battle");
		BattleState s = new BattleState(resolve, PATHS.local().save().get("__battle"));		
		
		

		new BattleStateGenerator().generate(s, spec, s.deploymentTiles);
		save("__battle");
		s.view();
		VIEW.b().getWindow().centererTile.set(THRONE.coo());
		VIEW.b().getWindow().zoomoutmax();
		
	}
	
	public static void setLoaded(BattleStateExiter resolve, Path saveFile, boolean deploy) {
		BattleState s = new BattleState(resolve, saveFile);		
		s.view();
	}
	
	private BattleState(BattleStateExiter resolve, Path saveFile){
		GAME.BATTLE_THREADS().pause();
		this.saveFile = saveFile;
		this.resolve = resolve;
		deploying = true;
		throneTimer = throneMax;
		
		DIR d = DIR.get(THRONE.coo(), SETT.TWIDTH/2, SETT.TWIDTH/2);
		
		deploymentTiles.set(
				SETT.TILE_BOUNDS.cX()+d.next(-2).x()*SETT.TWIDTH/2, SETT.TILE_BOUNDS.cX()+d.next(3).x()*SETT.TWIDTH/2, 
				SETT.TILE_BOUNDS.cY()+d.next(-2).y()*SETT.TWIDTH/2, SETT.TILE_BOUNDS.cY()+d.next(3).y()*SETT.TWIDTH/2);
		deploymentTiles.makePositive();
		view();
		VIEW.b().getWindow().centererTile.set(THRONE.coo());
		VIEW.b().getWindow().zoomoutmax();
		
	}
	
	private void view() {
		VIEW.messages().hideAll();
		GAME.SPEED.speedSet(0);
		VIEW.b().activate(this);
	}
	
	private static void save(String name) {
		if (PATHS.local().save().exists(name))
			PATHS.local().save().delete(name);
		if (GAME.saver().save(name) == null)
			throw new Errors.DataError(name, PATHS.local().save().get(name));
		
		
	}
	
	
	public void reloadBattle() {
		new GameLoader(saveFile){
			
			@Override
			public void doAfterSet() {
				GAME.BATTLE_THREADS().pause();
				concluded = false;
				deploying = true;
				
				throneTimer = throneMax;

				VIEW.b().activate(BattleState.this);
				VIEW.b().getWindow().centererTile.set(THRONE.coo());
				VIEW.b().getWindow().zoomoutmax();
				GAME.SPEED.speedSet(0);
				
			}
			
		}.set();
	}
	
	public double throneTimer() {
		return throneTimer;
	}
	
	public boolean deploying() {
		return deploying;
	}
	
	public void deploy() {
		deploying = false;
		GAME.setGameStart();
	}
	
	public RECTANGLE deploymentBounds() {
		return deploymentTiles;
	}
	
	
	void liveResolve(boolean retreat, boolean win) {
		BATTLE_RESULT res = BATTLE_RESULT.VICTORY;
		if (throneTimer<= 0)
			res = BATTLE_RESULT.DEFEAT;
		else if (retreat)
			res = BATTLE_RESULT.RETREAT;
		else if (!win)
			res = BATTLE_RESULT.DEFEAT;
		
		int eDeaths = STATS.POP().COUNT.leaves().get(CAUSE_LEAVES.SLAYED().index()).statistics(HCLASSES.OTHER()).get(null);
		int pLosses = STATS.POP().COUNT.leaves().get(CAUSE_LEAVES.SLAYED().index()).statistics(HCLASSES.CITIZEN()).get(null);
		// [COOP-MOD START]
		if (coop.battle.CoopBattleBridge.isOpponentHuman()) {
			coop.battle.CoopBattleBridge.onBattleConcluded(res, pLosses, eDeaths);
		}
		// [COOP-MOD END]
		resolve.exit(res, pLosses, eDeaths);
	}
	
	
	public void liveRetreat() {
		liveResolve(true, false);
	}
	
	public int liveRetreatLosses() {
		int am = (int) Math.ceil(GAME.ARMIES().enemy().men()*WBattles.retreatPenalty);
		for (Div d : GAME.ARMIES().player().divisions()) {
			if (d.status().isFighting()) {
				am += d.menNrOf()*0.5;
			}
		}
		if (am > GAME.ARMIES().player().men()) {
			am = GAME.ARMIES().player().men();
		}
		return am;
	}
	
	public void update(double ds) {
		

		if (concluded)
			return;
		
		if (deploying)
			return;
		
		if (GAME.ARMIES().player().men() == 0 || GAME.ARMIES().enemy().men() == 0) {
			if (GAME.ARMIES().player().men() == 0)
				new ILiveConclude(Dic.¤¤Defeat, false, false);
			else if (GAME.ARMIES().enemy().men() == 0)
				new ILiveConclude(Dic.¤¤Victory, false, true);
			concluded = true;
			return;
		}
		
		LIST<ENTITY> es = SETT.ENTITIES().fillTiles(THRONE.coo().x()-4, THRONE.coo().y()-4, 8, 8);
		
		double tt = throneTimer - ds;
		throneTimer = throneMax;
		for (ENTITY e : es) {
			if (e instanceof Humanoid) {
				Humanoid h = (Humanoid) e;
				if (h.indu().hType().isHostile() && h.division() != null) {
					throneTimer = tt;
					break;
				}
			}
		}
		
		if (throneTimer <= 0) {
			new ILiveConclude(Dic.¤¤Defeat, false, false);
			concluded = true;
			return;
		}
		
		
	}
	
	final class ILiveConclude extends Interrupter{

		

		private final GameWindow window = new GameWindow(C.DIM(), SETT.PIXEL_BOUNDS, 0);
		private final GuiSection section;
		
		ILiveConclude(CharSequence title, boolean retreat, boolean win){
			pin();
			persistantSet();
			
			section = new UIBattleResult(title) {

				@Override
				protected void close() {
					hide();
					liveResolve(retreat, win);
				}
				
			};
			window.copy(VIEW.b().getWindow());
			GAME.BATTLE_THREADS().pause();
			VIEW.inters().manager.add(this);
			section.body().centerIn(C.DIM());
			
		}

		@Override
		protected boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
			section.hover(mCoo);
			return true;
		}

		@Override
		protected void mouseClick(MButt button) {
			if (button == MButt.LEFT)
				section.click();
		}

		@Override
		protected void hoverTimer(GBox text) {
			section.hoverInfoGet(text);
		}

		@Override
		protected boolean render(Renderer r, float ds) {
			section.render(r, ds);
			GAME.s().render(r, ds, window, UIMinimapSettConfig.NORMAL);
			return false;
		}

		@Override
		protected boolean update(float ds) {
			GAME.SPEED.speedSet(1);
//			timer += ds;
//			
//			if (timer >= 10) {
//				hide();
//				liveResolve(retreat, win);
//			}
			return true;
		}
		
	}
	
	public static boolean okWorldTile(int tx, int ty, DIR eDir) {
		return okWorldTile(tx, ty) && okWorldTile(tx+eDir.x(), ty+eDir.y());
	}
	
	private static boolean okWorldTile(int tx, int ty) {
		
		
		if (tx-WCentre.TILE_DIM/2 < 0 || ty-WCentre.TILE_DIM/2 < 2 || tx + WCentre.TILE_DIM/2 >= TWIDTH() || ty+WCentre.TILE_DIM/2 >= THEIGHT())
			return false;
		for (int di = 0; di < DIR.ALLC.size(); di++) {
			DIR d = DIR.ALLC.get(di);
			if (WORLD.MOUNTAIN().is(tx, ty))
				return false;
			if (WORLD.WATER().isBig.is(tx, ty)) {
				return false;
			}
			
			if (!WTRAV.isGoodLandTile(tx+d.x(), ty+d.y()))
				return false;
			if (d != DIR.C && !WTRAV.can(tx, ty, d, false))
				return false;
		}
		return true;
	}
	
	
}
