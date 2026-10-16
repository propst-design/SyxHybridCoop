package view.tool;

import init.constant.C;
import init.sprite.SPRITES;
import settlement.main.SETT;
import snake2d.MButt;
import snake2d.PathTile;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Coo;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.map.MAP_SETTER;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import util.GUTIL;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.text.D;
import util.text.Dic;
import view.keyboard.KEYS;
import view.keyboard.KeyButt;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.ToolPlacer.placeFunc;

final class PlacableMultiTool extends placeFunc {

	private PlacableMulti placable;
	private int size = 0;
	private final Coo hTile = new Coo();
	private final Coo clickedTile = new Coo();
	private boolean newTile;
	private PLACER_TYPE type = PLACER_TYPE.SQUARE;
	private final PlacerArea a = PlacerArea.self;
	//private boolean pressed;

	{
		D.gInit(this);
	}
	
	private final ArrayList<CLICKABLE> butts = new ArrayList<>(2+PLACER_TYPE.all.size());
	private final CLICKABLE bIncrease = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.plus) {
		{hoverInfoSet(""+KEYS.MAIN().MOD.repr() + Dic.¤¤MouseWheelAdd);}
		@Override
		protected void renAction() {
			activeSet(type.usesSize && size < 15);
		};
		@Override
		protected void clickA() {
			radius(1);
		};
	}, KEYS.MAIN().GROW);
	private final CLICKABLE bDecrease = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.minus) {
		{hoverInfoSet(""+KEYS.MAIN().MOD.repr() + Dic.¤¤MouseWheelAdd);}
		@Override
		protected void renAction() {
			activeSet(type.usesSize && size > 0);
		};
		@Override
		protected void clickA() {
			radius(-1);
		};
	}, KEYS.MAIN().SHRINK);
	private final GButt.Panel[] buttsTypes = new GButt.Panel[PLACER_TYPE.all.size()];
	{
		for (int i = 0; i < buttsTypes.length; i++) {
			PLACER_TYPE t = PLACER_TYPE.all.get(i);
			buttsTypes[i] = new GButt.Panel(t.icon()) {
				{hoverInfoSet(t.name);}
				@Override
				protected void renAction() {
					selectedSet(type == t);
				};
				@Override
				protected void clickA() {
					type = t;
					placable.previous = t;
					clear();
					VIEW.inters().popup.close();
				};
			};
		}
	}
	private final GuiSection typeButts = new GuiSection();
	private final GButt.Panel buttType = new GButt.Panel(SPRITES.icons().m.cancel, D.g("type")) {
		
		@Override
		protected void renAction() {
			replaceLabel(type.icon(), DIR.C);
		};
		
		@Override
		protected void clickA() {
			VIEW.inters().popup.show(typeButts, this);
		};
	};
	
	private void radius(int d) {
		size = CLAMP.i(size+d, 0, 15);
		placable.prevSize = size;
	}
	
	@Override
	void updateHovered(float ds, GameWindow window, boolean pressed) {
		

		newTile |= hTile.set(window.tile());
		if (MButt.RIGHT.isDown())
			clear();
		
		if (type.usesSize) {
			double s = MButt.clearWheelSpin();
			if (KEYS.MAIN().MOD.isPressed() && s != 0) {
				
				if (s > 0) {
					radius(1);
				}else if (s < 0){
					radius(-1);
				}
				MButt.clearWheelSpin();
			}
			
			if (KEYS.MAIN().GROW.consumeClick()) {
				radius(1);
			}else if (KEYS.MAIN().SHRINK.consumeClick()){
				radius(-1);
			}
		}
		
		PlacerArea.self.clear();
		if (type == PLACER_TYPE.FILL) {
			specialFill(hTile.x(), hTile.y(), size, a.set, placable);
		}else if (type.drag && pressed) {
			type.paint(hTile.x(), hTile.y(), clickedTile.x(), clickedTile.y(), size, a.set);
		}else {
			type.paint(hTile.x(), hTile.y(), hTile.x(), hTile.y(), size, a.set);
		}
		

		
		GUTIL.filler().init(this);
		
		
		
		for (COORDINATE c : a.body()) {
			if (a.is(c))
				GUTIL.filler().fill(c);
		}
		
		while (GUTIL.filler().hasMore()) {
			COORDINATE c = GUTIL.filler().poll();
			for (int i = 0; i < DIR.ALL.size(); i++) {
				DIR d = DIR.ALL.get(i);
				int dx = c.x()+d.x();
				int dy = c.y()+d.y();
				if (!SETT.IN_BOUNDS(dx, dy))
					continue;
				if (placable.expandsTo(c.x(), c.y(), dx, dy)) {
					GUTIL.filler().fill(dx, dy);
					a.set.set(dx, dy);
				}
			}
		}
		
		GUTIL.filler().done();
		
		

		if (!type.drag) {
			if (pressed && newTile) {
				place();
			}
		}
		
		newTile = false;
		
		
	}
	
	@Override
	void update(float ds, GameWindow window, boolean pressed) {
		placable.updateRegardless(window, PlacerArea.self);
		super.update(ds, window, pressed);
	}
	
	private void specialFill(int x1, int y1, int size, MAP_SETTER area, PlacableMulti multi) {
		
		if (!SETT.IN_BOUNDS(x1, y1))
			return;
		
		GUTIL.flooder().init(area);
		GUTIL.flooder().pushSmaller(x1, y1, 0);
		while(GUTIL.flooder().hasMore()) {
			PathTile t = GUTIL.flooder().pollSmallest();
			if (t.getValue() > size+1)
				break;
			area.set(t.x(), t.y());
			if (multi.isPlacable(t.x(), t.y(), PlacerArea.self, PLACER_TYPE.FILL) == null) {
				for (int i = 0; i < DIR.ORTHO.size(); i++) {
					DIR d = DIR.ORTHO.get(i);
					int dx = t.x()+d.x();
					int dy = t.y()+d.y();
					if (SETT.IN_BOUNDS(dx, dy) && multi.isPlacable(dx, dy, PlacerArea.self, PLACER_TYPE.FILL) == null && multi.magicExpandTo(x1, y1, dx, dy)) {
						GUTIL.flooder().pushSmaller(dx, dy, t.getValue()+1);
					}
				}
				
			}
		}
		GUTIL.flooder().done();
		
		
		
	}
	
	void clear() {
		newTile = true;
		PlacerArea.self.clear();
	}

	@Override
	void render(SPRITE_RENDERER r, float ds, GameWindow window) {
		
		if (a.area() == 0)
			return;
		CharSequence pError = placable.isPlacable(a, type);
		CharSequence e = null;
		
		
		int errors = 0;
		for (COORDINATE c : PlacerArea.self.body()) {
			if (!a.is(c))
				continue;
			CharSequence e2 = placable.isPlacable(c.x(), c.y(), a, type);
			if (e2 != null) {
				errors ++;
				e = e2;
			}
			int x = (c.x()-window.tile().x())*C.TILE_SIZE+window.tile().rel().x();
			int y = (c.y()-window.tile().y())*C.TILE_SIZE+window.tile().rel().y();
			int m = 0;
			for (int i = 0; i < DIR.ORTHO.size(); i++) {
				DIR d = DIR.ORTHO.get(i);
				if (a.is(c, d))
					m |= d.mask();
			}
			boolean isPlacable = e2 == null;
			boolean areaIsPlacable = pError == null;
			if (!isPlacable)
				GCOLOR.MAP().BAD.bind();
			else if (!areaIsPlacable)
				GCOLOR.MAP().SOSO.bind();
			else
				GCOLOR.MAP().BEST.bind();
			placable.renderPlaceHolder(r, m, x, y, c.x(), c.y(), a, type, isPlacable, areaIsPlacable);
		}
		COLOR.unbind();
		if (pError != null && pError.length() > 0)
			VIEW.hoverBox().error(pError);
		else if (errors == a.area() && e.length() > 0)
			VIEW.hoverBox().error(e);
		else {
			placable.placeInfo(VIEW.hoverBox(), a.area()-errors, a);
		}
	}

	@Override
	void click(GameWindow window) {
		clickedTile.set(window.tile());
		if (!type.drag) {
			place();
		}
	}

	@Override
	void activate(PLACABLE placer, GameWindow window) {
		hTile.set(window.tile());
		placable = (PlacableMulti) placer;
		if (placable.previous != null) {
			type = placable.previous;
		}else {
			type = PLACER_TYPE.SQUARE;
		}
		if (!placable.canBePlacedAs(type)) {
			for (PLACER_TYPE t : PLACER_TYPE.all) {
				if (placable.canBePlacedAs(t)) {
					type = t;
					break;
				}
			}
		}
		if (placable.prevSize != -1) {
			size = placable.prevSize;
		}
		

		
		clear();
	}

	@Override
	void clickRelease(GameWindow window) {
		if (type.drag) {
			place();
		}
	}
	
	private void place() {
		
		placable.finishChecking(a);
		
		if (placable.isPlacable(a, type) != null)
			return;
		
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep() && !coop.cmd.CoopLockstepCoordinator.isApplyingRemoteCommand()) {
			java.util.List<int[]> coords = new java.util.ArrayList<int[]>();
			for (int y = a.body().y1(); y < a.body().y2(); y++) {
				for (int x = a.body().x1(); x < a.body().x2(); x++) {
					if (a.is(x, y) && placable.isPlacable(x, y, a, type) == null) {
						coords.add(new int[]{x, y});
					}
				}
			}
			if (!coords.isEmpty()) {
				coop.cmd.JobCommandPacket.broadcastJobPlacement(placable, type, coords);
			}
			return;
		}
		// [COOP-MOD END]

		for (int y = a.body().y1(); y < a.body().y2(); y++) {
			for (int x = a.body().x1(); x < a.body().x2(); x++) {
				if (!a.is(x, y) || placable.isPlacable(x, y, a, type) != null)
					continue;
				placable.place(x, y, a, type);
			}
		}
		placable.finishPlacing(a);
	}

	@Override
	LIST<CLICKABLE> gui() {
		butts.clear();
		int i = 0;
		boolean any = false;
		int x1 = typeButts.body().x1();
		int y1 = typeButts.body().y1();
		typeButts.clear();
		typeButts.body().moveX1Y1(x1, y1);
		for (GButt.Panel p : buttsTypes) {
			if (placable.canBePlacedAs(PLACER_TYPE.all.get(i))) {
				any |= !PLACER_TYPE.all.get(i).usesSize;
				typeButts.addDown(0, p);
			}
			i++;
		}
		butts.add(buttType);
		if (any) {
			butts.add(bIncrease);
			butts.add(bDecrease);
		}
		
		return butts;
	}

	
};