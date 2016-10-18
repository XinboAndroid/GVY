/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
/**
 * This will pull a chassis or accessory into the yard if it is referenced at the outgate and is notavailable in the yard
 *
 * Author: Peter Seiler
 * Date: 09/14/15
 * JIRA: CSDV-3208
 * SFDC: 144851
 *
 * Peter Seiler
 * 02/02/2016
 * JIRA: CSDV-3063
 * SFDC: 150230
 *
 * Fix bug that caused it to rectify a chassis that was married to another unit.
 *
 */

public class MATPullChsAcc extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATPullChsAcc");

        TruckTransaction ThisTran = inDao.getTran();

        /* get out if no gate transaction is found */

        if (ThisTran == null)
            return;

        String AccID = ThisTran.getTranCtrAccNbr();

        if (AccID == null)
        {
            AccID = ThisTran.getTranChsAccNbr();
        }

        if (AccID != null)
        {

            /* see if the accessory exists as a bare unit */

            Unit ThisAccUnit = this.findActiveUnitInYardByID(AccID);

            if (ThisAccUnit == null)
            {

                Unit ThisCtrUnit = this.findActiveUnitByAcc(AccID);
                if (ThisCtrUnit == null)
                {

                    /* there is no unit in the yard that uses this accessory */

                    /* get the equipment record for the accessory. */

                    Equipment ThisAccEq = Equipment.findEquipment(AccID);

                    if (ThisAccEq != null)
                    {
                        /* if it exists create a new bare accessory unit in the yard */

                        UnitManager unitMgr = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
                        LocPosition YardPos = LocPosition.createYardPosition(ContextHelper.getThreadYard(), 'DVRC', null, null, false);

                        UnitFacilityVisit newUfv = unitMgr.createYardBornUnit(ThisAccEq, YardPos, 'Gate Fix It');

                        /* rectify the unit into that yard position */

                        RectifyParms thisRectifyParm = new RectifyParms();

                        thisRectifyParm.setEraseHistory(false);
                        thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S40_YARD);
                        thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.ACTIVE);
                        thisRectifyParm.setPosition(YardPos);

                        newUfv.rectify(thisRectifyParm);

                        HibernateApi.getInstance().flush();

                    }
                }
            }
        }

        /* process chassis only if a chassis is specified and it is not 'chassis is owners' */

        if (ThisTran.getTranChsNbr() != null && !ThisTran.getTranChsIsOwners())
        {

            /* see if the chassis exists as a bare chassis unit */

            Unit ThisChsUnit = this.findActiveUnitInYardByID(ThisTran.getTranChsNbr());

            /* if not see if there is a unit with that chassis as carrier */

            if(ThisChsUnit == null)
            {
                ThisChsUnit = this.findActiveUnitByChs(ThisTran.getTranChsNbr());
            }

            if (ThisChsUnit == null)
            {

                /* there is no unit in the yard that uses this chassis */

                /* get the equipment record for the chassis. */

                Equipment ThisChsEq = Equipment.findEquipment(ThisTran.getTranChsNbr());

                if (ThisChsEq != null)
                {
                    /* if it exists create a new bare accessory unit in the yard */

                    UnitManager unitMgr = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
                    LocPosition YardPos = LocPosition.createYardPosition(ContextHelper.getThreadYard(), 'DVRC', null, null, false);

                    UnitFacilityVisit newUfv = unitMgr.createYardBornUnit(ThisChsEq, YardPos, 'Gate Fix It');

                    /* rectify the unit into that yard position */

                    RectifyParms thisRectifyParm = new RectifyParms();

                    thisRectifyParm.setEraseHistory(false);
                    thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S40_YARD);
                    thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.ACTIVE);
                    thisRectifyParm.setPosition(YardPos);

                    newUfv.rectify(thisRectifyParm);

                    HibernateApi.getInstance().flush();

                }
            }
        }

        executeInternal(inDao);
    }

    /* Local function to find the an active unit in the yard unit by ID */

    private Unit findActiveUnitInYardByID(String chsId)

    {

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID, chsId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        if(unitList == null || unitList.size()==0)
        {

            return null;

        }

        return unitList[0];
    }

    /* Local function to find the an active unit in the yard unit based on the chsid */

    private Unit findActiveUnitByChs(String chsId)

    {

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CARRIAGE_UE_EQ_ID, chsId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        if(unitList == null || unitList.size()==0)
        {

            return null;

        }

        return unitList[0];
    }

    /* Local function to find the an active unit in the yard unit based on the chsid */

    private Unit findActiveUnitByAcc(String chsId)

    {

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ACRY_EQUIP_IDS, chsId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        if(unitList == null || unitList.size()==0)
        {

            return null;

        }

        return unitList[0];
    }
}