/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitManagerPea
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * Check * This groovy departs the unit in a one-stage gate
 *
 * Author: Peter Seiler
 * Date: 06/30/15
 * JIRA: CSDV-3063
 * SFDC: 142561
 *
 */

public class MATDepartUnit extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATDepartUnit");

        /* Execute the built-in logic got the business task. */

        executeInternal(inDao);

        TruckTransaction ThisTran = inDao.getTran();

        /* get out if no gate transaction is found */

        if (ThisTran == null)
            return;

        Unit ThisUnit = this.findActiveUnitInYard(ThisTran.getTranCtrNbr());

        /* Unit ThisUnit = ThisTran.getTranUnit();  */

        this.log("ThisUnit " + ThisUnit)

        if (ThisUnit == null)
            return;

        /*  Before continuing, make sure there are no errors exist in the main session */

        if (RoadBizUtil.getMessageCollector().getMessageCount(MessageLevel.SEVERE) > 0)
            return;

        this.log ("no errors")
        this.log("ThisUnit.getUnitActiveUfvNowActive() " + ThisUnit.getUnitActiveUfvNowActive())

        Equipment ThisChassisEq = Equipment.findEquipment(ThisTran.getTranChassis());
        Equipment ThisCtrAcc = Equipment.findEquipment(ThisTran.getTranCtrAccessory());
        Equipment ThisChsAcc = Equipment.findEquipment(ThisTran.getTranChsAccNbr());

        UnitManager UnitMgr = Roastery.getBean(UnitManager.BEAN_ID);

        UnitMgr.confirmUnitEquipment(ThisUnit, ThisChassisEq, null, ThisCtrAcc, ThisChsAcc);

        /* set the unit to departed */

        ThisUnit.deliverOutOfFacility(ThisTran.getTranFacility());

        /* RectifyParms thisRectifyParm = new RectifyParms();

        thisRectifyParm.setEraseHistory(false);
        thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
        thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.DEPARTED);

        ThisUnit.getUnitActiveUfvNowActive().rectify(thisRectifyParm); */
    }


    /* Local function to find the an active container unit in the yard unit */

    private Unit findActiveUnitInYard (String ctrId)

    {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID, ctrId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        if(unitList == null || unitList.size()==0)
        {
            return null;
        }
        return unitList[0];
    }
}