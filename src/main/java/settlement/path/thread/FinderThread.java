package settlement.path.thread;

import settlement.main.SETT;
import settlement.path.components.SCOMPONENTS;
import settlement.path.path.SPath;
import snake2d.LOG;
import snake2d.PathGame.PathFancy;
import snake2d.PathTile;
import snake2d.PathUtilOnline;
import snake2d.util.sets.ArrayList;

public final class FinderThread {

	private final ArrayList<ThreadPathJob> queueJob = new ArrayList<>(1024);
	private final ArrayList<ThreadPath> queueJobPath = new ArrayList<>(1024);
	private volatile boolean lock;
	private volatile boolean stopForUp = false;
	
	private final static Worker[] works = new Worker[1];
	private final Thread[] threads = new Thread[works.length];
	
	// [COOP-MOD START]
	private final SCOMPONENTS comps;
	private PathUtilOnline detPather;
	private SPathFinderThread detFin;

	public void drainDeterministicBatch(int maxJobs) {
		if (detPather == null) {
			detPather = new PathUtilOnline(SETT.TWIDTH);
			detFin = new SPathFinderThread(comps, detPather, 13);
		}
		int drained = 0;
		while (drained < maxJobs) {
			lock();
			if (queueJob.isEmpty()) {
				lock = false;
				break;
			}
			ThreadPath t = queueJobPath.removeLast();
			ThreadPathJob j = queueJob.removeLast();
			lock = false;
			if (j.doJob(detPather, detFin, t))
				t.status = 3;
			else
				t.status = 2;
			drained++;
		}
	}
	// [COOP-MOD END]

	public FinderThread(SCOMPONENTS comps) {

		// [COOP-MOD START]
		this.comps = comps;
		// [COOP-MOD END]
		
		for (Worker w : works) {
			if (w != null) {
				w.working = false;
			}
		}
		
		for (int i = 0; i < threads.length; i++) {
			
			works[i] = new Worker(this, comps);
			Thread t = new Thread(works[i]);
			t.setDaemon(true);
			t.setName("Path offloade #" + i);
			threads[i] = t;
			t.start();
		}
		lock = false;
	}
	
	private synchronized void lock() {
		while(lock)
			;
		lock = true;
	}
	
	public void setStop() {
		stopForUp = true;
	}
	
	public void stop() {
		stopForUp = true;
		
		while(!allAreStopped()) {
			;
		}
		
	}
	
	private boolean allAreStopped() {
		for (int i = 0; i < threads.length; i++) {
			if (!threads[i].isAlive())
				throw new RuntimeException("dead!");
			if (!works[i].stopped)
				return false;
		}
		return true;
	}
	
	public void start() {
		// [COOP-MOD START]
		if (coop.net.CoopSessionManager.getInstance().isSharedCityLockstep()) {
			stopForUp = true;
			return;
		}
		// [COOP-MOD END]
		stopForUp = false;
		
	}
	
	public void prep(SPath p, int sx, int sy, int dx, int dy, boolean full) {
		prep(p, resume, sx, sy, dx, dy, full);
	}
	
	public void prep(SPath p, ThreadPathJob job, int sx, int sy, int dx, int dy, boolean full) {
		ThreadPath t = p.thread;
		if (t.status == 1)
			return;
		t.status = 1;
		t.sx = (short) sx;
		t.sy = (short) sy;
		t.dx = (short) dx;
		t.dy = (short) dy;
		t.full = full;
		lock();
		if (queueJob.hasRoom()) {
			queueJob.add(job);
			queueJobPath.add(t);
		}else
			t.status = 0;
		lock = false;
	}
	
	private static class Worker implements Runnable {

		private final PathUtilOnline pather;
		private final SPathFinderThread fin;
		private volatile boolean working = true;
		private volatile boolean stopped;
		private FinderThread tt;
		
		Worker(FinderThread tt, SCOMPONENTS comps){
			this.tt = tt;
			pather = new PathUtilOnline(SETT.TWIDTH);
			fin = new SPathFinderThread(comps, pather, 13);
		}

		@Override
		public void run() {
			while(working) {
				if (tt.stopForUp) {
					stopped = true;
					if (!working)
						break;
					while(tt.stopForUp) {
						if (!working)
							break;
						sleep();
						continue;
					}
					continue;
				}
				stopped = false;
				tt.lock();
				if (tt.queueJob.isEmpty()) {
					tt.lock = false;
					tt.stopForUp = true;
					stopped = true;
					Thread.yield();
					continue;
				}
				
				
				ThreadPath t = tt.queueJobPath.removeLast();
				ThreadPathJob j = tt.queueJob.removeLast();
				tt.lock = false;
				if (j.doJob(pather, fin, t))
					t.status = 3;
				else
					t.status = 2;
			}
			LOG.ln("Pathworker is dead");
		}
		
		private void sleep() {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				
			}
		}
		
	}
	
	public static class ThreadPath {

		private volatile byte status;
		public final PathFancy path = new PathFancy(SPath.size);
		short sx,sy,dx,dy;
		boolean full;
		public volatile short destX;
		public volatile short destY;

		public boolean isProcessed(int sx, int sy, int dx, int dy) {

			if (this.sx == sx && this.sy == sy && this.dx == dx && this.dy == dy)
				return status > 1;
			return false;
		}
		
		public boolean isBeingProcessed() {
			return status >= 1;
		}
		
		public boolean isSuccess() {
			return status == 3;
		}
		
		public void debug(int sx, int sy, int dx, int dy) {
			if (status > 1)
				LOG.ln((status) + " " + (this.sx-sx) + " " + (this.sy-sy) + " " + (this.dx-dx) + " " + (this.dy-dy));
		}
		
	}
	
	static interface ThreadPathJob {
		
		public boolean doJob(PathUtilOnline p, SPathFinderThread fin, ThreadPath pp);
		
	}
	
	private final static ThreadPathJob resume = new ThreadPathJob() {
		
		@Override
		public boolean doJob(PathUtilOnline p, SPathFinderThread fin, ThreadPath t) {
			PathTile tile = fin.find(t.sx, t.sy, t.dx, t.dy, t.full);
			
			if (tile != null) {
				t.destX = (short) tile.x();
				t.destY = (short) tile.y();
				t.path.set(tile);
				return true;
			}else {
				return false;
			}
			
		}
	};
	
}
