package game.faction.npc;

import game.faction.FACTIONS;
import game.faction.trade.TradeManager;
import game.time.TIME;
import init.sprite.SPRITES;
import world.region.RD;

public final class UpdaterNPC {
	
	public void init(TradeManager trade) {
		
		SPRITES.loader().print("Simulating factions...");
		
		int a = 50;
				
		for (int i = 0; i < a; i++) {
			SPRITES.loader().print("Simulating factions " + (int)(100*((i*2+a*2)/(double)(a*4))) + "%");
			
			for (FactionNPC f : FACTIONS.NPCs()) {
				if (!f.isActive())
					continue;
				// [COOP-MOD START]
				if (coop.world.CoopWorldRegistry.isPlayerAvatar(f.index()))
					continue;
				// [COOP-MOD END]
				RD.UPDATER().shipAll(f, 1.0);
				f.stockpile.update(f, TIME.secondsPerDay());
			}
			
			if (i % 4 == 0) {
				trade.prime();
			}
		}
		trade.prime();
	}
	

	
}
