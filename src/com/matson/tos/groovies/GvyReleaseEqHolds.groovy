import com.navis.services.business.event.GroovyEvent
import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.api.IFlagType
import com.navis.argo.business.atoms.FlagPurposeEnum
import com.navis.argo.business.reference.*
import com.navis.argo.business.atoms.LogicalEntityEnum;
import com.navis.argo.business.api.LogicalEntity;
import com.navis.argo.business.api.Serviceable;

public class GvyReleaseEqHolds
{
    def servicesMgr = (ServicesManager)Roastery.getBean("servicesManager");
    //Method Get Active Holds for Unit
    public String releaseHoldsPermissions(Object  unit)
    {
        com.navis.argo.ContextHelper.setThreadExternalUser("-jms-");
        try
        {
            def holdFlags = unit.getFieldValue("unitAppliedHoldOrPermName")
            def flagIds = holdFlags != null ? holdFlags.split(",") : ''
            for(holdId in flagIds)
            {
                def  iFlageType = servicesMgr.getFlagTypeById(holdId)
                def logicalEntity =   iFlageType.getAppliesTo()
                def flagPurpose =  iFlageType.getPurpose().getKey()
                println(" N4Flags "+flagIds+"  iFlageType::"+iFlageType+"   logicalEntity::"+logicalEntity+"  flagPurpose ::"+flagPurpose)
                if(flagPurpose.equals('HOLD')  && logicalEntity.equals(LogicalEntityEnum.EQ))
                {
                    println("holdId:"+holdId+" logicalEntity:"+logicalEntity)
                    //Releasing Equip Holds
                    println("LogicalEntityEnum ::-1"+LogicalEntityEnum)
                    def equipmentId = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqIdFull")
                    def equipObj  =  Equipment.loadEquipment(equipmentId);
                    def operator = com.navis.argo.business.model.Operator.findOperator("MATSON");
                    def equipmentState =    com.navis.inventory.business.units.EquipmentState.findEquipmentState(equipObj,operator);
                    if(holdId.equals('LTV')){
                        releaseHold(equipmentState,holdId)
                    }
                }
            }//for ends
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method Ends

    public String releaseHold(Object unit,String holdId) {
        try
        {
            com.navis.argo.ContextHelper.setThreadExternalUser("-jms-");
            servicesMgr.applyPermission(holdId, unit, null, "EQ Hold Released Correction", true)
        }catch(Exception e){
            e.printStackTrace()
        }

    }


}