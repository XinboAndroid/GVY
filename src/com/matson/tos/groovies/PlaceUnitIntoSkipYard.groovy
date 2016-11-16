/*
* SrNo Doer Date        Change
* A1   GR   07/07/10  Method to Detach Chassis
* A2   GR   07/12/10  IsOnVessel=true and chassis value dosent match then Detach
* A3   GR   07/26/10  Added NULL Check for Unit
* 08/16/11 2.1 Updated Email Method
*/
import com.navis.argo.ContextHelper;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.argo.business.model.LocPosition;
import com.navis.argo.business.model.Yard;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.road.business.workflow.TransactionAndVisitHolder;
import com.navis.road.business.util.RoadBizUtil;
import com.navis.framework.util.BizViolation;
import com.navis.framework.util.message.MessageLevel;
import com.navis.framework.util.internationalization.PropertyKey;
import com.navis.framework.util.internationalization.PropertyKeyFactory;
import com.navis.road.business.model.TruckTransaction;
import com.navis.argo.business.reference.Equipment;
import com.navis.inventory.business.api.UnitManager;
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.framework.business.Roastery


public class PlaceUnitIntoSkipYard {

    public static final String BEAN_ID = "placeUnitIntoSkipYard";
    public static PropertyKey PLACE_UNIT_INTO_SKIP_YARD = PropertyKeyFactory.valueOf("gate.place_unit_into_skip_yard");
    private static final String SKIP_YARD_BLOCK_NAME = "SKPYRD";
    private static final String emailTo = '1aktosdevteam@matson.com'

    public void execute(TransactionAndVisitHolder dao, api) {

        UnitFacilityVisit ufv = dao.getTran().getTranUfv();
        LocPosition currentPos = ufv.getUfvLastKnownPosition();
        if (LocTypeEnum.VESSEL.equals(currentPos.getPosLocType())) {
            Yard moveYard = ContextHelper.getThreadYard();
            LocPosition skipYardPos = LocPosition.resolvePosition(ufv.getUfvFacility(),
                    LocTypeEnum.YARD, moveYard.getYrdId(), SKIP_YARD_BLOCK_NAME, null, ufv.getBasicLength());
            Unit unit = dao.getTran().getTranUnit();
            try {
                unit.move(skipYardPos);
            } catch (BizViolation bv) {
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PLACE_UNIT_INTO_SKIP_YARD, unit.getUnitId(), bv.getLocalizedMessage());
            }
        }
    }



    public void detachChassis(TransactionAndVisitHolder dao, api)
    {
        String cntrNbr = null; String chassisNbr = null;
        try
        {
            TruckTransaction tran = dao.tran
            cntrNbr = tran.tranCtrNbr
            chassisNbr = tran.tranChsNbr
            Unit unit = dao.tran.tranUnit;
            if(unit == null){ //A3
                println('Unit Object Null at pass pass Gate')
                return;
            }
            def carriageId = unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull")
            def lkpPosLoc = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")

            if(carriageId == null){
                println('No chassis to Dismount at pass pass Gate')
                return;
            }

            if(chassisNbr == null && carriageId != null){
                unit.swipeChsByOwnersChs()
            }else if (carriageId != null && !carriageId.equals(chassisNbr) && LocTypeEnum.VESSEL.equals(lkpPosLoc)){
                unit.swipeChsByOwnersChs()
            }
        }catch(Exception e){
            def emailSender = api.getGroovyClassInstance("EmailSender");
            emailSender.custSendEmail(emailTo, "PassPass Error Detaching Chassis "+chassisNbr+" from Container "+cntrNbr, e.getMessage());
        }
    }//Method Ends
}
