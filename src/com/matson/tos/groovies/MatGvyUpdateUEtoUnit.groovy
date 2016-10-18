import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.reference.Equipment
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Level

/**
 * Created by psethuraman on 7/14/2016.
 */
class MatGvyUpdateUEtoUnit extends GroovyApi {
    /**
     * Print document based on the configuration docTypeId parameter
     *
     * @param inOutDao
     */
    public void execute(Map parameters) throws Exception {
        //this.log(Level.INFO);
        this.log(" MatGvyUpdateUEtoUnit execute Stared.");
        try {
            UnitManager manager = Roastery.getBean(UnitManager.BEAN_ID);
            UnitFacilityVisit ufv = manager.findActiveUfvForUnitDigits("CXCU8781421");
            Unit unit = ufv.getUfvUnit();
            UnitFinder finder = Roastery.getBean(UnitFinder.BEAN_ID);
            UnitEquipment ue = finder.findActiveUeUsingEqInAnyRole(ContextHelper.getThreadOperator(), ContextHelper.getThreadComplex(), Equipment.findEquipment("CXCU8781421"));
            unit.setUnitPrimaryUe(ue);
        } catch (Exception e) {
            this.log(e)
            throw new BizViolation(e);
        }

        this.log(" MatGvyUpdateUEtoUnit execute Completed.");
    }
}
