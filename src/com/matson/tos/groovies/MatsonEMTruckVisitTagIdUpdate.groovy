/*
 * Copyright (c) 2011 Navis LLC. All Rights Reserved.
 *
 */

/**
 * Created by IntelliJ IDEA. User: isimmons Date: 10/1/11 Time: 12:36 PM To change this template use File | Settings | File Templates.
 */
/*
Groovy code called via direct injection through the agro webservice

Aim is to update the truck visit details ( tvdtlsFlexString01) with the WNET tag details recorded
The details sent are
truck id - Transcore generated
ctr-id - WNET generated ctr id
tag -id - WNET tag read
gos-tv-gkey  WNET tv gkey
lane-id  WNET lane id - matches N4 Gate lane Id

example of message sent
<groovy class-location="database"  class-name="MatsonEMTruckVisitTagIdUpdate">
    <parameters>
	<parameter id="truck-id" value="1236"/>
     <parameter id="equipment-id" value="SUDU1874437"/>
     <parameter id="tag-id" value="88555"/>
	 <parameter id="gos-tv-gkey" value="1099"/>
	 <parameter id="lane-id" value="1"/>
    </parameters>
</groovy>

*/

import java.util.Date;

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.ArgoField;
import com.navis.argo.business.model.GeneralReference;
import com.navis.framework.business.Roastery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.road.RoadEntity;
import com.navis.road.RoadField;
import com.navis.road.business.model.TruckVisitDetails;

class MatsonEMTruckVisitTagIdUpdate extends GroovyInjectionBase {
    public String execute(Map inParameters) {

        String trkId = inParameters.get("truck-id");
        String ctrId = inParameters.get("equipment-id");
        String tagId = inParameters.get("tag-id");
        String gosTvGkey = inParameters.get("gos-tv-gkey");
        String laneId = inParameters.get("lane-id");

        TruckVisitDetails truckVisit = null;

        String returnString = "";
        if (trkId != null) {
            truckVisit = findTruckVisitByNbr(trkId, gosTvGkey);
        }

        if (truckVisit == null) {
            // no truck visit - update the general reference
            if (laneId == null) {
                //set lane to 99
                laneId = "99";
            }
            String _inType1 = "DRAYMAN";
            String _inId1 = "SNXMSG";
            String _inId2 = "LANE";
            String _inId3 = laneId;
            String _date = new Date().format('yyyy-MM-dd HH:mm:ss');

            GeneralReference genR = GeneralReference.findUniqueEntryById(_inType1, _inId1, _inId2, _inId3);
            if (genR == null) {
                // set up the genR
                returnString = "No general reference setup for label id " + laneId;
                GeneralReference genR1 = GeneralReference.findOrCreate(_inType1, _inId1, _inId2, _inId3, _date, tagId, trkId, ctrId, gosTvGkey, null);
            } else {
                genR.setFieldValue(ArgoField.REF_VALUE1, _date);
                genR.setFieldValue(ArgoField.REF_VALUE2, tagId);
                genR.setFieldValue(ArgoField.REF_VALUE3, trkId);
                genR.setFieldValue(ArgoField.REF_VALUE4, ctrId);
                genR.setFieldValue(ArgoField.REF_VALUE5, gosTvGkey);
            }

            returnString = "Updated general reference : Lane Id " + laneId + ". Rfid tag is " + tagId;
            return returnString;
        } else {
            // do a simple Change and record an event
            truckVisit.setTvdtlsFlexString01(tagId);
            returnString = "Updated truck visit is: " + trkId + " : " + gosTvGkey + ". Rfid tag is " + tagId;
            return returnString;
        }
    }

    private TruckVisitDetails findTruckVisitByNbr(String inTruckLicNbr, String inGosTVGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(RoadEntity.TRUCK_VISIT_DETAILS)
                .addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_TRUCK_ID, inTruckLicNbr))
                .addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_STATUS, "OK"))
                .addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_GOS_TV_KEY, inGosTVGkey));
        return (TruckVisitDetails) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(dq);
    }
}
