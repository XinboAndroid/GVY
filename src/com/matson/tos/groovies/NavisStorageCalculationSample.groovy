/*
 * Copyright (c) 2013 Navis LLC. All Rights Reserved.
 *
 */
import com.navis.inventory.business.units.UnitStorageCalculation
import com.navis.inventory.external.inventory.AbstractStorageCalculation

/**
 * This groovy is used to set the values ufvFlexStorageString01, ufvFlexStorageString02, ufvFlexStorageString03, ufvFlexStorageDouble01,
 * ufvFlexStorageDouble02 and ufvFlexStorageDouble03 as needed.
 * These values to be configured in Fields.
 * Once the fields are configured it automatically displayed in UnitInspector --> Storage and Line Storage panel.
 * The value needs to be populated using the groovy code.
 * Author: Murali Raghavachari
 * Date Written: 11/25/2013
 * SFDC : 76034
 * JIRA : ARGO-53280, CSDV-1523
 * Called From:
 *
 **/
public class NavisStorageCalculationSample extends AbstractStorageCalculation {

    @Override
    public void unitStorageCalculationExtension(Map inMap) {
        if (!inMap.isEmpty()) {
            UnitStorageCalculation usc = (UnitStorageCalculation) inMap.get("UnitStorageCalculation"); //UNIT_STORAGE_CALCULATION is not working. hard coded
            if (usc != null) {
                String chargeFor = usc.getChargeFor();
                if (("STORAGE").equals(chargeFor)) {
                    usc.setUfvFlexStorageDouble01(10.00);
                    usc.setUfvFlexStorageDouble02(20.00);
                    usc.setUfvFlexStorageString01("Testing STORAGE.Sample hard coded value");
                } else if (("LINE_STORAGE").equals(chargeFor)) {
                    usc.setUfvFlexStorageDouble01(33.00);
                    usc.setUfvFlexStorageDouble02(44.00);
                    usc.setUfvFlexStorageString01("Testing LINE STORAGE.Sample hard coded value");
                }
            }
        }
    }
}
