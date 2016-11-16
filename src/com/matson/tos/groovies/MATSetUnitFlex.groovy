/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package codeExtensions
import bsh.This
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.inventory.business.units.*
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * This groovy set the unit flex on receive export.
 *
 * Author: Peter Seiler
 * Date: 08/05/15
 * JIRA: ARGO-76865
 * SFDC: 142550
 *
 * Aug. 12, 2015 Peter Seiler Add defensive code to exit if no Unit or no booking found
 *
 * Peter Seiler
 * Date: 09/03/15
 * JIRA: CSDV-3208
 *
 * Synchronize the UCC code on the Unit (UnitFlexString15) with the Equipment State UCC (eqsFlexString02)
 *
 * Peter Seiler
 * Date: 09/03/2015
 * SFDC: 144915
 *
 * If RM transaction and VV/POD are specified set the unit's routing to that
 * =====================================================================================================
 * Modified to sync ECC code on the ufv (ufvFlexString08) with Equipment State ECC (eqsFlexString03)
 *
 * Bruno Chiarini
 * Date: 16-Sep-2015
 * =====================================================================================================
 * Wrapped code retrieving booking in a try block to handle RI transactions
 *
 * Bruno Chiarini
 * Date: 03-Oct-2015
 * =====================================================================================================
 * Removed change done on 2015-09-03. Requirement is to no longer persist value of UCC in equipment
 *
 * Bruno Chiarini
 * Date: 2016-02-19
 * SFDC: 150685
 * JIRA: CSDV-3208
 * =====================================================================================================
 */

public class MATSetUnitFlex extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATSetUnitFlex");

        /* check various components of the gate transaction to insure everything needed is present. */

        if (inDao == null)
            return;

        TruckTransaction ThisTran = inDao.getTran();

        if (ThisTran == null)
            return;

        /* Execute the built-in logic got the business task. */

        executeInternal(inDao);

        /* set the unit priority stow code to the booking stow block */

        Unit ThisUnit = ThisTran.getTranUnit();

        if (ThisUnit == null)
        {
            return;
        }

        /* for RM transactions set OB routing if VV/POD specified */

        if (ThisTran.getTranSubType() == TranSubTypeEnum.RM
                && ThisTran.getTranCarrierVisit() != null
                && ThisTran.getTranDischargePoint1() != null)
        {

            UnitFacilityVisit ThisUFV = ThisTran.getTranUfv();

            if(ThisUFV != null)
            {
                Routing ThisRouting = ThisUnit.getUnitRouting();
                ThisRouting.setRtgPOD1(ThisTran.getTranDischargePoint1());
                ThisRouting.setRtgDeclaredCv(ThisTran.getTranCarrierVisit());
                ThisUnit.setUnitRouting(ThisRouting);

                ThisUFV.setUfvObCv(ThisTran.getTranCarrierVisit().getCvGkey());
            }
        }

        try {
            // Added try block to handle cast exception when doing RI transactions
            Booking ThisBooking = ThisTran.getTranEqo();

            /* if a booking is found copy the Priority stow to the unit flex string */

            if (ThisBooking != null) {
                ThisUnit.setUnitFlexString08(ThisBooking.getEqoStowBlock());
            }
        }
        catch(Exception) {}

        UnitEquipment ThisUnitEquip = ThisUnit.getUnitPrimaryUe();
        EquipmentState ThisEqState = ThisUnitEquip.getUeEquipmentState();

        // UCC SYNCING

//        /* if the gate screen did not update the UCC code copy the value from the EQS */
//        /* if the UCC value is not set on the gate screen copy the EQS UCC code to the Unit */
//
//        if (ThisUnit.getUnitFlexString15() == null)
//        {
//            if (ThisEqState.getEqsFlexString02() != null)
//            {
//                /* set the Unit UCC code to the Equipment State's value */
//                ThisUnit.setUnitFlexString15(ThisEqState.getEqsFlexString02());
//            }
//        }
//        else
//        {
//            /* UCC exists, overwrite equipment's value */
//            ThisEqState.setEqsFlexString02(ThisUnit.getUnitFlexString15());
//        }

        /* set the unit UCC code */

        ThisUnit.setUnitFlexString15(ThisTran.getTranUnitFlexString15());

        // ECC SYNCING

        /* if the gate screen did not update the ECC code copy the value from the EQS */

        UnitFacilityVisit ThisUFV = ThisTran.getTranUfv();

        /* if the ECC value is not set on the gate screen copy the EQS ECC code to the Unit */

        if (ThisUFV != null) {
            this.log("ThisUFV   "+ThisUFV);
            this.log("Transaction Type  " + ThisTran.getTranSubType());
            this.log("ThisUFV.getUfvFlexString08()  " + ThisUFV.getUfvFlexString08());
            this.log("ThisEqState.getEqsFlexString03()  " + ThisEqState.getEqsFlexString03());
            this.log("ThisUFV.getUfvFlexString08() == null" + (ThisUFV.getUfvFlexString08() == null));

            if (ThisUFV.getUfvFlexString08() == null)
            {
                if (ThisEqState.getEqsFlexString03() != null)
                {
                    /* set the Unit ECC code to the Equipment State's value */
                    ThisUFV.setUfvFlexString08(ThisEqState.getEqsFlexString03());
                }
            }
            else
            {
                /* ECC exists, overwrite equipment's value */
                ThisEqState.setEqsFlexString03(ThisUFV.getUfvFlexString08());
            }
        }
    }
}