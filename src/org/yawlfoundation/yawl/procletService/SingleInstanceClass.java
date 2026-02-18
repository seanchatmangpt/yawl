/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.procletService;

import org.yawlfoundation.yawl.procletService.blockType.BlockPICreate;
import org.yawlfoundation.yawl.procletService.state.Performative;
import org.yawlfoundation.yawl.procletService.state.Performatives;
import org.yawlfoundation.yawl.procletService.util.ThreadNotify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


public class SingleInstanceClass {

	private static final SingleInstanceClass singleInstance = new SingleInstanceClass();

	private List<ThreadNotify> registeredClasses = new ArrayList<ThreadNotify>();
	private HashMap<ThreadNotify,InternalRunner> mapping = new HashMap<ThreadNotify,InternalRunner>();
	private HashMap<ThreadNotify,Boolean> mappingDone = new HashMap<ThreadNotify,Boolean>();
	private final ReentrantLock _mutex = new ReentrantLock();
	private final ReentrantLock _mutex2 = new ReentrantLock();
	private final ReentrantLock _mutex3 = new ReentrantLock();
	private List<String> blockedCases = new ArrayList<String>();

	private SingleInstanceClass() {
		super();
	}

	public void blockCase(String caseid) {
		_mutex3.lock();
		try {
			if (!blockedCases.contains(caseid)) {
				blockedCases.add(caseid);
			}
		} finally {
			_mutex3.unlock();
		}
	}

	public boolean isCaseBlocked(String caseid) {
		_mutex3.lock();
		try {
			return blockedCases.contains(caseid);
		} finally {
			_mutex3.unlock();
		}
	}

	public void unblockCase(String caseid) {
		_mutex3.lock();
		try {
			blockedCases.remove(caseid);
		} finally {
			_mutex3.unlock();
		}
	}

	public InternalRunner registerAndWait(ThreadNotify thread, long w) {
		InternalRunner ir = null;
		_mutex.lock();
		try {
			if (!registeredClasses.contains(thread)) {
				registeredClasses.add(thread);
				// add a sleep thread
				ir = new InternalRunner(thread, w);
				//mapping.put(thread, ir);
			}
			ir.start();
		} finally {
			_mutex.unlock();
		}
		return ir;
	}

	public InternalRunner registerAndWaitDuringNotify(ThreadNotify thread, long w) {
		_mutex2.lock();
		try {
			// remove old thread from mapping done and registeredclasses
			InternalRunner ir = null;
			// assume registeredClasses is empty
//			while (true) {
//				if (this.registeredClasses.isEmpty()) {
//					break;
//				}
//			}
			if (!registeredClasses.contains(thread)) {
				registeredClasses.add(thread);
				// add a sleep thread
				ir = new InternalRunner(thread, w);
				mapping.put(thread, ir);
				ir.start();
			}
			return ir;
		} finally {
			_mutex2.unlock();
		}
	}

	public void notifyPerformativeListeners(List<Performative> perfs) {
		/* DEADLOCK FIX: Snapshot registered listeners before dispatching notifications.
		 * Previously the entire method (including spin-wait and tn.notification() calls)
		 * ran inside synchronized(mutex). InternalRunner.run() also acquires mutex before
		 * calling tn.notification(true), and any listener that called registerAndWait()
		 * also needs mutex -- guaranteeing a livelock. Fix: perform all mutation of shared
		 * state inside mutex, then release mutex before dispatching notifications and
		 * before the completion spin-wait so other threads can acquire mutex freely. */
		List<ThreadNotify> listenersToNotify;
		_mutex.lock();
		try {
			// first add performatives
			Performatives perfsInst = Performatives.getInstance();
			for (Performative perf : perfs) {
				perfsInst.addPerformative(perf);
			}
			// first process the creation of new classes
			BlockPICreate bpc = BlockPICreate.getInstance();
			bpc.checkForCreationProclets();
			// mark all registered listeners as not-done before releasing the lock
			for (ThreadNotify tn : this.registeredClasses) {
				this.mappingDone.put(tn, false);
			}
			// take a defensive snapshot of the listener list so we can notify outside mutex
			listenersToNotify = new ArrayList<ThreadNotify>(this.registeredClasses);
			this.mapping.clear();
			this.registeredClasses.clear();
		} finally {
			_mutex.unlock();
		}
		// dispatch notifications outside mutex so listeners (and InternalRunner) can
		// re-acquire mutex without deadlocking
		for (ThreadNotify tn : listenersToNotify) {
			tn.notification(false);
		}
		// wait for all listeners to complete outside mutex
		while (true) {
			try {
				Thread.sleep(500);
				// everybody done
				boolean done = true;
				_mutex.lock();
				try {
					Iterator<ThreadNotify> it = this.mappingDone.keySet().iterator();
					while (it.hasNext()) {
						ThreadNotify notif = it.next();
						boolean d = this.mappingDone.get(notif);
						if (!d) {
							done = false;
							break;
						}
					}
				} finally {
					_mutex.unlock();
				}
				if (done) {
					break;
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void done(ThreadNotify notify) {
		if (this.mappingDone.containsKey(notify)) {
			this.mappingDone.put(notify, true);
		}
	}

	public void unregister(ThreadNotify thread) {
		_mutex2.lock();
		try {
			registeredClasses.remove(thread);
			mapping.remove(thread);
		} finally {
			_mutex2.unlock();
		}
	}

	public static SingleInstanceClass getInstance() {
		return singleInstance;
	}

	public class InternalRunner implements Runnable {
		private long started = 0;
		private long interval = 0;
		private ThreadNotify tn = null;
		private volatile Thread _thread;

		public InternalRunner(ThreadNotify tn, long interval) {
			this.tn = tn;
			this.interval = interval;
		}

		public void start() {
			_thread = Thread.ofVirtual().name("proclet-internal-runner").start(this);
		}

		public boolean isAlive() {
			Thread t = _thread;
			return t != null && t.isAlive();
		}

		public void setThreadNotify(ThreadNotify tn) {
			this.tn = tn;
		}

		@Override
		public void run() {
			try {
				started = System.currentTimeMillis();
				System.out.println("sleep:" + interval);
				Thread.sleep(interval);
				System.out.println("done sleeping");
				// done sleeping
				_mutex.lock();
				try {
					System.out.println("inside mutex!");
					System.out.println(tn);
					//System.out.println(sic.mapping.containsValue(this));
					if (tn != null && tn.isAlive()) {
						System.out.println("timer done!");
						tn.notification(true);
						tn = null;
					}
					else {
						System.out.println("thread died without doing anything!");
					}
				} finally {
					_mutex.unlock();
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}

		public long leftOver() {
			return System.currentTimeMillis() - this.started;
		}
	}

}
