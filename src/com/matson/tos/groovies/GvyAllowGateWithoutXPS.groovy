/*
* Copyright (c) 2003 Navis LLC. All Rights Reserved.
* $Id: GvyAllowGateWithoutXPS.groovy,v 1.1 2016/10/05 21:10:16 vnatesan Exp $
*/

import com.navis.argo.ArgoPropertyKeys
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.util.RoadBizUtil

/**
 * This groovy allows the Gate to continue processing even though XPS is down
 * by setting the message level to Warning for the XPS TCP error message
 */
public class GvyAllowGateWithoutXPS {

    public void setXpsTpcWarning() throws BizViolation {

        for (UserMessage um : (List<UserMessage>)RoadBizUtil.getMessageCollector().getMessages()) {
            if (ArgoPropertyKeys.XPS_TPC_ERROR.equals(um.getMessageKey())) {
                um.setSeverity(MessageLevel.WARNING);
            }
        }
    }
}
