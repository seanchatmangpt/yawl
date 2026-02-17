package org.yawlfoundation.yawl.stateless.listener.event;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

/**
 * @author Michael Adams
 * @date 24/8/20
 */
public class YTimerEvent extends YEvent {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    private YWorkItem _item;
    private String expiryTimeString;

    public YTimerEvent(YEventType eType, YWorkItem item) {
        super(eType, item.getCaseID());
        _item = item;
    }


    public YWorkItem getItem() {
        return _item;
    }

    public String getExpiryTimeString() {
        if (expiryTimeString == null) {
            expiryTimeString = FORMATTER.format(Instant.ofEpochMilli(_item.getTimerExpiry()));
        }
        return expiryTimeString;
    }
}
