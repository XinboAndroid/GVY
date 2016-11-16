import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.framework.portal.UserContext
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Logger

/*

AUTHOR: Siva Raja
Date Written: 06/04/2012
Description: Updates Unit details based on Stow plan posting through EDI.
This groovy is triggered from UNIT_PROPERTY_UPDATE general notices.

 */

public class MATProcessUnitUpdatesFromStowPlan extends GroovyApi {

    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    private static final String maeOperator = "MAE";
    private static final String mskOperator = "MSK";
    private static final String anlEquipOwner = "ANL";
    private static final String anlcEquipOwner = "ANLC";
    private static final String appendString = "U";
    private static final String client = "CLIENT";
    private static final String honFacility = "HON";
    private static final String optFacility = "OPT";
    private static final String ediEvent = "TESTGROOVY";
    private static final String unitEvent = "UNIT_ACTIVATE";

    List<String> MARSHALL_ISLAND_DEST = new ArrayList<String>();
    {
        MARSHALL_ISLAND_DEST.add("EBY");
        MARSHALL_ISLAND_DEST.add("JIS");
        MARSHALL_ISLAND_DEST.add("KWJ");
        MARSHALL_ISLAND_DEST.add("MAJ");
        MARSHALL_ISLAND_DEST.add("WAK");
    }
    List<String> GUAM_PORTS = new ArrayList<String>();
    {
        GUAM_PORTS.add("API");
        GUAM_PORTS.add("GUM");
        GUAM_PORTS.add("KMI");
        GUAM_PORTS.add("PAG");
        GUAM_PORTS.add("PNP");
        GUAM_PORTS.add("PPT");
        GUAM_PORTS.add("PUX");
        GUAM_PORTS.add("RTA");
        GUAM_PORTS.add("SPN");
        GUAM_PORTS.add("TIN");
        GUAM_PORTS.add("TMGU");
        GUAM_PORTS.add("UUK");
        GUAM_PORTS.add("YAP");
    }

    List<String> NIS_PORTS = new ArrayList<String>();
    {
        NIS_PORTS.add("HIL");
    }

    public void execute(GroovyEvent event, Object api){

        LOGGER.warn("MATProcessUnitUpdatesFromStowPlan Started !" + timeNow);
        Unit unit = (Unit) event.getEntity();
        LOGGER.warn("unit:" + unit);
        Event evnt = event.getEvent();
        String eventId = evnt.getEventTypeId();
        LOGGER.warn("Event:" + eventId);
        boolean process = false;
        if (unit != null){
            //If the event is from processing the EDI stow plan process the unit updates
            UnitFacilityVisit ufv = this.findUfv(unit);
            if (eventId.equals(ediEvent))
            {
                process = true;
            }
            // If the event is from UNIT_ACTIVATE, then confirm that the unit has a valid stow position and then process the unit updates.
            // We are doing this here because when new units are created using stowplan event is not getting recorded. May be a N4 2.1 issue.
            // When we upgrade to 2.2 and higher Positing should be changed to LocPosition
            if (eventId.equals(unitEvent))
            {
                LocTypeEnum currentPositionType = null;
                if (ufv != null) {
                    currentPositionType = ufv.getUfvLastKnownPosition().getPosLocType();
                }
                if (currentPositionType.equals(LocTypeEnum.VESSEL)){
                    process = true;
                }
            }
            LOGGER.warn("process:" + process.toString());
            if (process.toString() == "true") {
                this.updateUfvProperties (unit);
                this.updateContainerProperties(unit);
                this.updateUnitProperties(unit);
            }
        }
        LOGGER.warn("MATProcessUnitUpdatesFromStowPlan Ended !" + timeNow);
    }

    private UnitFacilityVisit findUfv(Unit inUnit) {
        UnitFacilityVisit ufv;
        Facility fcy = Facility.findFacility(honFacility) ;
        if (fcy != null){
            ufv = inUnit.getUfvForFacilityNewest(fcy);
        }
        return ufv;
    }

    // this method is used to update Unit Facility Visit
    private void updateUfvProperties (Unit inUnit){
        UnitFacilityVisit inUfv = this.findUfv(inUnit);
        // Set transit state to inbound irrespective of Vessel Visit transit state.
        if (inUfv != null){
            RectifyParms parms = new RectifyParms();
            parms.setUfvTransitState(UfvTransitStateEnum.S20_INBOUND);
            try{
                inUfv.rectify(parms);
                LOGGER.warn ("Unit Facility Visit rectified");
            } catch (Exception e) {
                LOGGER.warn("Rectify Failed" + e);
            }
        }
    }

//this method is used to update equipment and container properties.

