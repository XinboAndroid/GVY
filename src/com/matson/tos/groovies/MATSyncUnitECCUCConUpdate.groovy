/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/


import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.*
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.Facility

/**
 * Copies values of ECC and UCC from unit and ufv flex fields to corresponding flex fields in Equipment State
 * values are required in unit and flex field for visibility in XPS
 * and copied to Equipment state to persist
 *
 * Author: Bruno Chiarini
 * Date: 17-Sep-2015
 * JIRA: CSDV-3208
 * SFDC: 145020
 *
 *=====================================================================================================
 * Requirement is to no longer persist value of UCC in equipment. Commented out unnecessary code.
 *
 * Bruno Chiarini
 * Date: 2016-02-19
 * SFDC: 150685
 * JIRA: CSDV-3208
 * =====================================================================================================
 *
 */

public class MATSyncUnitECCUCConUpdate extends AbstractGeneralNoticeCodeExtension

{
    private Logger LOGGER = Logger.getLogger(MATSyncUnitECCUCConUpdate.class);

    public void execute(GroovyEvent inEvent)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATSyncUnitECCUCConUpdate Execution Started");

        try {
            Unit ThisUnit = (Unit)inEvent.getEntity();
            if (ThisUnit == null) {
                LOGGER.error("Reference to Unit not found!");
                return;
            }
            else
                LOGGER.info("Unit: " + ThisUnit);

            Facility ThisFacility = ContextHelper.getThreadFacility();
            UnitFacilityVisit ThisUFV = ThisUnit.getUfvForFacilityAndEventTime(ThisFacility,
                    inEvent.getEvent().getEventTime());
            if (ThisUFV == null) {
                LOGGER.error("Reference to UFV not found!");
                return;
            }
            else
                LOGGER.info("UFV: " + ThisUFV);

            UnitEquipment ThisUnitEquip = ThisUnit.getUnitPrimaryUe();
            EquipmentState ThisEqState = ThisUnitEquip.getUeEquipmentState();

            ThisEqState.setEqsFlexString03(ThisUFV.getUfvFlexString08());     // ECC
            LOGGER.info("Eq ECC set to: " + ThisUFV.getUfvFlexString08());

            //ThisEqState.setEqsFlexString02(ThisUnit.getUnitFlexString15());     // UCC
            //LOGGER.info("Eq UCC set to: " + ThisUnit.getUnitFlexString15());

            LOGGER.info("Update Successful.")
        }
        catch (Exception e) {
            LOGGER.error("Update Failed. Exception [" + e + "].");
        }
        finally {
            LOGGER.info("MATSyncUnitECCUCCOnUpdate Execution Ended.")
        }
    }
}