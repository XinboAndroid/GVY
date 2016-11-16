/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.services.business.event.GroovyEvent

/**
 * This groovy checks the booking item remarks.  If the remarks start with 'DUAL' the following is /x/y where x is temp 1 and y is temp 1
 *
 * Author: Peter Seiler
 * Date: 07/06/15
 * JIRA: CSDV-3035
 * SFDC: 138256
 *
 */

public class MATBookingReeferRequirements extends AbstractGeneralNoticeCodeExtension
{
    public void execute(GroovyEvent inEvent)
    {
        this.log("MATBookingReeferRequirements: Started");

        EquipmentOrder ThisBooking = (EquipmentOrder) inEvent.getEntity();

        if (ThisBooking == null)
            return;

        Set<EquipmentOrderItem> TheseEQOIs = ThisBooking.getEqboOrderItems();

        if (TheseEQOIs == null || TheseEQOIs.size() == 0)
            return;

        for (EquipmentOrderItem ThisEQOI : TheseEQOIs)
        {

            /* get the equipment order item entity */

            String ThisEQOIRemarks = ThisEQOI.getEqoiRemarks();

            if (ThisEQOIRemarks != null && ThisEQOIRemarks.size() > 4 && ThisEQOIRemarks.indexOf('DUAL') == 0)
            {

                List<UnitEquipment> UE_OnEQOI = this.findUnitEqForOrderItem(ThisEQOI.getEqboiGkey());

                if (UE_OnEQOI == null || UE_OnEQOI.size() == 0)
                    return;

                String Temp1Txt = null;
                String Temp2Txt = null;
                Double Temp1Num = null;
                Double Temp2Num = null;

                int FirstSlash = ThisEQOIRemarks.indexOf('/');
                int SecondSlash = ThisEQOIRemarks.indexOf('/', FirstSlash + 1);


                if (FirstSlash < SecondSlash)
                {

                    /* get the values of the two tempuratures */

                    Temp1Txt = ThisEQOIRemarks[FirstSlash + 1..SecondSlash - 1];
                    Temp2Txt = ThisEQOIRemarks[SecondSlash + 1..ThisEQOIRemarks.length() - 1]
                }

                /* the text of the reefer settings have been parsed */

                if (Temp1Txt != null && Temp2Txt != null && Temp1Txt.isNumber() && Temp2Txt.isNumber())
                {
                    Temp1Num = Double.parseDouble(Temp1Txt);
                    Temp2Num = Double.parseDouble(Temp2Txt);
                }

                this.log("After asigning to Double " + Temp1Num + " " + Temp2Num)

                /* Find any associated equipment */

                if (Temp1Num != null)
                {
                    for (UnitEquipment EQOI_UE : UE_OnEQOI)
                    {

                        GoodsBase thisUnitGoods = null;
                        ReeferRqmnts thisReeferReq = null;

                        Unit thisUnit = EQOI_UE.getUeUnit();

                        if (thisUnit != null)
                        {
                            thisUnitGoods = thisUnit.getUnitGoods();
                        }

                        if (thisUnitGoods != null)
                        {
                            thisReeferReq = thisUnitGoods.getGdsReeferRqmnts();
                        }

                        if (thisReeferReq != null)
                        {

                            thisReeferReq.setRfreqTempRequiredC((Temp1Num - 32.0) / 1.8);
                            thisReeferReq.setRfreqTempLimitMinC((Temp1Num - 32.0) / 1.8);
                            thisReeferReq.setRfreqTempLimitMaxC((Temp2Num - 32.0) / 1.8);
                        }
                    }
                }
            }
        }
    }

    /* local function to find UnitEquipment associated with an EquipmentOrderItem */

    private List<UnitEquipment> findUnitEqForOrderItem(long inEQOI)
    {

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_EQUIPMENT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UE_DEPARTURE_ORDER_ITEM, inEQOI));

        List<UnitEquipment> UEList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        this.log ("UEList " + UEList)

        return UEList;
    }
}