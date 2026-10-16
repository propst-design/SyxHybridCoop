package game.battle.thread;

import java.io.IOException;
import java.nio.file.Path;

import game.GAME;
import game.GAME.GameResource;
import game.battle.div.Div;
import game.battle.thread.general.Strategos2000;
import game.battle.thread.order.BattleOrders;
import game.battle.thread.position.DivCentres;
import game.battle.thread.status.BattleStatus;
import game.battle.thread.trajectory.BattleTrajectories;
import game.debug.Profiler;
import init.constant.Config;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.ACTION;
import snake2d.util.misc.ACTION.ACTION_O;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import snake2d.util.sets.LIST;
import view.interrupter.IDebugPanel;

public class BattleThreads extends GameResource{

	public final BattleStatus status = new BattleStatus();
	public final DivCentres centres = new DivCentres();
	public final BattleTrajectories trajs = new BattleTrajectories();
	public final BattleOrders orders = new BattleOrders();
	public final Strategos2000 strat = new Strategos2000();
	private final ArrayList<BattleThread> threads = new ArrayList<BattleThread>(orders, centres, status, trajs, strat);
	private boolean started = false;
	
	public BattleThreads(GAME game) {
		super("BATTLE_THREADS");
		
		GAME.addBeforeGameStarts(new ACTION() {
			@Override
			public void exe() {
				if (!started)
					unpause(true);
			}
		});
		GAME.saver().onAfterLoad(new ACTION_O<Path>() {
			
			@Override
			public void exe(Path t) {
				boolean s = started;
				
				((BattleThread)centres).init();
				((BattleThread)status).init();
				
				if (s) {
					unpause(false);
				}
			}
		});
		
		IDebugPanel.add("battle threads pause", new ACTION() {
			
			@Override
			public void exe() {
				pause();
			}
		});
		IDebugPanel.add("battle threads unpause", new ACTION() {
			
			@Override
			public void exe() {
				if (started)
					pause();
				unpause(true);
			}
		});
	}
	
	public void initAndTeleport(LIST<Div> divs) {
		boolean s = started;
		pause();
		for (Div d : divs) {
			orders.init(d);
		}
		Bitmap1D map = new Bitmap1D(Config.battle().DIVISIONS_PER_BATTLE, false);
		for (Div d : divs) {
			map.set(d.index(), true);
		}
		for (ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (e instanceof Humanoid) {
				Humanoid a = (Humanoid) e;
				if (a.division() != null && map.get(a.division().index()))
					a.teleportAndInitInDiv();
			}
		}
		((BattleThread)centres).init();
		((BattleThread)status).init();
		((BattleThread)trajs).init();
		((BattleThread)strat).init();
		if (s)
			unpause(false);
	}

	
	
	@Override
	protected void update(double ds, Profiler prof) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void save(FilePutter file) {
		boolean started = this.started;
		pause();
		for (BattleThread t : threads) {
			t.save(file);
		}
		file.bool(started);
		if (started)
			unpause(false);
	}

	@Override
	protected void load(FileGetter file) throws IOException {

		for (BattleThread t : threads) {
			t.load(file);
		}
		started = file.bool();
	}
	
	public void pause() {
		started = false;
		for (BattleThread t : threads) {
			t.stop();
		}
		
	}
	
	public void unpause(boolean init) {
		started = true;
		for (BattleThread t : threads) {
			// [COOP-MOD START]
			if (t == strat && coop.battle.CoopBattleBridge.isOpponentHuman()) {
				continue;
			}
			// [COOP-MOD END]
			if (init)
				t.init();
			t.start();
		}
		
	}

	@Override
	protected void loadFail() {
		boolean s = started;
		pause();
		for (BattleThread t : threads) {
			t.init();
		}
		if (s)
			unpause(true);
	}


}
