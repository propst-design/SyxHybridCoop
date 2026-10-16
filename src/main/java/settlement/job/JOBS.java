package settlement.job;

import static settlement.main.SETT.IN_BOUNDS;
import static settlement.main.SETT.PATH;
import static settlement.main.SETT.TAREA;
import static settlement.main.SETT.TWIDTH;

import java.io.IOException;

import game.debug.Profiler;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.sprite.SPRITES;
import settlement.job.JobBuildFort.JobBuildForts;
import settlement.job.JobBuildRoad.JobBuildRoads;
import settlement.main.ON_TOP_RENDERABLE;
import settlement.main.SETT;
import settlement.main.SETT.SettResource;
import settlement.tilemap.terrain.TGrowable;
import snake2d.CORE;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.map.MAP_OBJECT;
import snake2d.util.map.MAP_SETTER;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import snake2d.util.sets.Bitsmap1D;
import snake2d.util.sets.Bitsmap2D;
import snake2d.util.sets.KeyMap;
import snake2d.util.sets.LIST;
import util.colors.COLOR_MAP;
import util.colors.GCOLOR;
import util.data.BOOLEAN.BOOLEAN_MUTABLE;
import util.gui.misc.GBox;
import util.keymap.MAPSAVE;
import util.rendering.RenderData;
import util.rendering.ShadowBatch;
import view.tool.PLACABLE;
import view.tool.PlacableMulti;

public final class JOBS extends SettResource {

	private final Bitsmap2D map = new Bitsmap2D(0, 12, SETT.TWIDTH, SETT.THEIGHT);
	private final Bitsmap1D statei = new Bitsmap1D(-1, 2, TAREA);
	
	public Bitsmap2D paintmap = new Bitsmap2D(0, 3, SETT.TWIDTH, SETT.THEIGHT);
	
	public final BOOLEAN_MUTABLE planMode = new BOOLEAN_MUTABLE() {

		private boolean i = false;

		@Override
		public boolean is() {
			return i;
		}

		@Override
		public BOOLEAN_MUTABLE set(boolean b) {
			i = b;
			return this;
		}

	};
	private int hoverI = -1;

	final Bitsmap1D progress = new Bitsmap1D(0, 3, TAREA);
	final Bitmap1D wantsRes = new Bitmap1D(TAREA, false);
	final StateManager state = new StateManager(statei);

	public final MAP_OBJECT<Job> getter;
//	public final MAP_INT in2dexMap = new MAP_INT() {
//
//		@Override
//		public int get(int tx, int ty) {
//			if (IN_BOUNDS(tx, ty))
//				return get(tx + ty * TWIDTH);
//			return Job.NOTHING;
//		}
//
//		@Override
//		public int get(int tile) {
//			return map[tile];
//		}
//	};
	public final PlacableMulti tool_clear = new PlacerDelete();
	public final PlacableMulti tool_activate = new PlacerActivate();
	public final PlacableMulti tool_dormant = new PlacerDormant();
	public final PlacableMulti tool_remove_all = new PlacerRemoveAll();
	public final PlacableMulti tool_remove_smartl = new PlacerRemoveSmart();
	public final MAP_OBJECT<Job> jobGetter = new JobGetter();
	final JobRoom room = new JobRoom(null);
	final JobRoom[] rooms = new JobRoom[RESOURCES.ALL().size()];
	{
		for (int i = 0; i < rooms.length; i++)
			rooms[i] = new JobRoom(RESOURCES.ALL().get(i));
	}

	public final PlacableMulti tool_repair = new PlacerRepair();

	public final JobBuildRoads roads = new JobBuildRoads();
	public final LIST<JobBuildStructure> build_structure = JobBuildStructure.make();
	public final JobBuildForts build_fort = new JobBuildForts();
	public final LIST<Job> fences = JobBuildFence.make();
	public final JobClears clearss = new JobClears();
	public final LIST<PLACABLE> clears = new ArrayList<>(clearss.placers);
	public final BlockedJobs blocked = new BlockedJobs();


	// [COOP-MOD START]
	public static Job getJob(int index) {
		if (index >= 0 && index < Job.all.size()) {
			return Job.all.get(index);
		}
		return null;
	}

	public static int getJobCount() {
		return Job.all.size();
	}
	// [COOP-MOD END]

