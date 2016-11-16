import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.*
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.GroovyEvent
import com.navis.argo.business.atoms.DrayStatusEnum

public class MdaHoldProc {
    public String process(Object equip) {

        def equiId = equip.getFieldValue("eqsEquipment.eqIdFull") // Object Equipment.EqId

        //println("equiId = "+equiId)

        def injBase = new GroovyInjectionBase();

        def complex = ContextHelper.getThreadComplex();

        def unitFinder = injBase.getUnitFinder();

        def eq = Equipment.loadEquipment( equiId);

        def unit = unitFinder.findAttachedUnit(complex, eq);

        if ( unit != null) {
            def unitId = unit.getFieldValue("unitId");
            //println( "UnitId = " + unitId);
            unit.setFieldValue( "unitDrayStatus", DrayStatusEnum.RETURN);
        } else {
            println( "Unit is null.");
        }


    }
}