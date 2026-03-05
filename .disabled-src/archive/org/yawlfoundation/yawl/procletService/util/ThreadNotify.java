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

package org.yawlfoundation.yawl.procletService.util;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rendezvous utility for the proclet service. Previously extended Thread with
 * synchronized+wait/notify, which pins virtual thread carriers. Converted to
 * implement Runnable with ReentrantLock+Condition so virtual threads are not pinned.
 *
 * Callers use start() and join() as before; the underlying carrier is a virtual thread
 * launched via Thread.ofVirtual().
 */
public class ThreadNotify implements Runnable {

    protected volatile boolean threadSuspended = true;
    protected volatile boolean timeOut = false;

    protected final ReentrantLock _suspendLock = new ReentrantLock();
    protected final Condition _resumeCondition = _suspendLock.newCondition();

    /** Tracks the virtual thread so callers can join() and isAlive(). */
    private volatile Thread _thread;

    public void start() {
        _thread = Thread.ofVirtual().name("proclet-notify").start(this);
    }

    public void join() throws InterruptedException {
        Thread t = _thread;
        if (t != null) {
            t.join();
        }
    }

    public boolean isAlive() {
        Thread t = _thread;
        return t != null && t.isAlive();
    }

    public void press() {
        _suspendLock.lock();
        try {
            threadSuspended = !threadSuspended;
            if (!threadSuspended) {
                System.out.println("press!");
                _resumeCondition.signalAll();
            }
        } finally {
            _suspendLock.unlock();
        }
    }

    public void notification(boolean b) {
        _suspendLock.lock();
        try {
            threadSuspended = !threadSuspended;
            this.timeOut = true;
            if (!threadSuspended) {
                System.out.println("notify! " + this);
            }
            _resumeCondition.signalAll();
        } finally {
            _suspendLock.unlock();
        }
    }

    @Override
    public void run() {
        _suspendLock.lock();
        try {
            System.out.println();
            while (threadSuspended) {
                System.out.println("before " + this);
                _resumeCondition.await();
                System.out.println("after " + this);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            _suspendLock.unlock();
        }
    }

    protected boolean isTimeOut() {
        return this.timeOut;
    }
}