    private void updateContainerProperties(Unit inUnit){

        Equipment inEq = inUnit.getPrimaryEq();
        if (inEq != null){
            UnitEquipment ue = inUnit.getUnitPrimaryUe();
            EquipmentState eqs = ue.getUeEquipmentState();
            // Set equipment flex string 01 to "CLIENT" for each container attached to the bill of lading.
            try{
                eqs.setEqsFlexString01(client);
            } catch (Exception e){
                LOGGER.warn(" Error in updating the Equipment Flex String " + e);
            }


            // If the Equipment Operator = MSK set the Equipment Operator = MAE
            String  equipOperator = inEq.getEquipmentOperatorId();
            ScopedBizUnit eqMaeOperator = ScopedBizUnit.findEquipmentOperator(maeOperator);
            if (equipOperator.equals(mskOperator)) {
                try {
                    eqs.upgradeEquipmentOperator(eqMaeOperator,DataSourceEnum.EDI_STOW);
                } catch (Exception e){
                    LOGGER.warn(" Error in updating the Equipment Operator for MAE " + e);
                }
            }
            // "If equipoperator = ANL then set equipowner = ANLC else equipowner = equipoperator+”U” "
            LOGGER.warn (" equipment owner :" + inEq.equipmentOwnerId);
            ScopedBizUnit equipmentAnlOwner = ScopedBizUnit.findEquipmentOwner(anlcEquipOwner) ;
            if (equipOperator.equals(anlEquipOwner))
            {
                try {
                    eqs.upgradeEquipmentOwner(equipmentAnlOwner,DataSourceEnum.EDI_MNFST);
                } catch (Exception e){
                    LOGGER.warn(" Error in updating the Equipment Operator for ANL " + e);
                }
            }
            else {
                String equipmentOwnerString = equipOperator + appendString;
                ScopedBizUnit equipmentOwner = ScopedBizUnit.findEquipmentOwner(equipmentOwnerString);
                try {
                    eqs.upgradeEquipmentOwner(equipmentOwner,DataSourceEnum.EDI_STOW);
                } catch (Exception e){
                    LOGGER.warn(" Error in updating the Equipment Operator " + equipmentOwnerString + "   " + e);
                }
            }
        }
    }

    private void updateUnitProperties (Unit inUnit){

        //If the BL Number is null then update BL number with hard coded text
        GoodsBase goods = inUnit.getUnitGoods();
        String blNbr =  goods.getGdsBlNbr();
        if (blNbr == null){
            // Update the Unit Notes with hard coded text
            try{
                inUnit.updateRemarks("Stowplan Data");
                goods.setGdsBlNbr("DO NOT EDIT - WAIT FOR NEWVESS");
            } catch (Exception e){
                LOGGER.warn("Unable to update commodity description " + e);
            }

        }
        //If discharge port is not HON then set Unit Category to Through
        RoutingPoint point = inUnit.getUnitRouting().getRtgPOD1();
        String disc = point.getPointId();

        if (!disc.equals(honFacility)){
            inUnit.updateCategory(UnitCategoryEnum.THROUGH);
        }
        //If Unit Freight Kind is MTY and the Discharge port is HON then Unit Category to Import
        if ((FreightKindEnum.MTY.equals(inUnit.getUnitFreightKind())) && (disc.equals(honFacility))){
            inUnit.updateCategory(UnitCategoryEnum.IMPORT);
        }
        //If Unit Freight Kind is MTY and the Discharge port is OPT then Unit Category to Import
        if ((FreightKindEnum.MTY.equals(inUnit.getUnitFreightKind())) && (disc.equals(optFacility))){
            inUnit.updateCategory(UnitCategoryEnum.IMPORT);
        }
        //if unit destination is Marshal Islands port (EBY, JIS, KWJ, MAJ, WAK)
        //then set category = TRANSSHIP
        //if unit destination is Guam port (API, GUM, KMI, PAG, PNP, PPT, PUX, RTA, SPN, TIN, TMGU, UUK, YAP)
        //then set category = TRANSSHIP
        // if unit destination is NIS ports (HIL)
        // then set the unit pod1 as the destination routing point.

        String destination = inUnit.getUnitGoods().getGdsDestination();

        if (!destination.equals(null)){
            if (MARSHALL_ISLAND_DEST.contains(destination)){
                inUnit.updateCategory(UnitCategoryEnum.TRANSSHIP);
            }
            if (GUAM_PORTS.contains(destination)){
                inUnit.updateCategory(UnitCategoryEnum.TRANSSHIP);
            }
            if (NIS_PORTS.contains(destination)){
                RoutingPoint destPoint = RoutingPoint.findRoutingPoint(destination);
                if (!destPoint.equals(null)){
                    inUnit.getUnitRouting().setRtgPOD1(destPoint);
                }
            }
        }

    }
    private static final Logger LOGGER = Logger.getLogger(MATProcessUnitUpdatesFromStowPlan.class);
}
