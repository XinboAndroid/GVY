/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.road.business.atoms.TranSubTypeEnum
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.navis.inventory.InventoryField

/**
 * This will pull a chassis or accessory out of the yard if the clerk has entered it at the ingate.
 * This allows a chassis to be ingated that was no properly delivered earlier.
 *
 * Author: Peter Seiler
 * Date: 09/09/15
 * JIRA: CSDV-3208
 * SFDC: 144851
 *
 * -------------------------------------------------------------------------------------------------------------------
 * In RC the accessory id is in the chassis accessory number field. Added code to get this value in case of an RC
 *
 * Author:  Bruno Chiarini
 * Date:    2015-09-18
 *
 */

public class MATDetachChsAcc extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    private Logger LOGGER = Logger.getLogger(MATDetachChsAcc.class);

    public void execute(TransactionAndVisitHolder inDao)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATDetachChsAcc Execution Started");

        TruckTransaction ThisTran = inDao.getTran();

        /* get out if no gate transaction is found */

        if (ThisTran == null)
            return;

        // One single Accessory field in receive transactions
        // In RC is Chassis Accessory Number, In all others is Container Accessory Number
        String AccId;
        if (ThisTran.getTranSubType() == TranSubTypeEnum.RC)
            AccId = ThisTran.getTranChsAccNbr();
        else
            AccId = ThisTran.getTranCtrAccNbr();

        LOGGER.info("Accessory Id: " + AccId);

        if (AccId != null)
        {

            /* see if the accessory exists as a bare unit */

            Unit ThisAccUnit = this.findActiveUnitInYardByID(AccId);
            LOGGER.info("Active unit for AccId: " + ThisAccUnit);


            if (ThisAccUnit == null)
            {
                // It doesn't exist as bare unit, let's check if it's attached to another unit

                Unit ThisCtrUnit = this.findActiveUnitByAcc(AccId);
                LOGGER.info("Attached to active unit: " + ThisCtrUnit);

                if (ThisCtrUnit != null)
                {
                    /* there is another unit with it as accessory  detach it */
                    UnitEquipment ThisCtrAccUnitUe = ThisCtrUnit.getAccessoryOnCtr();
                    UnitEquipment ThisChsAccUnitUe = ThisCtrUnit.getAccessoryOnChs();

                    if (ThisCtrAccUnitUe == null)
                    //Not on Ctr, must be on Chs
                        ThisChsAccUnitUe.detach("Detached by MATDetachChsAcc");
                    else if (ThisChsAccUnitUe == null)
                    //Not on Chs, must be on Ctr
                        ThisCtrAccUnitUe.detach("Detached by MATDetachChsAcc");
                    else
                    {
                        //Chs and Ctr have accessories, check which one to detach based on id
                        //Can only be one of them, because ingate has only one acc field
                        LOGGER.info("CtrUnitUe ID: " + ThisCtrAccUnitUe.getField(InventoryField.UNIT_EQ_ID_FULL));
                        if (ThisCtrAccUnitUe.getField(InventoryField.UNIT_EQ_ID_FULL) == AccId)
                            ThisCtrAccUnitUe.detach("Detached by MATDetachChsAcc");
                        else
                            ThisChsAccUnitUe.detach("Detached by MATDetachChsAcc");
                    }

                    HibernateApi.getInstance().flush();

                    LOGGER.info("Accessory Detached");
                }
            }

            /* if there is an accessory in the yard retire it */

            if (ThisAccUnit != null)
            {
                ThisAccUnit.makeRetired();
                HibernateApi.getInstance().flush();
                LOGGER.info("Accessory Retired");
            }
        }

        /* process chassis only if a chassis is specified and it is not 'chassis is owners' */

        if (ThisTran.getTranChsNbr() != null && !ThisTran.getTranChsIsOwners())
        {

            /* see if the chassis exists as a bare chassis unit */

            Unit ThisChsUnit = this.findActiveUnitInYardByID(ThisTran.getTranChsNbr());

            /* if not see if there is a unit with that chassis as carrier */

            if (ThisChsUnit == null)
            {
                Unit ThisCtrUnit = this.findActiveUnitByChs(ThisTran.getTranChsNbr());

                if (ThisCtrUnit != null)
                {

                    /* there is another unit with it as carrier  detach it */

                    ThisChsUnit = ThisCtrUnit.dismount();
                    HibernateApi.getInstance().flush();
                }
            }

            /* if there is a in the yard retire it */

            if (ThisChsUnit != null)

            {
                ThisChsUnit.makeRetired();
                HibernateApi.getInstance().flush();
            }
        }

        LOGGER.info("MATDetachChsAcc Execution Ended")
        executeInternal(inDao);
    }

    /* Local function to find the an active unit in the yard unit by ID */

    private Unit findActiveUnitInYardByID(String chsId)

    {
        //LOGGER.info("Finding units");
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID, chsId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
        //LOGGER.info("Found " + unitList.length);
        //for (int i=0; i< unitList.length; i++)
        //    LOGGER.info("ActiveUnitInYardbyAccFound: " + unitList[i]);
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

        //LOGGER.info("Finding units By Acc");
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ACRY_EQUIP_IDS, chsId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        //LOGGER.info("Found " + unitList.length);
        //for (int i=0; i< unitList.length; i++)
        //    LOGGER.info("ActiveUnitInYardbyAccFound: " + unitList[i]);
        if(unitList == null || unitList.size()==0)
        {

            return null;

        }

        return unitList[0];
    }
}