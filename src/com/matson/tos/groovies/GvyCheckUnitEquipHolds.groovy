import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.reference.Equipment;
import com.navis.framework.util.internationalization.PropertyKey;
import com.navis.framework.util.internationalization.PropertyKeyFactory;
import com.navis.framework.util.message.MessageLevel;
import com.navis.inventory.business.atoms.EqUnitRoleEnum;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitEquipment;
import com.navis.road.business.atoms.TranSubTypeEnum;
import com.navis.road.business.model.TruckTransaction;
import com.navis.road.business.util.RoadBizUtil;
import com.navis.road.business.workflow.TransactionAndVisitHolder;
import com.navis.services.business.rules.Flag;
import com.navis.services.business.rules.FlagType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lcrouch
 * Date: 8/21/12
 * Time: 4:04 PM
 * Checks the unit equipment guardian holds on the chassis, sends error msg if the hold exists
 */
public class GvyCheckUnitEquipHolds {
    public void execute(TransactionAndVisitHolder dao, GroovyApi api) {
        TruckTransaction tran = dao.getTran();
        Unit unit = tran.getTranUnit();
        UnitEquipment unitEquipment;
        List<String> eqHoldsList = Arrays.asList("CL","SHOP");

        if(TranSubTypeEnum.DC.equals(dao.getTran().getTranSubType())){
            unitEquipment = unit.getUnitPrimaryUe();
        } else {
            unitEquipment = unit.getUeInRole(EqUnitRoleEnum.CARRIAGE);
        }
        if(unitEquipment != null){

            for(String aEqHold : eqHoldsList){
                FlagType ftype = FlagType.findFlagType(aEqHold);
                List flagList = Flag.findActiveFlagsForEntity(ftype,unitEquipment.getUeEquipmentState(),null);

                if(flagList != null && flagList.size() > 0){
                    PropertyKey HOLD_EXISTS = PropertyKeyFactory.valueOf("gate.hold_exists");
                    RoadBizUtil.appendMessage(MessageLevel.SEVERE,HOLD_EXISTS,aEqHold);
                }
            }
        }

    }
}
