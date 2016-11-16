/*
 * Copyright (c) 2010 Zebra Technologies Corp. All Rights Reserved.
 * $Id: CustomCalculateStorageDays.groovy,v 1.1 2016/10/05 21:10:15 vnatesan Exp $
 */

import com.navis.inventory.external.inventory.AbstractStorageRule
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.Unit

/**
 * 2010-11-24 psethuraman ARGO-27225: custom storage days calculation
 **/
public class CustomCalculateStorageDays extends AbstractStorageRule {

    public Date calculateStorageStartDate(EFieldChanges inChanges) {
        Unit unit = (Unit)((FieldChanges) inChanges).getFieldChange(InventoryField.UFV_UNIT).getNewValue();
        String flexString = unit.getUnitFlexString01();
        log("executing groovy to set custom start day with unit flex string as " + flexString);
        return new Date();
    }

    public Date calculateStorageEndDate(EFieldChanges inChanges) {
        Unit unit = (Unit)((FieldChanges) inChanges).getFieldChange(InventoryField.UFV_UNIT).getNewValue();
        String flexString = unit.getUnitFlexString05();
        log("executing groovy to set custom end day with unit Flex string as " + flexString);
        return new Date()+1;
    }
}
