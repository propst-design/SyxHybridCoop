package world.battle;

import game.faction.FACTIONS;
import game.faction.Faction;
import init.constant.Config;
import snake2d.LOG;
import snake2d.util.file.Alloc;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayList;
import world.WORLD;
import world.army.AD;
import world.army.WDIV;
import world.entity.army.WArmy;
import world.map.regions.Region;
import world.region.RD;

// [COOP-MOD START]
public class Side  {
// [COOP-MOD END]
	
	private final ArrayList<SideUnit> all = new ArrayList<SideUnit>(Config.battle().DIVISIONS_PER_ARMY);
	{
		while(all.hasRoom())
			all.add(new SideUnit());
	}
	public final ArrayList<SideUnit> us = new ArrayList<>(Config.battle().DIVISIONS_PER_ARMY);

	private int divisions;
	
	private final SideUnit unitTmp = new SideUnit();
	private final int ui[] = Alloc.ii(Config.battle().DIVISIONS_PER_ARMY);
	private final int di[] = Alloc.ii(Config.battle().DIVISIONS_PER_ARMY);
	
	public Side(){

	}
	
	void clear() {
		all.add(us);
		us.clearSloppy();
		divisions = 0;
	}

	public Side copy() {
		Side s = new Side();
		for (int ui = 0; ui < us.size(); ui++) {
			SideUnit u = us.get(ui);
			SideUnit n = new SideUnit();
			
			n.copy(u);
			s.us.add(n);
		}
		
		s.divisions = divisions;
		
		for (int i = 0; i < di.length; i++) {
			s.ui[i] = ui[i];
			s.di[i] = di[i];
		}
		
		
		return s;
	}
	
	public void debug() {
		LOG.ln(divs());
		for (int i = 0; i < divs(); i++)
			LOG.ln(div(i).men() + " " + div(i).name() + " " + div(i).bannerI());
	}
	
	public void add(WArmy a) {
		int max = Config.battle().DIVISIONS_PER_ARMY-divisions;
		for (SideUnit s : us)
			if (s.a() == a)
				return;
		
		unitTmp.set(a, max);
		inited();
		
				
	}
	
	public void add(Region reg) {
		int max = Config.battle().DIVISIONS_PER_ARMY-divisions;
		
		for (SideUnit s : us)
			if (s.r() == reg)
				return;
		
		unitTmp.set(reg, max);
		
		inited();
	}
	
	private void inited() {
		
		if (unitTmp.a() != null && unitTmp.divs() <= 0)
			return;
		
		for (int i = 0; i < us.size(); i++) {
			if (us.get(i).isSameAs(unitTmp))
				return;
		}
		
		SideUnit u = all.removeLast();
		
		u.copy(unitTmp);
		us.add(u);
		
		for (int i = 0; i < u.divs(); i++) {
			ui[divisions] = us.size()-1;
			di[divisions] = i;
			divisions++;
		}
	}
	
	public int divs() {
		return divisions;
	}
	
	public WDIV div(int di) {
		SideUnit u = us.get(ui[di]);
		int i = this.di[di];
		if (i < 0 || i >= u.divs())
			return null;
		return u.div(i);
	}
	
	public int ui(int di) {
		return ui[di];
	}
	
	public SideUnit divUnit(int di) {
		return us.get(ui[di]);
	}

	// [COOP-MOD START]
	public static final class SideUnit {
	// [COOP-MOD END]

		private int type;
		private final static int T_ARMY = 0;
		private final static int T_GARRISON = 1;
		
		private int regionI;
		private int armyI;
		private int maxDivs;
		
		public SideUnit() {
			
		}
		
		void copy(SideUnit o) {
			this.type = o.type;
			this.regionI = o.regionI;
			this.armyI = o.armyI;
			this.maxDivs = o.maxDivs;
		}
		
		public SideUnit set(Region reg, int maxDivs) {
			type = T_GARRISON;
			regionI = reg.index();
			this.maxDivs = maxDivs;
			return this;
		}
		
		public SideUnit set(WArmy a, int maxDivs) {
			type = T_ARMY;
			armyI = a.armyIndex();
			this.maxDivs = maxDivs;
			return this;
		}
		
		public int divs() {
			switch(type) {
			case T_ARMY : return CLAMP.i(a().divs().size(), 0, maxDivs);
			case T_GARRISON: return CLAMP.i(RD.MILITARY().divisions(r()).size(), 0, maxDivs);
			default: throw new RuntimeException();
			}
		}
		
		public WDIV div(int index) {
			switch(type) {
			case T_ARMY : return a().divs().get(index);
			case T_GARRISON: return RD.MILITARY().divisions(r()).get(index);
			default: throw new RuntimeException();
			}
		}
		
		public Faction faction() {
			switch(type) {
			case T_ARMY : return a().faction();
			case T_GARRISON: return r().faction();
			default: throw new RuntimeException();
			}
		}
		
		public double power() {
			switch(type) {
			case T_ARMY : return (a().faction() == FACTIONS.player() ? 0.8 : 1.0)*AD.power().get(a());
			case T_GARRISON: return (r().faction() == FACTIONS.player() ? 0.8 : 1.0)*RD.MILITARY().power.getD(r());
			default: throw new RuntimeException();
			}
		}
		
		public int x() {
			switch(type) {
			case T_ARMY : return a().ctx();
			case T_GARRISON: return r().cx();
			default: throw new RuntimeException();
			}
		}
		
		public int y() {
			switch(type) {
			case T_ARMY : return a().cty();
			case T_GARRISON: return r().cy();
			default: throw new RuntimeException();
			}
		}
		
		public int men() {
			switch(type) {
			case T_ARMY : return AD.men(null).get(a());
			case T_GARRISON: return RD.MILITARY().garrison.get(r());
			default: throw new RuntimeException();
			}
		}
		
		public WArmy a() {
			if (type == T_ARMY)
				return WORLD.ENTITIES().armies.get(armyI);
			return null;
		}
		
		public Region r() {
			if (type == T_GARRISON)
				return WORLD.REGIONS().getByIndex(regionI);
			return null;
		}
		
		public boolean isSameAs(SideUnit o) {
			if (type == o.type)
				return (type == T_GARRISON && regionI == o.regionI) || (type == T_ARMY && armyI == o.armyI);
			return false;
		}


		
	}
	
	static class Conflict {
		
		public final Side A = new Side();
		public final Side B = new Side();
		
		public Conflict() {
		
		}

		void clear() {
			A.clear();
			B.clear();
		}

	}
}
