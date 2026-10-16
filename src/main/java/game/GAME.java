package game;

import java.io.IOException;

import game.audio.AUDIO;
import game.battle.Armies;
import game.battle.thread.BattleThreads;
import game.battle.util.BattleUtil;
import game.boosting.superb.SuperBoostables;
import game.boosting.tmp.TmpBoosting;
import game.debug.Profiler;
import game.event.engine.EVENT_HANDLER;
import game.events.EVENTS;
import game.faction.FACTIONS;
import game.faction.player.Player;
import game.nobility.NOBLES;
import game.raiding.RAIDING;
import game.save.GameSaver;
import game.save.Savable;
import game.time.Intervals;
import game.time.TIME;
import game.tourism.TOURISM;
import game.values.GCOUNTS;
import init.INIT;
import init.paths.PATHS;
import init.race.RACES;
import init.settings.S;
import init.sprite.SPRITES;
import script.ScriptEngine;
import settlement.main.SETT;
import snake2d.CORE;
import snake2d.CORE.GlJob;
import snake2d.LOG;
import snake2d.TextureHolder;
import snake2d.util.file.Alloc;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.ACTION;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.ArrayListGrower;
import util.GUTIL;
import util.spritecomposer.Initer;
import view.interrupter.IDebugPanel;
import view.main.VIEW;
import world.WORLD;

public class GAME{
	
	private static TextureHolder texture;
	private static GAME game;
	public final static GameSpeed SPEED = new GameSpeed(); 
	
	private final ArrayListGrower<ACTION> onUpdateFinish = new ArrayListGrower<>();
	private final ArrayListGrower<ACTION> onGameInited = new ArrayListGrower<>();
	private final ArrayListGrower<ACTION> onViewInited = new ArrayListGrower<>();
	private final ArrayListGrower<ACTION> onBeforeGameStarts = new ArrayListGrower<>();
	
	private final GameSaver saver;
	private final Intervals intervals;
	private final AUDIO audio;
	
	private final TmpBoosting boostingTmp;
	private final SuperBoostables boostingSuper;
	
	private final Armies battle;
	private final BattleThreads battleThreads;
	private final BattleUtil battleutil;
	
	private final SETT settlement;
	private final WORLD world;

	private final FACTIONS factions;
	private final EVENTS events;
	private final EVENT_HANDLER event;
	private final RAIDING raiders;
	private final ScriptEngine script;
	private final NOBLES nobilities;
	private final GCOUNTS counts;
	
	private int updateI = 0;
	// [COOP-MOD START]
	private double tickAccum = 0.0;
	// [COOP-MOD END]
	private final int version;
	private Profiler profiler = Profiler.DUMMY;
	private boolean achieving = true;
	
	private final VIEW view;
	
	private GAME(GameSpec spec) throws IOException{
		CORE.disposeClient();
		GameDisposable.disposeAll();
		this.version = spec.version;
		this.saver = new GameSaver(this);
		this.saver.add(new Savable("GAME") {
			
			@Override
			protected void save(FilePutter file) {
			
				file.bool(game.achieving);
				SPEED.save(file);
				file.i(updateI());
				
			}
			
			@Override
			protected void load(FileGetter file) throws IOException {

				achieving = file.bool();

				
				SPEED.load(file);
				
				updateI = file.i();
				
			}
		});
		
		
		game = this;
		CORE.checkIn();
		script = new ScriptEngine(spec.scripts);
		CORE.checkIn();
		
		audio = new AUDIO(null);
		
		INIT init = new INIT();
		
		new SPRITES(this);
		
		new GUTIL();
		
		CORE.checkIn();
		new TIME();
		CORE.checkIn();
		
		audio.init();
		
		intervals = new Intervals();
		CORE.checkIn();
		
		boostingTmp = new TmpBoosting(this);
		boostingSuper = new SuperBoostables(this);
		CORE.checkIn();
		
		settlement = new SETT();
		CORE.checkIn();
		
		battle = new Armies(this);
		battleThreads = new BattleThreads(game);
		
		battleutil = new BattleUtil(this);
		
		
		CORE.checkIn();
		counts = new GCOUNTS();
		CORE.checkIn();
		

		
		RACES.expand();
		world = new WORLD(spec.wx, spec.wy);
		
		raiders = new RAIDING();
		
		CORE.checkIn();
		events = new EVENTS();
		new TOURISM();
		CORE.checkIn();
		nobilities = new NOBLES();
		factions = new FACTIONS();
		CORE.checkIn();
		SPEED.clear();
		CORE.checkIn();
		
		script.init.initBeforeGameInited();
		
		event = new EVENT_HANDLER();
		
		
		for (Savable s : init.finish()) {
			saver.addSpecialSaver(s);
		}
		
		for (ACTION a : onGameInited)
			a.exe();
		
		view = new VIEW(GAME.this);
		
		script.init(null);
		
		IDebugPanel.add("Profile", new ACTION() {
			
			@Override
			public void exe() {
				if (profiler == Profiler.DUMMY)
					profiler = Profiler.LIVE;
				else
					profiler = Profiler.DUMMY;
			}
		});
		
		for (ACTION a : onViewInited)
			a.exe();
		
		
		
	}
	