	public JOBS() {
		super("JOBS", true);
		new Debug();
		
		getter = new MAP_OBJECT<Job>() {
			
			@Override
			public Job get(int tx, int ty) {
				if (!IN_BOUNDS(tx, ty))
					return null;
				
				int i = map.get(tx, ty);
				if (i != Job.NOTHING) {
					Job j = Job.all.get(i -1);
					if (!j.get(tx, ty)) {
						PlacerDelete.place(tx, ty);
						return null;
					}
					return j;
				}

				return null;
			}
			
			@Override
			public Job get(int tile) {
				return get(tile % TWIDTH, tile / TWIDTH);
			}
			
			@Override
			public boolean is(int tile) {
				int i = map.get(tile);
				return i != Job.NOTHING;
			}
			
			@Override
			public boolean is(int tx, int ty) {
				if (!IN_BOUNDS(tx, ty))
					return false;
				return is(tx + ty*TWIDTH);
			}
		};

		new ON_TOP_RENDERABLE() {

			@Override
			public void render(Renderer r, ShadowBatch shadowBatch, RenderData data, double ds) {
				RenderData.RenderIterator i = data.onScreenTiles();
				COLOR_MAP c = GCOLOR.MAP();
				while (i.has()) {

					int index = map.get(i.tile());
					
					if (index != Job.NOTHING) {
						
						index -= 1;
						
						if (i.tile() == hoverI) {
							COLOR.WHITE2WHITE.bind();
						} else {

							switch (state.get(i.tile())) {
							case DORMANT:
								c.DORMANT.bind();
								break;
							case RESERVABLE:
								c.JOB_ACTIVE.bind();
								break;
							case RESERVED:
								c.JOB_RESERVED.bind();
								break;
							case BLOCKED:
								c.JOB_BLOCKED.bind();
								break;
							}

						}
						Job j = Job.all.get(index);
						
						if (j != null) {
							
							j.renderAbove(r, i.x(), i.y(), 0, i.tx(), i.ty());

							if (CORE.renderer().getZoomout() <= 1) {
								j.get(i.tx(), i.ty());
								RESOURCE res = j.resourceCurrentlyNeeded();
								if ((j == clearss.food && !SETT.WEATHER().growthRipe.cropsAreRipe()) || (res != null
										&& !j.jobReservedIs(res) && !PATH().finders.resource.normal.has(i.tx(), i.ty(), res))) {
									COLOR.WHITE702WHITE100.bind();
									SPRITES.cons().ICO.warning.render(r, i.x(), i.y());
									COLOR.unbind();
								}

							}
							// COLOR.unbind();
							// StringReusable.TMP.clear().add(state.getDepth(i.tx(), i.ty()));
							// UI.FONT().H1.render(StringReusable.TMP, i.x(), i.y());
						}
					} else if (SETT.TERRAIN().get(i.tile()) instanceof TGrowable
							&& SETT.TERRAIN().GROWABLES.get(0).job.is(i.tile())) {
						if (i.tile() == hoverI) {
							COLOR.WHITE2WHITE.bind();
						} else {
							c.DORMANT.bind();

						}
						SPRITES.cons().BIG.dashed_hollow.render(r, 0, i.x(), i.y());

					}
					i.next();
				}
				COLOR.unbind();

			}
		}.add();
		clear();
		
		KeyMap<Job> map = new KeyMap<Job>();
		for (Job j : Job.all)
			map.put(j.key(), j);
	}

	public void render(Renderer r, ShadowBatch shadowBatch, RenderData data) {

		RenderData.RenderIterator i = data.onScreenTiles();

		while (i.has()) {
			int index = map.get(i.tile());
			if (index != Job.NOTHING) {
				Job.all.get(index -1).renderBelow(r, shadowBatch, i, progress.get(i.tile()));
			}
			i.next();
		}

	}

	void set(Job job, int tx, int ty) {
		map.set(tx, ty, job == null ? 0 : job.index()+1);
	}

	@Override
	protected void save(FilePutter saveFile) {
		map.save(saveFile);
		MAPSAVE.saveMeta(saveFile, Job.all);
		
		statei.save(saveFile);
		progress.save(saveFile);
		wantsRes.save(saveFile);
		paintmap.save(saveFile);
		blocked.save(saveFile);
	}

	@Override
	protected void load(FileGetter saveFile) throws IOException {
		map.load(saveFile);
		int[] oo = MAPSAVE.saveWash(saveFile, Job.all, Job.NOTHING);
		statei.load(saveFile);
		progress.load(saveFile);
		wantsRes.load(saveFile);
		if (oo != null) {
			
			for (int i = 0; i < SETT.TAREA; i++) {
				int bb = map.get(i);
				if (bb == Job.NOTHING || bb < 0 || bb >= oo.length || oo[bb] == Job.NOTHING) {
					map.set(i, Job.NOTHING);
					statei.set(i, 0);
					progress.set(i, 0);
					wantsRes.set(i, false);
				}
				
			}
			
		}
		paintmap.load(saveFile);
		blocked.load(saveFile);
			
		

	}

	@Override
	protected void clear() {
		map.clear();
		statei.clear();
		progress.clear();
		wantsRes.clear();
		blocked.clear();
	}
	
	@Override
	protected void init(boolean loaded) {
		clearss.initSpeeds();
		if (loaded) {
			for (COORDINATE c : SETT.TILE_BOUNDS) {
				getter.get(c);
			}
		}
	}

	@Override
	protected void update(double ds, Profiler profiler) {
		hoverI = -1;
		blocked.update(ds);;
	}

	public void hover(int tx, int ty, GBox box) {
		hoverI = tx + ty * SETT.TWIDTH;
		if (getter.is(tx, ty)) {
			Job j = getter.get(tx, ty);
			hoverI = j.tile;
			j.hover(box);

//			if (j.resourceCurrentlyNeeded() != null) {
//				box.NL();
//				box.setResource(j.resourceCurrentlyNeeded(), 1);
//			}

			box.NL();
			
			box.add(box.text().add(state.getDepth(tx, ty)));
			
		} else if (SETT.TERRAIN().get(tx, ty) instanceof TGrowable && SETT.TERRAIN().GROWABLES.get(0).job.is(tx, ty)) {
			clearss.HoverEdible(box, tx, ty);
		}
		
		
	}

	public final MAP_SETTER clearer = new MAP_SETTER() {

		@Override
		public MAP_SETTER set(int tx, int ty) {
			PlacerDelete.place(tx, ty);
			return this;
		}

		@Override
		public MAP_SETTER set(int tile) {
			throw new RuntimeException();
		}
	};

}
