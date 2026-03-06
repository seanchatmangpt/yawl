package org.yawlfoundation.yawl.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Michael Adams
 * @date 5/5/20
 */
public class NamedThreadFactory implements ThreadFactory {

   private final AtomicLong _index = new AtomicLong(0);
   private final String _prefix;


   public NamedThreadFactory(String prefix) {
       _prefix = prefix + "-";
   }


   @Override
   public Thread newThread(Runnable runnable) {
       return Thread.ofVirtual()
               .name(_prefix, _index.getAndIncrement())
               .unstarted(runnable);
   }

}
