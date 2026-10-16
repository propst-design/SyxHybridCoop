package game.battle.formation;

import game.GAME;
import game.battle.Armies;
import game.battle.Army;
import game.battle.div.Div;
import game.battle.formation.DivDeployer.DivDeployB;
import game.battle.thread.order.BattleOrderTask;
import init.constant.C;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.VectorImp;
import snake2d.util.map.MAP_BOOLEAN;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import util.GUTIL;
import util.rendering.RenderData;

public class DivDeployerUser {

	private final ArrayList<DivDeployB> all;
	private final ArrayList<DivDeployB> selection;
	public final DivDeployer deployer;
	private final int clampM = ~((C.TILE_SIZE-1)>>2);
	
	private ArrayList<DivDeployB> tmp = new ArrayList<>(Armies.DIVISIONS);
	private ArrayList<DivDeployB> selected = new ArrayList<>(Armies.DIVISIONS);
	private final VectorImp vec = new VectorImp();
	private static final ArrayList<Div> tmp2 = new ArrayList<>(1);
	private final DivFormationImp fTmp = new DivFormationImp();
	private final BattleOrderTask task = new BattleOrderTask();
	public DivDeployerUser(LIST<Army> armies) {
		int size = Armies.DIVISIONS;
		all = new ArrayList<>(size);
		selection = new ArrayList<>(size);
		for (Army a : armies) {
			for (Div d : a.divisions()) {
				DivDeployB dep = new DivDeployB();
				dep.div = d;
				all.add(dep);
			}
		}
		this.deployer = new DivDeployer(GUTIL.pathTools()) {
			@Override
			protected boolean isDeployable(int px, int py, Army a) {
				return !blocked(px, py, a);
			};
		};
	}
	
	protected boolean blocked(int x, int y, Army a) {
		return false;
	}
	
	public boolean render(SPRITE_RENDERER ren, LIST<Div> divs, int x1, int x2, int y1, int y2, RenderData data) {
		x1 += (~clampM+1)/2;
		y1 += (~clampM+1)/2;
		x2 += (~clampM+1)/2;
		y2 += (~clampM+1)/2;
		
		x1 &= clampM;
		y1 &= clampM;
		x2 &= clampM;
		y2 &= clampM;
		
		selection.clear();
		for (Div d : divs) {
			if (d.menNrOf() == 0)
				continue;
			DivDeployB dep = all.get(d.index());
			dep.div = d;
			selection.add(dep);
		}
		
		LIST<DivDeployB> result = init(selection, x1, x2, y1, y2);
		boolean dep = false;
		for (DivDeployB b : result) {
			DivFormationImp d = deployer.deploy(b.div.info, b.div.menNrOf(),  b.div.settings().formation, b.x1, b.y1, b.dx, b.dy, b.width, GAME.ARMIES().player());
			if (d != null)
				dep = true;
			DivRenderer.render(ren, d, data);
		}
		return dep;
	}
	
	public void render(SPRITE_RENDERER ren, DivFormationImp d, RenderData data) {
		DivRenderer.render(ren, d, data);
	}

	public static void addSecretBlocker(MAP_BOOLEAN block) {
		
	}
	
	public boolean deploy(LIST<Div> divs, int x1, int x2, int y1, int y2) {
		selection.clear();
		
		x1 += (~clampM+1)/2;
		y1 += (~clampM+1)/2;
		x2 += (~clampM+1)/2;
		y2 += (~clampM+1)/2;
		
		x1 &= clampM;
		y1 &= clampM;
		x2 &= clampM;
		y2 &= clampM;
		
		for (Div d : divs) {
			if (d.menNrOf() == 0)
				continue;
			DivDeployB dep = all.get(d.index());
			dep.div = d;
			selection.add(dep);
		}
		
		LIST<DivDeployB> result = init(selection, x1, x2, y1, y2);
		boolean dep = false;
		for (DivDeployB b : result) {
			DivFormationImp d = deployer.deploy(b.div.info, b.div.menNrOf(), b.div.settings().formation, b.x1, b.y1, b.dx, b.dy, b.width, b.div.army());
			
			if (d != null) {
				dep = true;
				b.div.order().dest.set(d);
				task.move(b.div);
				b.div.order().task.set(task); 
				// [COOP-MOD START]
				if (coop.battle.CoopBattleBridge.isInLiveBattle()) {
					coop.battle.CoopBattleBridge.broadcastDivisionOrder((short) b.div.index(), d.start().x(), d.start().y(), 0, b.div.settings().mustering());
				}
				// [COOP-MOD END]
			}
			
			
		}
		return dep;
	}
	
