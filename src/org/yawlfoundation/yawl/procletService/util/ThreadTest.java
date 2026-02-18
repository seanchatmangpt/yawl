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

/**
 * Test subclass of ThreadNotify. Previously used synchronized(this)+wait/notify which
 * pins virtual thread carriers. Updated to use the inherited ReentrantLock+Condition
 * from ThreadNotify so virtual threads are not pinned.
 */
public class ThreadTest extends ThreadNotify {

    public ThreadTest() {
    }

    @Override
    public void press() {
        _suspendLock.lock();
        try {
            threadSuspended = !threadSuspended;
            if (!threadSuspended) {
                _resumeCondition.signal();
            }
        } finally {
            _suspendLock.unlock();
        }
    }

    @Override
    public void run() {
        _suspendLock.lock();
        try {
            while (threadSuspended) {
                System.out.println("before");
                _resumeCondition.await();
                System.out.println("after");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            _suspendLock.unlock();
        }
    }

    public static void main(String[] args) {
        ThreadTest t = new ThreadTest();
        t.start();
        System.out.println();
        t.press();
        System.out.println();
    }
}
