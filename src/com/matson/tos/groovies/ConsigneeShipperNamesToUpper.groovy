/**
 * Author: Karthik Rajendran
 * Created date: 06/29/2013
 * Description: This Groovy sets the Consignee, Shipper fields to upper case and invoke update MatUpdateUnitConsigneeNotes groovy
 *
 */

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.apex.business.model.GroovyInjectionBase

public class ConsigneeShipperNamesToUpper extends AbstractEntityLifecycleInterceptor {
    public void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.log("START : ******* ConsigneeShipperNamesToUpper.onUpdate() *******")
        try {
            Unit unit =  inEntity._entity
            this.log("Unit ID = "+unit.unitId+"\t BLNBR = "+unit.getFieldValue("unitGoods.gdsBlNbr"))
            GoodsBase goods = unit.getUnitGoods()
            def inj = new GroovyInjectionBase()
            // Shipper Name To Upper case
            def unitShipper = unit.getFieldValue("unitGoods.gdsShipperAsString")
            def unitShipperUpper = null
            if(!unitShipper.equals(null)) {
                unitShipperUpper = unitShipper.toUpperCase()
                if(!unitShipper.equals(unitShipperUpper)) {
                    unit.setFieldValue("unitGoods.gdsShipperBzu.bzuName",unitShipperUpper)
                    unit.setFieldValue("unitGoods.gdsShipperBzu.bzuId",unitShipperUpper)
                }
            }
            this.log("ORIG SHIPPER="+unitShipper+"\t -> \t NEW SHIPPER="+unitShipperUpper)
            // Consignee Name To Upper case
            def unitConsignee = unit.getFieldValue("unitGoods.gdsConsigneeAsString")
            def unitConsigneeUpper = null
            if(!unitConsignee.equals(null)) {
                unitConsigneeUpper = unitConsignee.toUpperCase()
                boolean isOrigConsigneeUpper = unitConsignee.equals(unitConsigneeUpper)
                if(!isOrigConsigneeUpper) {
                    unit.setFieldValue("unitGoods.gdsConsigneeBzu.bzuName",unitConsigneeUpper)
                    unit.setFieldValue("unitGoods.gdsConsigneeBzu.bzuId",unitConsigneeUpper)
                }
            }
            this.log("ORIG CONSIGNEE="+unitConsignee+"\t -> \t NEW CONSIGNEE="+unitConsigneeUpper)
            // Consignee Notes Update
            def MatUpdateUnitConsigneeNotes = inj.getGroovyClassInstance("MatUpdateUnitConsigneeNotes")
            MatUpdateUnitConsigneeNotes.updateNotes(unit)
        }
        catch (Throwable e) {
            this.log("ConsigneeShipperNamesToUpper.onUpdate(): error occured while attempting to set ShipperName/ConsigneeName field to uppercase \n" + e.getMessage())
            e.printStackTrace()
        }
        this.log("END : ******* ConsigneeShipperNamesToUpper.onUpdate() *******")
    }
}