	public static VIEW create(String... scripts) {
		return create(GameSpec.get(scripts));
	}
	
	public static VIEW create(GameSpec spec) {
		LOG.ln("NEW GAME " + "Game version: " + VERSION.VERSION_STRING);
		new GlJob() {
			@Override
			public void doJob() {
				
				texture = new Initer() {
					
					@Override
					public void createAssets() throws IOException {
						CORE.getSoundCore().stopAllSounds();
						CORE.getSoundCore().disposeSounds();
						CORE.checkIn();
						new GAME(spec);
						CORE.checkIn();
						
						
					}
				}.get("game", PATHS.textureSize(), SETT.THEIGHT);
			}
		}.perform();
		return game.view;
	}
	
	public static void update(double seconds) {
		
		SPEED.speedSet(1.0);
		game.update(seconds, 0);
	}
	
	public void update(double ds, double slowDown){
		
//		if (game.battle.poll()) {
//			RES.sound().update(0, ds, ds);
//			SPEED.tmpPause();
//			return;
//		}
		
		audio.update(ds);
		game.profiler.logStart(game);
		double ods = ds;
		
		float max = (float) (1.0/16);
		
		double speed = SPEED.update(slowDown);
		
		
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep() ||
		    coop.net.CoopSessionManager.getInstance().getSessionState() == coop.net.CoopSessionManager.SessionState.BATTLE_SUBSTATE) {
			if (speed == 0) {
				for (GameResource s : GameResource.all) {
					if (s.isBattle || !VIEW.b().isActive())
						s.update(0, game.profiler);
				}
				game.updateI++;
			} else {
				ds *= speed;
				saver.autoSave(ds);
				game.tickAccum += ds;
				while (game.tickAccum >= max) {
					if (!coop.cmd.CoopLockstepCoordinator.canAdvanceTick(game.updateI)) {
						break;
					}

					coop.cmd.CoopLockstepCoordinator.preTick(game.updateI);

					snake2d.util.rnd.RND.inSimStep = true;
					snake2d.util.rnd.RND.simThread = Thread.currentThread();
					try {
						for (GameResource s : GameResource.all) {
							if (s.isBattle || !VIEW.b().isActive())
								s.update(max, game.profiler);
						}
					} finally {
						snake2d.util.rnd.RND.inSimStep = false;
					}

					// Synchronous deterministic path batch drain
					if (SETT.PATH() != null && SETT.PATH().thread != null) {
						SETT.PATH().thread.drainDeterministicBatch(64);
					}

					coop.cmd.CoopLockstepCoordinator.postTick(game.updateI);

					game.updateI++;
					game.tickAccum -= max;
				}
			}
		} else {
		// [COOP-MOD END]
		if (speed == 0) {
			for (GameResource s : GameResource.all) {
				if (s.isBattle || !VIEW.b().isActive())
					s.update(0, game.profiler);
			}
			game.updateI++;
		}else {
			ds *= speed;
			saver.autoSave(ds);
			int fulls = (int) (ds/max);
			double last = ds - fulls*max;
			
			for (int i = 0; i < fulls; i++){
				for (GameResource s : GameResource.all) {
					if (s.isBattle|| !VIEW.b().isActive())
						s.update(max, game.profiler);
				}
				game.updateI++;
			}
			
			if (last > 0) {
				for (GameResource s : GameResource.all) {
					if (s.isBattle || !VIEW.b().isActive())
						s.update(last, game.profiler);
				}
				game.updateI++;
			}
		}
		// [COOP-MOD START]
		}
		// [COOP-MOD END]
		
		for (ACTION a : onUpdateFinish)
			a.exe();
		
