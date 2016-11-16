/*
*  SrNo  doer  date        Change
*  A1    GR    06/07/2010  Added Attached EQ date to cntr chassis equipment.
*  A2    GR    06/29/2010  Attached EQ date to Bare Cntr Equipment
*  A3    GR    10/27/10    Update Unit Activate Message
*  A4    GR    10/13/11    TOS2.1 Change Departed to Retire
*/
import com.navis.inventory.business.api.UnitManager;
import com.navis.inventory.business.api.RectifyParms;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.framework.business.Roastery;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.services.business.event.GroovyEvent;
import com.navis.inventory.business.atoms.EqUnitRoleEnum;
import com.navis.inventory.business.units.UnitEquipment
/*
* Class Departs all the UFV Units and sets the Master Visit State Departed
*/
public class GvyInjNLTProcess
{
    def ibCarrier = ''
    def obCarrier = ''
    def inUnit = null
    def complex = null
    def inEquipment = null
    def injBase = null
    def unitFinder = null
    def equiClass = null
    def unitId = ""


    //Method Set all the Complex Level Active units to Departed
    public String processNLT(Object unit)
    {
        com.navis.argo.ContextHelper.setThreadExternalUser("jms");
        try
        {

            //Processing One unit at a time
            unitId = unit.getFieldValue("unitId");
            def carriage = unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull")

            // EQUIP CLASS
            equiClass =unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            equiClass = equiClass != null ? equiClass.getKey() : ''

            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            ibCarrier =  unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId");
            obCarrier = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")

            //Accessory
            def unitAccessory = unit.getUnitAcryEquipIds()
            def chassiAccessory = unit.getUnitCtrAccessory()
            chassiAccessory = chassiAccessory != null ? chassiAccessory : ''
            def accessory = unitAccessory == null ? chassiAccessory : ''

            injBase = new GroovyInjectionBase()
            unitFinder = injBase.getUnitFinder()
            complex = ContextHelper.getThreadComplex();

            /* Case1 : If Accessory is Attached to Primary Unit Chassis
               - Swipe & Depart accessory at a complex level if attached to active unit(chassis)
               Case2 : If Accessory/Chassis is Attached to Primary Unit Container
               - Swipe & Depart accessory/Chassis at a complex level if attached to active unit(Container)
               Case3 : If Accessory/Chassis/Conatiner is Primary unit
               -- Swipe & Depart Primary unit a Complex level so that NLT can Bring in the unit.
            */
            if(transitState.equals('S10_ADVISED') && chassiAccessory !=null && equiClass.equals('CHASSIS')){
                swipeAttachAccessory(chassiAccessory)

                //Depart Active ufv entires for Accessory
                processUnit(chassiAccessory)
            }
            //Primary=Container and secondary=chassis (check if chassis is attached to an active unit)
            if(transitState.equals('S10_ADVISED') && carriage !=null && equiClass.equals('CONTAINER')){

                //swipe & Depart accessory if attached to active unit
                if(unitAccessory != null){
                    swipeAttachAccessory(unitAccessory)
                    processUnit(unitAccessory)
                }

                //swipe chassis if attached to active unit
                swipeAttachChassis(carriage)

                //Depart Active ufv entires for chassis
                processUnit(carriage)
            }
            //IF Accessory or Chassis or Container as Primary Unit
            if(transitState.equals('S10_ADVISED')){
                //Chassis to Check if it is associated with another unit.

                if(equiClass.equals('ACCESSORY')){
                    swipeAttachAccessory(unitId)
                }
                if(equiClass.equals('CHASSIS')){
                    swipeAttachChassis(unitId)
                }
                //Depart Active ufv entires
                processUnit(unitId)

                //Set Advised Unit to Active
                setAdviseUnitToActive(injBase, unit)
            }else{
                println("Did Not Process as unit is not Advised :"+unitId+" Tstate:"+transitState)
            }

        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method execute Ends

    public void processUnit(String unitId)
    {
        Set ufvSet = null
        try
        {
            ufvSet = findVisitStateActiveUnit(unitId)
            def ufvSize = ufvSet!= null ? ufvSet.size() : 0

            //Check for Multiple ufv in Active state: Depart the Active unit First
            if(ufvSize > 1){
                for(aUfvDept in ufvSet){
                    if (UnitVisitStateEnum.ACTIVE.equals(aUfvDept.getUfvVisitState())) {
                        RectifyParms rparms = new RectifyParms();
                        rparms.setUfvTransitState(UfvTransitStateEnum.S99_RETIRED);
                        rparms.setUnitVisitState(UnitVisitStateEnum.RETIRED)
                        aUfvDept.rectify(rparms);
                    }
                }//for Ends
            }//If Ends

            //Condition to depart Active Ufv entries at a complex level.
            for(aUfv in ufvSet)
            {
                def ufvIbCarrier = aUfv.getUfvActualIbCv() != null ? aUfv.getUfvActualIbCv().getCvId() : "";
                def ufvObCarrier = aUfv.getUfvIntendedObCv() != null ? aUfv.getUfvIntendedObCv().getCvId() : "";

                //If is unit Departed and File IB and OB carrier is same as N4 then Delete unit
                // Added this Code to stop creating Multiple units Entries on the NLT if same NLT is executed twice.
                // If Blank OB in the NLT file then the Delete Check is passed

                if(ibCarrier.equals(ufvIbCarrier) && obCarrier.equals(ufvObCarrier)){
                    Unit myUnit = inUnit
                    if(myUnit != null){
                        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID)
                        unitManager.purgeUnit(myUnit)
                    }
                }
                else  if (!UnitVisitStateEnum.DEPARTED.equals(aUfv.getUfvVisitState()) || UnitVisitStateEnum.DEPARTED.equals(aUfv.getUfvVisitState())) {
                    RectifyParms rparms = new RectifyParms();
                    rparms.setUfvTransitState(UfvTransitStateEnum.S99_RETIRED);
                    rparms.setUnitVisitState(UnitVisitStateEnum.RETIRED)
                    aUfv.rectify(rparms);
                }//else if Ends
            }//for ends
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    // Method returns a Set of Complex Level Master State Active Units
    public Set findVisitStateActiveUnit(String unitId)
    {
        Set unitUfvSet = null
        try{
            inEquipment = Equipment.loadEquipment(unitId);
            inUnit = unitFinder.findActiveUnit(complex,inEquipment)
            unitUfvSet = inUnit != null ? inUnit.getUnitUfvSet() : null;
        }catch(Exception e){
            e.printStackTrace()
        }
        return unitUfvSet
    }

    public void setAdviseUnitToActive(Object injBase, Object unit){
        try{
            def unitDetails = injBase.getGroovyClassInstance("GvyUnitLookup")
            def unitIdGky=unit.getFieldValue("unitGkey")
            def visit = unitDetails.lookupFacility(unitIdGky)
            if(visit != null)
            {
                // A1, Set Visible in sparcs true
                visit.setFieldValue("ufvVisibleInSparcs", true);
                visit.setFieldValue("ufvTransitState", UfvTransitStateEnum.S20_INBOUND)
                visit.setFieldValue("ufvVisitState", UnitVisitStateEnum.ACTIVE)
                unit.setFieldValue("unitVisitState",UnitVisitStateEnum.ACTIVE)

                //Trigger N4 Unit Activate
                def event = new GroovyEvent( null, unit);
                event.postNewEvent( "UNIT_ACTIVATE","Key Dup Proc Execution for NV/NLT"); //A3

                //Set Attached Cntr Chassis Time
                setAttachedEqTime(unit)
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    //Method Swipes chassis attached to active unit so taht chassis cant be associated with another unit
    public void swipeAttachChassis(String carriage){
        try
        {
            if(carriage != null){
                def chasEquip = Equipment.loadEquipment(carriage);
                def attChasUnit = unitFinder.findAttachedUnit(complex,chasEquip)
                def attChasUnitClass = attChasUnit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
                attChasUnitClass = attChasUnitClass != null ? attChasUnitClass.getKey() : ''

                def attChasUnitTstate = attChasUnit.getFieldValue("unitActiveUfv.ufvTransitState")
                attChasUnitTstate = attChasUnitTstate != null ? attChasUnitTstate.getKey() : ''

                if(attChasUnit != null && attChasUnitClass.equals("CONTAINER") && !attChasUnitTstate.equals('S10_ADVISED')){
                    def attChasUnitId = attChasUnit.getFieldValue("unitId")
                    attChasUnit.swipeChsByOwnersChs()
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    //Method Swipes chassis attached to active unit so taht chassis cant be associated with another unit
    public void swipeAttachAccessory(String equiMg){
        try
        {
            if(equiMg != null){
                def mgEquip = Equipment.loadEquipment(equiMg);
                def attMgUnit = unitFinder.findAttachedUnit(complex,mgEquip)
                def attMgUnitClass = attMgUnit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
                attMgUnitClass = attMgUnitClass != null ? attMgUnitClass.getKey() : ''

                def attMgUnitTstate = attMgUnit.getFieldValue("unitActiveUfv.ufvTransitState")
                attMgUnitTstate = attMgUnitTstate != null ? attMgUnitTstate.getKey() : ''

                if(attMgUnit != null && attMgUnitClass.equals("CHASSIS") && !attMgUnitTstate.equals('S10_ADVISED')){
                    def attMgUnitId = attMgUnit.getFieldValue("unitId")
                    attMgUnit.detachAccessoriesOnChassis("NLT Process : Detached Acry from Chassis")
                }
                else if(attMgUnit != null && attMgUnitClass.equals("CONTAINER") && !attMgUnitTstate.equals('S10_ADVISED')){
                    def attMgUnitId = attMgUnit.getFieldValue("unitId")
                    //UnitEquipment priUnitEquipment = attMgUnit.getUnitPrimaryUe()
                    //UnitEquipment acryUe  = attMgUnit.getUeAccessory();
                    //attMgUnit.detachAllEquipmentOfRole(EqUnitRoleEnum.ACCESSORY,"NLT Process : Detached Acry from Container");
                    attMgUnit.detachAccessoriesOnChassis("NLT Process : Detached Acry from Chassis")
                }

            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

// Method Sets Cntr & Chassis Attached Equip time
    public void setAttachedEqTime(Object unit)
    {
        UnitEquipment cntrEquip = unit.getUnitPrimaryUe();
        UnitEquipment chasEquip = unit.getUnitCarriageUe();
        if(cntrEquip != null && chasEquip == null){
            Calendar calendarHst = Calendar.getInstance();
            Date cntrDate = calendarHst.getTime()
            cntrEquip.setUeAttachTime(cntrDate);

        }else if(cntrEquip != null && chasEquip != null){
            Calendar calendarHst = Calendar.getInstance();
            Date chasDate = calendarHst.getTime()

            calendarHst.add(Calendar.MINUTE, -2);
            Date cntrDate = calendarHst.getTime()
            cntrEquip.setUeAttachTime(cntrDate);
            chasEquip.setUeAttachTime(chasDate);
        }

    }//Method Ends

}//Class Ends