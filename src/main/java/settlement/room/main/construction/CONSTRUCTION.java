package settlement.room.main.construction;

import static settlement.main.SETT.ROOMS;

import settlement.main.SETT;
import settlement.room.food.farm.ROOM_FARM;
import settlement.room.main.ROOMS;
import settlement.room.main.Room;
import settlement.room.main.TmpArea;
import settlement.room.main.furnisher.Furnisher;
import settlement.room.main.furnisher.FurnisherItem;
import settlement.room.main.util.RoomInit;
import settlement.room.main.util.RoomState;
import settlement.tilemap.TILE_FIXABLE;
import settlement.tilemap.terrain.TBuilding;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Coo;
import snake2d.util.datatypes.Rec;
import snake2d.util.map.MAP_BOOLEAN;
import util.gui.misc.GBox;
import view.main.VIEW;

public final class CONSTRUCTION {

	final ConstructionBlueprint construction;
	private final Coo ctmp = new Coo();
	public CONSTRUCTION(ROOMS m){
		construction = new ConstructionBlueprint(m);
	}
	
	public MAP_BOOLEAN isser = new MAP_BOOLEAN() {
		
		@Override
		public boolean is(int tx, int ty) {
			return SETT.ROOMS().map.blueprint.get(tx, ty) == construction;
		}
		
		@Override
		public boolean is(int tile) {
			return SETT.ROOMS().map.blueprint.get(tile) == construction;
		}
	};
	
	public void breakIt(TmpArea area, ConstructionInit init, int tx, int ty) {
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep() && !coop.cmd.CoopLockstepCoordinator.isApplyingRemoteCommand()) {
			coop.cmd.RoomCommandPacket.interceptBreakIt(area, init, tx, ty);
			area.clear();
			return;
		}
		// [COOP-MOD END]
		if (init.b.resources() == 0 && !init.b.mustBeIndoors() && !init.b.needFlooring()) {
			area.clear();
			return;
		}
		
		ConstructionData.dBroken.set(area, tx, ty, 1);
		ConstructionData.dFloored.set(area, tx, ty, 0);
		FurnisherItem it = SETT.ROOMS().fData.item.get(tx, ty);
		if (it != null) {
			COORDINATE c = SETT.ROOMS().fData.itemX1Y1(tx, ty, ctmp);
			if (c != null) {
				for (int y = 0; y < it.height(); y++) {
					for (int x = 0; x < it.width(); x++) {
						if (it.get(x, y) != null) {
							int dx = x + c.x();
							int dy = y + c.y();
							if (area.is(dx, dy)) {
								ConstructionData.dBroken.set(area, dx, dy, 1);
								
							}
						}
					}
				}
			}
		}
		
		
		pcreate(area, init);
	}
	
	public void createClean(TmpArea area, ConstructionInit init) {
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep() && !coop.cmd.CoopLockstepCoordinator.isApplyingRemoteCommand()) {
			coop.cmd.RoomCommandPacket.interceptCreateClean(area, init);
			area.clear();
			return;
		}
		// [COOP-MOD END]
		for (COORDINATE c : area.body()) {
			if (!area.is(c))
				continue;
			SETT.ROOMS().data.set(area, c.x(), c.y(), 0);
		}
		pcreate(area, init);
	}
	
	public void createWithConstructionData(TmpArea area, ConstructionInit init) {
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep() && !coop.cmd.CoopLockstepCoordinator.isApplyingRemoteCommand()) {
			coop.cmd.RoomCommandPacket.interceptCreateWithConstructionData(area, init);
			area.clear();
			return;
		}
		// [COOP-MOD END]
		for (COORDINATE c : area.body()) {
			if (!area.is(c))
				continue;
			int d = ConstructionData.dData.get(c);
			SETT.ROOMS().data.set(area, c.x(), c.y(), 0);
			ConstructionData.dData.set(area, c, d);
		}
		pcreate(area, init);
	}
	
	private void pcreate(TmpArea area, ConstructionInit init) {
		
		if (init.b.resources() == 0 && !init.b.mustBeIndoors() && !init.b.needFlooring()) {
			RoomInit i = new RoomInit(init.b.blue(), 0);
		
			ppCreate(area, i, init.b, init.upgrade, init.state);
		
			
		}else {
			construction.create(area, init);
		}
		area.clear();
	}
	
	private static Rec tmp = new Rec();
	
	static void ppCreate(TmpArea a, RoomInit init, Furnisher blueprint, int upgrade, RoomState state) {
		
		int x1 = a.mx();
		int y1 = a.my();
		tmp.set(a);
		blueprint.create(a, init);
		a.clear();
		
		Room r = SETT.ROOMS().map.get(x1, y1);
		r.upgradeSet(x1, y1, upgrade);
		if (state != null) {
			state.apply(SETT.ROOMS().map.get(x1, y1), x1, y1);
		}
		
		if (r != null) {
			for (COORDINATE c : tmp) {
				if (r.isSame(x1, y1, c.x(), c.y()) && SETT.TERRAIN().get(c) instanceof TILE_FIXABLE) {
					((TILE_FIXABLE)SETT.TERRAIN().get(c)).getTerrain(c.x(), c.y()).placeFixed(c.x(), c.y());;
				}
			}
		}
		
	}
	
	public boolean isRepair(int tx, int ty) {
		ConstructionInstance i = construction.get(tx, ty);
		return (i != null && i.broken);
	}

	public void construct(int tx, int ty) {
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep() && !coop.cmd.CoopLockstepCoordinator.isApplyingRemoteCommand()) {
			coop.cmd.RoomCommandPacket.interceptConstruct(tx, ty);
			return;
		}
		// [COOP-MOD END]
		construction.construct(tx, ty);
		
	}
	
	public TBuilding structure(int tx, int ty) {
		Room room = ROOMS().map.get(tx, ty);
	
		if (room instanceof ConstructionInstance) {
			return ((ConstructionInstance)room).structure();
		}
		return null;
	}
	
	public int instances(){
		return construction.all.size();
	}
	
	public int area(boolean countFarm) {
		int a = 0;
		for (ConstructionInstance i : construction.all) {
			if (i.blueprint == null)
				continue;
			if (countFarm && i.blueprint.blue() instanceof ROOM_FARM)
				a += 0.1*i.area();
			else
				a += i.area();
		}
		
		return a;
	}
	
	public void renderButt(SPRITE_RENDERER r, int x1, int cy, int ins) {
		if (ins >= 0 && ins < construction.all.size()) {
			ConstructionInstance i = construction.all.get(ins);
			construction.hoverer.renderButt(i, r, x1, cy);
		}
	}
	
	public void hoverButt(GBox box, int ins) {
		if (ins >= 0 && ins < construction.all.size()) {
			ConstructionInstance i = construction.all.get(ins);
			construction.hoverer. hover(box, i, i.mX(), i.mY());
			SETT.OVERLAY().add(i.mX(), i.mY());
		}
	}
	
	public void clickButt(int ins) {
		if (ins >= 0 && ins < construction.all.size()) {
			ConstructionInstance i = construction.all.get(ins);
			VIEW.s().getWindow().centererTile.set(i.body().cX(), i.body().cY());
		}
	}

	public RoomState state(int tx, int ty) {
		if (isser.is(tx, ty)) {
			ConstructionInstance ins = (ConstructionInstance) SETT.ROOMS().map.get(tx, ty);
			return ins.state;
		}
		return null;
	}
	
}