		game.profiler.logEnd(game);
		game.profiler.log();
		game.saver.autoSave(ods);
		
	}
	
	public void afterTick() {
		for (GameResource s : GameResource.all) {
			s.afterTick();
		}

	}
	
	public static void setGameStart() {
		game.updateI = -1;
		for (ACTION a : game.onBeforeGameStarts)
			a.exe();
	}
	
	public static Armies ARMIES(){
		return game.battle;
	}
	
	public static BattleThreads BATTLE_THREADS(){
		return game.battleThreads;
	}

	public static WORLD world(){
		return game.world;
	}
	
	public static SETT s(){
		return game.settlement;
	}

	public static Intervals intervals(){
		return game.intervals;
	}
	
	public static FACTIONS factions() {
		return game.factions;
	}
	
	public static int updateI() {
		return game.updateI;
	}
	
	public static ScriptEngine script() {
		return game.script;
	}
	
	public static Player player() {
		return FACTIONS.player();
	}
	
	public static EVENTS events() {
		return game.events;
	}
	
	public static TextureHolder texture() {
		return texture;
	}
	
	public static NOBLES NOBLE() {
		return game.nobilities;
	}
	
	public static RAIDING raiders() {
		return game.raiders;
	}
	
	public static EVENT_HANDLER EVENT() {
		return game.event;
	}
	
	public static BattleUtil battle() {
		return game.battleutil;
	}

	public static TmpBoosting BOOST() {
		return game.boostingTmp;
	}
	
	public static SuperBoostables BOOSTS() {
		return game.boostingSuper;
	}

	public static GameSaver saver() {
		return game.saver;
	}
	
	
	public static GCOUNTS count() {
		return game.counts;
	}
	
	public static void Notify(CharSequence s) {
	    if (S.get().developer && S.get().debug) {
			SPEED.speedSet(0);
		    System.out.println();
		    System.out.println("SYX NOTIFICATION: " + s);
		    StackTraceElement[] trace =  new RuntimeException().getStackTrace();
		    int l = trace.length-1;
		    for (; l>= 0; l--) {
		    	StackTraceElement e = trace[l];
		    	if (e.getClassName() == GAME.class.getName())
		            break;
		        if (e.getClassName().startsWith("snake2d"))
		            break;
		    }
		    for (int i = 1; i <= l; i++) {
		        System.out.println("    " + trace[i]);
		    }
		    
		    System.out.println();
	    }
	}
	
	public static void Notify(Object s) {
	    Notify(""+s);
	}
	
	public static void Error(String s) {
	    if (S.get().developer || S.get().debug) {
	    	Warn(s);
	    }else {
	    	throw new RuntimeException(s);
	    }
		
	}
	
	public static void Warn(String s) {
	    if (S.get().developer || S.get().debug) {
	    	SPEED.speedSet(0);
	    	LOG.err("SYX WARNING: " + s);
		    StackTraceElement[] trace =  new RuntimeException().getStackTrace();
		    for (StackTraceElement e : trace) {
		        if (e.getClassName() == GAME.class.getName())
		            continue;
		        if (e.getClassName().startsWith("snake2d"))
		            continue;
		        LOG.err("    " + e);
		    }    
	    }
		
	}
	
	public static void WarnLight(String s) {
	    if (S.get().developer || S.get().debug) {
	    	LOG.err("SYX WARNING: " + s);
	    }
	}
	
	public static void Error(CharSequence s) {
	    Error(""+s);
		
	}
	
	public static int version() {
		return game.version;
	}
	
	public abstract static class GameResource extends Savable{
		
		protected final boolean isBattle;
		
		private static final ArrayList<GameResource> all = new ArrayList<GameResource>(16);
		static {
			new GameDisposable() {
				
				@Override
				protected void dispose() {
					all.clear();
				}
			};
		}
		
		
		protected GameResource(String key) {
			this(key, true);
			
		}

		protected GameResource(String key, boolean isBattle) {
			super(key);
			GAME.saver().add(this);
			all.add(this);
			this.isBattle = isBattle;
			
		}

		protected void afterTick() {
			
		}
		protected abstract void update(double ds, Profiler prof);
		
	}

	public static boolean achieving() {
		return game.achieving;
	}
	
	public static void achieve(boolean a) {
		game.achieving = a;
	}
	
	public static class Cache {
		
		int upI;
		private final int ticks;
		
		public Cache(int ticks){
			upI = -ticks;
			this.ticks = ticks;
		}
		
		public boolean shouldAndReset() {
			if (Math.abs(GAME.updateI()-upI) > ticks) {
				upI = GAME.updateI();
				return true;
			}
			return false;
		}
		
		public void reset() {
			upI = GAME.updateI()-ticks;
		}
		
	}
	
	public static void addAfterUpdate(ACTION action) {
		game.onUpdateFinish.add(action);
	}
	
	public static void addOnInit(ACTION action) {
		game.onGameInited.add(action);
	}
	
	public static void addOnViewInit(ACTION action) {
		game.onViewInited.add(action);
	}
	
	public static void addBeforeGameStarts(ACTION action) {
		game.onBeforeGameStarts.add(action);
	}
	
}
