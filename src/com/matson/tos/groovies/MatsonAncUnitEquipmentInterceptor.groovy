/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.EqBaseOrderItem
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 Whenever the booking number of a unit is updated, the value of the booking unit gets copied to the bill of lading (BL) number of the unit.
 This includes the cases when booking number is set to null, or from null to a value.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 10/21/2015
 *
 * Date: 10/21/2015: 5:41 PM
 * JIRA: CSDV-3307
 * SFDC: 00146342
 * Called from: Entity Life Cycle Interceptor for UnitEquipment entity.
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncUnitEquipmentInterceptor extends AbstractEntityLifecycleInterceptor {
    @Override
    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor invoked onCreate Method.");
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor onCreate");
        if (inOriginalFieldChanges.hasFieldChange(InventoryField.UE_DEPARTURE_ORDER_ITEM)) {
            LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor UE_DEPARTURE_ORDER_ITEM");
            copyBkgNbrToBLNbr(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
            //EqBaseOrderItem item = inOriginalFieldChanges.findFieldChange(InventoryField.UE_DEPARTURE_ORDER_ITEM) as EqBaseOrderItem;
        }
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor completed onCreate Method.");
    }

    @Override
    public void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor invoked onUpdate Method.");
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor onUpdate");
        copyBkgNbrToBLNbr(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor completed onUpdate Method.");
    }

    private void copyBkgNbrToBLNbr(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor copyBkgNbrToBLNbr");
        def matsonAncUnitEquipmentLibrary = getLibrary("MatsonAncUnitEquipmentLibrary");
        LOGGER.info("MatsonAncUnitEquipmentInterceptor about to execute MatsonAncUnitEquipmentLibrary");
        if (matsonAncUnitEquipmentLibrary == null) {
            LOGGER.info("MatsonAncUnitEquipmentInterceptor, couldn't find the library MatsonAncUnitEquipmentLibrary");
            return;
        }
        Map param = new HashMap();
        param.put("ENTITY", inEntity);
        param.put("ORIGINAL_FIELD_CHANGES", inOriginalFieldChanges);
        param.put("MORE_FIELD_CHANGES", inMoreFieldChanges);
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor param"+param);
        matsonAncUnitEquipmentLibrary.execute(param);
        LOGGER.info("MatsonAncUnitEquipmentInterceptor completed calling MatsonAncUnitEquipmentLibrary");
    }

    private Logger LOGGER = Logger.getLogger(MatsonAncUnitEquipmentInterceptor.class);
}