	public void deploy(Div div, int x1, int x2, int y1, int y2) {
		tmp2.clear();
		tmp2.add(div);
		deploy(tmp2, x1, x2, y1, y2);
	}
	
	public void deploy(Div div, int dx, int dy) {

		
		div.order().dest.get(fTmp);
		DivFormationImp d = deployer.deploy(
				div.info,
				div.menNrOf(), 
				fTmp.formation(), 
				fTmp.start().x()+dx, fTmp.start().y()+dy, 
				fTmp.dx(), fTmp.dy(), fTmp.width(), GAME.ARMIES().player());
		
		if (d != null && d.deployed() != 0) {
			task.move(div);
			div.order().dest.set(d);
			div.order().task.set(task); 
			// [COOP-MOD START]
			if (coop.battle.CoopBattleBridge.isInLiveBattle()) {
				coop.battle.CoopBattleBridge.broadcastDivisionOrder((short) div.index(), d.start().x(), d.start().y(), 0, div.settings().mustering());
			}
			// [COOP-MOD END]
		}
		
	
	}

	public boolean isBlocked(int x, int y, int tileSize, Army a) {
		return DivPlacability.pixelIsBlocked(x, y, tileSize, GAME.ARMIES().player()) && !blocked(x, y, a);
	}
	
	private LIST<DivDeployB> init(LIST<DivDeployB> divs, int x1, int x2, int y1, int y2) {

		
		selected.clear();
		double distFull = vec.set(x1, y1, x2, y2);
		{
//			int baseSize = C.TILE_SIZEH;
//			int steps = (int) Math.ceil(distFull / baseSize);
//			double stepX = vec.nX() * baseSize;
//			double stepY = vec.nY() * baseSize;
//
//			steps = getSteps(steps, stepX, stepY, x1, y1, baseSize);
//			distFull = steps * baseSize;
			selected.clear();
			if (divs.size() * C.TILE_SIZE > distFull) {
				return selected;
			}
		}
		
		
		
		double menTotal = 0;
		{
			
			tmp.clear();
			for (DivDeployB d : divs) {
				menTotal += d.div.menNrOf();
				if (d.div.menNrOf() == 0)
					continue;
				tmp.add(d);

			}
		}
		{
			while (tmp.size() > 0) {
				double smallesD = Double.MAX_VALUE;
				int s = -1;
				for (int i = 0; i < tmp.size(); i++) {
					RECTANGLE d = tmp.get(i).div.position().body();
					double ddx = d.cX() - x1;
					double ddy = d.cY() - y1;
					double dist = Math.sqrt(ddx * ddx + ddy * ddy);
					if (dist < smallesD) {
						smallesD = dist;
						s = i;
					}
				}
				DivDeployB dr = tmp.get(s);
				selected.add(dr);
				tmp.remove(s);
			}
		}
		if (selected.isEmpty())
			return selected;
		{
			DIV_FORMATION lastF = selected.get(0).div.settings().formation;
			double distGaps = 0;
			for (DivDeployB d : selected) {
				if (d.div.settings().formation != lastF) {
					lastF = d.div.settings().formation;
					distGaps++;
				}
			}
			distFull-= distGaps*C.TILE_SIZEH;
		}

		
		
		double dx = x1;
		double dy = y1;
		double extra = 0;
		DIV_FORMATION lastF = selected.get(0).div.settings().formation;
		for (DivDeployB d : selected) {
			if (d.div.settings().formation != lastF) {
				lastF = d.div.settings().formation;
				dx += C.TILE_SIZEH*vec.nX();
				dy += C.TILE_SIZEH*vec.nY();
			}
			double dist = distFull*(d.div.menNrOf() / menTotal);
			dist += extra;
			extra = dist-(d.div.settings().formation.size(d.div) * (int)(dist/d.div.settings().formation.size(d.div)));
	
			d.width = (int) (dist);
			d.x1 = (int) dx;
			d.y1 = (int) dy;
			d.dx = vec.nX();
			d.dy = vec.nY();
		
			dx += (d.div.settings().formation.size(d.div) * (int)(dist/d.div.settings().formation.size(d.div)))*vec.nX();
			dy += (d.div.settings().formation.size(d.div) * (int)(dist/d.div.settings().formation.size(d.div)))*vec.nY();
		}
		


		return selected;
	}
	
	public void stop(LIST<Div> divs) {
//		for (Div d : divs) {
//			d.orders().lock(5);
//			if (d.orders().current().deployed() > 0 && d.orders().destination().deployed() > 0) {
//				d.order().destination.set(d.position());
//				d.orders().destination().copy(d.orders().current());
//				d.orders().path.clear();
//			}
//			d.orders().unlock();
//		}
	}


}
