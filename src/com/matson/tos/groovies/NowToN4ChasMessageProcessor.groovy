/*
*  Handeled
*  Now sends Mount Message and In N4 chassis is on Active unit then Dismount from Active unit
*  Retire Active bare Chassis before Mount
*  Chassis with and without wildChar Lookup
*  Chassis Creation
   05/05/2010
*  A1 - Change to Pass Messages on 1. UTR & Chassis marriage  2.Bare Chassis Update
*  A1 - Lookup Active unit of chassis checkdigit (X and valid number) and then
   A1 - Add Bare chassis Position Check
   05/11
   A2 - Lookup active unit with Master State
   A2 - Comment out dismounting for Load back chassis
   A2 - Handel Correct chassis lookup
   05/13
   A3 - Planned position chekc for stacks
   A3 - Create bare Chassis Active InYard Chassis only for Valid yard position
   05/14
   A4 - Commented Dismount
   A4 - Auto UNIT_LOAD for the ALE
   A4 - Dismount Cntr on bare chassis update from valid position.
   05/18
   A5 - Dismount existing chassis before mounting new chassis.
        issue : as now we are creating chassis only on valid positions
   05/28
   A6  - Swipe existing chassis before mounting new chassis in Unit on VESSEL
   06/07/10
   A7 - Add code to fix incorrect attached unit on the gateActiveChassis lookup.
        This fix would avoid error UNITS__EQUIP_ALREADY_ACTIVE
   06/10/2010
   A8 - Add Position TRUCK check before Dismount chassis
        Removed the Swipe Code to added dismount method.
   06/14/10
   A9 - Dismount Chassis from Other Facility and make Attached unit=NULL in Current Facility.
   06/18/10
   A10- Adding DVI chassis Notification
   06/29/10 - Removed DVI notification Method
   A11- Roll Over MG on Mount action
        Owner and Operator of New Chassis
		Stop Creating Facility Visit for BombCart Chassis
   A12 - Return Code for Containers with NO ACTIVE UFV
   A13 - ALE attach Chassis Check added on unit LOAD. TT#8408
   A14 - TT#11271 Attached unit Method lookup changed
   A15 - TT#12428 Handel Obsolete Chassis
   A16 - 05/24/11 - TT#  Stop Creating Chassis
   A17 - 05/25/11 - TT#12502 Suppress Bare chas moves on Vessel
   A18 - 05/25/11 - Post Email Alerts only for Valid Yard Position
   A19 - 08/12/11 - Added F&M to chassis Email
   A20 - 08/15/11 - Added Yard Check and Suppressed Email
   * 08/16/11 2.1 Updated Email Method
   A21 - 04/05/2012  Suppressed Dismount Bare chassis as not N4 take care of it
*/

import com.navis.argo.business.model.Facility
import com.navis.framework.business.Roastery
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.model.Yard

// Peter Seiler replace Position with LocPosition

import com.navis.argo.business.model.LocPosition
import com.navis.inventory.business.api.UnitFinder
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Equipment;
import com.navis.inventory.business.units.UnitEquipment;
import com.navis.argo.business.reference.Chassis
import com.navis.inventory.business.units.Unit;
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.LineOperator;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.argo.business.atoms.DataSourceEnum;
import com.navis.argo.business.atoms.CarrierModeEnum;
import com.navis.inventory.business.api.UnitManager;
import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.argo.ArgoBizMetafield;
import com.navis.argo.business.api.ILocationLoader;
import com.navis.argo.business.model.ILocation;
import com.navis.argo.business.api.GroovyApi;

import java.util.List;
import java.util.Map
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.reference.ScopedBizUnit

import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.jms.*;

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoRefField;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.business.atoms.LifeCycleStateEnum;

/**
 *
 * Patch Matson N4 Groovy for 2.1/2.6 upgrade
 *
 * Author: Peter Seiler
 * Date: 6 July 2014
 * JIRA: ARGO-59892
 * SFDC: None
 * Called from: Unkown
 *
 */

public class NowToN4ChasMessageProcessor extends GroovyInjectionBase{

    UnitFinder unitFinder = null;
    boolean isDismount = false
    def emailSender = null;
    private static final String chasErrorEmail = '1aktosdevteam@matson.com,1aktosdevteam@matson.com';
    private static final String emailTo = '1aktosdevteam@matson.com'
    private static final String emailfrom = '1aktosdevteam@matson.com'
    //private static final String emailTo = '1aktosdevteam@matson.com'
    private static final String eol = "\r\n";
    private static final String tab = "\t";
    List chassisEquipList = null;  def equipPrimary = null; def equipSecondary = null;
    def position = null; def note = null;
    def complex = null;  Facility facility = null;
    GroovyApi gvyApi = new GroovyApi();

    public String execute(Map inParameters) {

        equipPrimary = (String) inParameters.get("equipPrimary");
        equipSecondary = (String) inParameters.get("equipSecondary");
        position = (String) inParameters.get( "position");
        note = (String) inParameters.get("note");
        def recorder = (String) inParameters.get("recorder");
        def count = (String) inParameters.get("count");
        int counter = count == null ? 0 : Integer.parseInt(count);

        position = manipulatePosition(position)

        complex = ContextHelper.getThreadComplex();
        facility = ContextHelper.getThreadFacility()
        ContextHelper.setThreadExternalUser("now");

        def temp = null;
        Equipment chasEq = null;
        String chasEquipId = null;
        Equipment acryEquip = null;
        try
        {
            //Get primaryEquipment unit
            unitFinder = (UnitFinder)Roastery.getBean("unitFinder");

            //1 - IF Equipment Cntr & Chas not null
            if(equipPrimary != null && equipSecondary != null)
            {
                //A11 - Code for Chassis Lookup
                chasEquipId = findorCreateChassis(equipSecondary.replace(' ',''))
                if(chasEquipId == null){
                    fail( "ERR_GVY_NowToN4_01 Cannot Find or Create Chassis Equipment :"+equipSecondary);
                }

                //1.1 Get Active ufv and Mount Chas
                def ufv = null;
                try{
                    ufv = findActiveUfv(equipPrimary);
                }catch(Exception e){
                    println('Container With NO ACTIVE UFV ='+equipPrimary)
                    return;
                }

                if(ufv == null){ return; }

                //1.2 Auto Load TO ALE Vessel
                if('VESSEL'.equals(position)){  //Auto Load Code for ALE
                    if(equipSecondary == null){
                        return;
                    } //A17
                    autoUnitLoad(position, ufv, equipSecondary)
                    return;
                }

                Unit dismountedUnit = null;
                def attachedUnit = null;
                def map =  getActiveChassis(chassisEquipList,equipPrimary, complex, unitFinder) //101
                if(map != null){
                    Iterator it = map.keySet().iterator();
                    while (it.hasNext()){
                        def aEquipMap = it.next();
                        chasEquipId = aEquipMap   //101
                        attachedUnit = map.get(aEquipMap)
                    }
                }
                Equipment eq = Equipment.findEquipment(chasEquipId);

                def attachedUnitId = ''
                if(attachedUnit != null){
                    attachedUnitId = attachedUnit.unitId
                }else{
                    println("Attacehd unit is null maybe mount at ingate equipPrimary="+equipPrimary+" equipSecondary="+equipSecondary)
                }
                //1.3 Dismount Attached unit if not current unit passed by now
                if(attachedUnit != null && !equipPrimary.equals(attachedUnitId)
                        && 'CONTAINER'.equals(attachedUnit.unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass.key)){
                    dismountChassis(attachedUnit,eq,unitFinder);
                    println("Dismounted N4 Chassis="+equipSecondary+"  attachedUnitId"+attachedUnitId)
                }
                //1.4 If there are active bare chassis in the Yard then Retire those
                def equipId  = dismountedUnit != null ? dismountedUnit.unitId : chasEquipId
                if(equipId != null && !equipId.contains('swiped')){
                    println("equipId="+equipId+" dismount="+(dismountedUnit != null ? dismountedUnit.unitId : "NO-DISMOUNT"))
                    acryEquip = retireBareChassis(equipId, complex, unitFinder)
                }
                //1.5 Compare UFV to update Position
                if(equipPrimary.equals(attachedUnitId)){
                    println("SAME UNIT Check for Position update :equipPrimary="+equipPrimary+" attachedUnit="+attachedUnitId)
                    def updatePosUnit = ufv.getUfvUnit()
                    def slot = ufv.ufvLastKnownPosition.posSlot
                    if((slot != null && !slot.equals(position)) &&
                            !ufv.ufvLastKnownPosition.posLocType.equals(LocTypeEnum.VESSEL) && !position.startsWith('TR-')){ //A1
                        updatePosition(updatePosUnit, position, complex, unitFinder)
                    }
                }else if(!equipPrimary.equals(attachedUnitId)){
                    def mountUnitUfv = findActiveUfv(equipPrimary);
                    def mountUnit = mountUnitUfv.getUfvUnit()

                    //Dismount chassis if there is a chassis attached
                    dismountChassis(mountUnit,eq,unitFinder);

                    mountChassis(mountUnitUfv,chasEquipId,acryEquip)

                    def slot = mountUnitUfv.ufvLastKnownPosition.posSlot
                    if((slot != null && !slot.equals(position)) &&
                            !mountUnitUfv.ufvLastKnownPosition.posLocType.equals(LocTypeEnum.VESSEL) && !position.startsWith('TR-')){ //A1
                        //println("slot="+slot+" mountUnit="+mountUnit)
                        updatePosition(mountUnit, position, complex, unitFinder)
                    }
                }

            }else if(equipPrimary != null && equipSecondary == null){

                if('VESSEL'.equals(position)){  //A17 - Auto Load Code for ALE
                    return;
                }
                //Get Chassis FullId
                chasEquipId = findorCreateChassis(equipPrimary.replace(' ',''))
                Unit attachedUnit = null;
                def map =  getActiveChassis(chassisEquipList,equipPrimary, complex, unitFinder)
                if(map != null){
                    Iterator it = map.keySet().iterator();
                    while (it.hasNext()){
                        def aEquipMap = it.next();
                        chasEquipId = aEquipMap
                        attachedUnit = map.get(aEquipMap)
                    }
                }

                if(chasEquipId == null){
                    fail( "ERR_GVY_NowToN4_02 Cannot Find or Create Chassis Equipment :"+equipPrimary);
                }

                //Handel UfvVisit=Departed and UnitVisit=ACTIVE condition
                Unit oldUnitAttached =  attachedUnit;
                if(attachedUnit != null && !UnitVisitStateEnum.ACTIVE.equals(attachedUnit.unitActiveUfv.ufvVisitState)){
                    //A21  dismountChassis(attachedUnit); //A8
                    attachedUnit = null;
                }//Find Unit on Vessel

                //Find attached unit for chassis & check if its in the same facility
                //def attachedUnit = findAttachedUnit(chasEquipId,complex,unitFinder)
                def attchEquipClass = null;

                if(attachedUnit != null){
                    attchEquipClass = getEquipClass(attachedUnit)
                    def facilityId = attachedUnit.getFieldValue("unitActiveUfv.ufvFacility.fcyId")
                    if(facilityId != null && !facilityId.equals(ContextHelper.getThreadFacility().getFcyId())){
                        //fail( "Attached unit is not in the Current Facility");
                        println("Attached unit not in the Current Facility : Dismounted")
                        //A21  dismountChassis(attachedUnit)
                        attachedUnit = null;   attchEquipClass=null;
                    }
                }

                println("chasEquipId="+chasEquipId+"attachedUnit="+attachedUnit+"  attchEquipClass="+attchEquipClass)
                //To Handel New Chassis Equipment Created
                if(attachedUnit == null || (attachedUnit != null && 'CHASSIS'.equals(attchEquipClass))){ //Bare Chassis psition update
                    def activeBareChas = findActiveUnit(chasEquipId, complex, unitFinder)
                    println("activeBareChas="+activeBareChas)
                    if(activeBareChas == null && !position.startsWith('TR-') && !chasEquipId.startsWith('YC89')){
                        def equipment = Equipment.loadEquipment(chasEquipId);
                        try {
                            gvyApi.getGroovyClassInstance("DetachChassisFromUnitInComplex").detachChassisFromDepartedUnit(oldUnitAttached, gvyApi);
                        } catch (Exception e) {
                            //
                        }
                        UnitFacilityVisit chasUfv = getMgr().createYardBornUnit(facility, equipment, position, "Now posted New Chassis")
                        def chasUnit = chasUfv.ufvUnit
                        println("Now posted New Chassis="+chasEquipId)

                        chasUnit.setUnitLineOperator(LineOperator.findLineOperatorById('MAT'))
                        chasUnit.setUnitFreightKind(FreightKindEnum.MTY);
                        if(!position.startsWith('TR-')){
                            updatePosition(chasUnit, position, complex, unitFinder)
                        }
                    }

                    if(attachedUnit != null && activeBareChas != null && !position.startsWith('TR-') && 'CHASSIS'.equals(attchEquipClass)){
                        //2 - Update Chassis Position
                        updatePosition(attachedUnit, position, complex, unitFinder)
                    }

                }else if(note.contains('nowborn') && attchEquipClass.equals('CONTAINER')){ //Mount //3 - Now Born Message
                    mountChassis(attachedUnit.unitActiveUfv,chasEquipId,acryEquip)
                    // compare ufv position to position recieved from messages
                    def slot = attachedUnit.unitActiveUfv.ufvLastKnownPosition.posSlot
                    if(slot == null || !slot.equals(position) && !position.startsWith('TR-')){
                        updatePosition(attachedUnit, position, complex, unitFinder)
                    }
                    println("nowborn mounted Cntr="+attachedUnit.unitId)
                }//4 - Dismount
                else if(!note.contains('nowborn') && attchEquipClass.equals('CONTAINER')) { //Dismount
                    if(!position.startsWith('TR-') && attachedUnit != null ){
                        def dismountedUnit = null; //A21  dismountChassis(attachedUnit)//A4
                        if(dismountedUnit != null){
                            updatePosition(dismountedUnit, position, complex, unitFinder)
                            println("Dismounted Cntr for valid bare Chas Position ="+dismountedUnit.unitId)
                        }
                    }
                }else{
                    //Else Throw a failed maessage back
                    fail( "Bad Messages Type : Now Message Didnt Process");
                }

            }//Else If Ends

        }catch(Exception e){
            e.printStackTrace();
            if(e.getMessage() != null && !e.getMessage().contains("NO_EMAIL")){ //A18
                emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailTo, "NowToN4:"+equipPrimary+" Message Processing Error ", e.getMessage());
            }//If Ends
        }//Catch Ends
    }//Method process Ends

    public Equipment retireBareChassis(String equipId, Object complex, UnitFinder uf){
        def inEquipment = null;
        def activeUnit = null

        inEquipment = Equipment.loadEquipment(equipId);
        activeUnit = uf.findActiveUnit(complex, inEquipment)
        println('inEquipment='+inEquipment+'  activeUnit='+activeUnit)

        if(activeUnit == null){
            return;
        }

        //A12 - Accessory
        UnitEquipment acryUnitEquip = activeUnit.getAccessoryOnChs();
        Equipment acryEq = acryUnitEquip != null ? acryUnitEquip.getUeEquipment() : null
        def equipmentClass = getEquipClass(activeUnit)

        //IF Bare active Chassis
        if(equipmentClass.equals('CHASSIS')){
            activeUnit.makeRetired()
            println("After Retiring Chas="+equipId)
        }
        return acryEq;
    }


    public void updatePosition(Unit unit, String inYardSlot,Object complex, Object unitFinderObj){
        try{
            if(unit == null){
                fail( "NowToN4_GroovyError_02 Cannot Update Position as Unit value is Null");
            }
            def inUfv = null;
            def inFacility = ContextHelper.getThreadFacility()
            inUfv = unit.unitActiveUfv

            Yard inYard =  Yard.findYard("SI", inFacility)

            // Peter Seiler replace Position with LocPosition

            LocPosition pos = LocPosition.createYardPosition(inYard, inYardSlot, null, unit.getBasicLength(), true);
            inUfv.move(pos, null);

        }catch(Exception e){
            e.printStackTrace()
            fail( "NowToN4_GroovyError_03 Error Updating the Chassis Position ="+inYardSlot);
        }
    }


    public void mountChassis(Object activeUfv, String chasEquipId, Object acryEquip){
        def inEquipment = Equipment.loadEquipment(chasEquipId);
        def unit = activeUfv.getUfvUnit()
        unit.attachCarriage(inEquipment)
        //A12 Mount accessory to Chassis Marriage unit
        if(acryEquip != null){ unit.attachAccessoryOnChassis(acryEquip) }
    }

    public String getEquipClass(Object unit){
        def equiClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
        def equipmentClass = equiClass != null ? equiClass.getKey() : equiClass
        return equipmentClass
    }

    public Object findActiveUnit(String equipId, Object complex, UnitFinder uf){
        def inEquipment = Equipment.loadEquipment(equipId);
        def unit = uf.findActiveUnit(complex, inEquipment)
    }

    /* public  Object findAttachedUnit(String equipId,Object complex, UnitFinder uf){ //105 Issue

       def inEquipment = Equipment.loadEquipment(equipId);
       def attachedUnit = uf.findAttachedUnit(complex,inEquipment)  //MayBe use this findActiveUeUsingEqInAnyRole
       //Additional Level for Checking as under some instances findAttachedUnit is not returning the Attacehd unit
       if(attachedUnit == null){
           println("Second Level Check:"+equipId)
           UnitEquipment unitEq = uf.findActiveUeUsingEqInAnyRole(null, complex, inEquipment)
           attachedUnit = unitEq != null ? unitEq.ueUnit : null ;
       }
       println("GET ACTIVE UNIT ="+(attachedUnit != null ? attachedUnit.getFieldValue("UnitVisitState") : ''))
       if(attachedUnit != null && UnitVisitStateEnum.ACTIVE.equals(attachedUnit.getFieldValue("UnitVisitState"))){
           return attachedUnit
       }
       return null;
    }*/
    //A14
    public Object findActiveUeUsingEquipmentInAnyRole(String equipId,Object complex, UnitFinder uf){ //107 Issue

        def inEquipment = Equipment.loadEquipment(equipId);
        UnitEquipment unitEq = uf.findActiveUeUsingEqInAnyRole(null, complex, inEquipment)
        def attachedUnit = unitEq != null ? unitEq.ueUnit : null ;

        println("findActiveUeUsingEquipmentInAnyRole ="+attachedUnit+"  STATE"+(attachedUnit != null ? attachedUnit.getFieldValue("UnitVisitState") : ''))

        if(attachedUnit != null && UnitVisitStateEnum.ACTIVE.equals(attachedUnit.getFieldValue("UnitVisitState"))){
            return attachedUnit
        }
        return null;
    }


    //Strips out zero from single position Stalls(example C1409 to C149)
    //NSS to PKZ heap
    public String manipulatePosition(String position){
        def pos = position
        if(position != null && position.length() >= 5 && position.charAt(3) == '0'){
            pos = position.substring(0,3)+position.substring(4)
        }else if(position != null && position.equals('NSS')){
            pos = 'MIA'
        }
        return pos
    }


    /*
    * 1. Method finds Chassis FullId or Pad's CheckDigit for lookup
    * 2. IF Not Chas Equipment found then Create a Chassis
    */
    public String findCreateFullIdOrPadCheckDigit(String inChsId) {
        String chsIdFull = null;
        Chassis chs = Chassis.findChassis(inChsId);
        if(chs != null && LifeCycleStateEnum.OBSOLETE.equals(chs.eqLifeCycleState)){ //A15
            emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail("1aktosdevteam@matson.com", "Chassis:"+chs.getEqIdFull()+" Is Obsolete", "ChasRfid:"+chs.getEqIdFull()+" Is Obsolete");
            chs = null;
        }

        if(chs != null){
            chassisEquipList = new ArrayList();
            chassisEquipList.add(chs);
            chsIdFull = chs.getEqIdFull();
            chs = null;
        }
        //println("inChsId="+inChsId+" chs="+chs)
        if (chs == null) {
            if (inChsId.length() <= 11) { //A15
                DomainQuery dq = QueryUtils.createDomainQuery("Chassis").addDqPredicate(PredicateFactory.like(ArgoRefField.EQ_ID_FULL, (new StringBuilder()).append(inChsId).append("_").toString())).addDqPredicate(PredicateFactory.ne(ArgoRefField.EQ_LIFE_CYCLE_STATE,LifeCycleStateEnum.OBSOLETE)) ;
                List eqs = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                //chassisEquipList = eqs;
                if(eqs != null && eqs.size() >= 1){
                    chassisEquipList = new ArrayList();
                    chassisEquipList.addAll(eqs)
                }
                if (eqs != null && eqs.size() >= 1) {
                    def temp = null;
                    for(aEq in eqs)
                    {
                        chs = (Chassis)aEq;
                        //chsIdFull = chs.getEqIdFull();
                        if(temp == null){
                            temp = chs.getEqIdFull()
                        }else if(temp != null && temp.toUpperCase().endsWith("X") && !temp.equals(chs.getEqIdFull())){
                            temp = chs.getEqIdFull()
                        }
                    }
                    chsIdFull = temp
                }else if ((eqs == null || eqs.size() == 0) && (chassisEquipList == null || chassisEquipList.size() == 0)){
                    String temp = "";
                    if(inChsId.length() == 11){
                        temp = "BAD CHASSIS TAG - CHASSIS TAGGED WITH CHECKDIGIT ="+inChsId+"  YARD_POSITION="+position;
                    }else{
                        temp = "CREATE CHASSIS CALL="+inChsId+"    YARD_POSITION="+position; //A101
                        //chsIdFull = createChassis(inChsId)
                    }

                    if(!temp.contains("YARD_POSITION=TR-")){ //A19
                        emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
                        emailSender.custSendEmail(chasErrorEmail, inChsId+": NEW CHASSIS READ ", temp); //A101
                    }
                    throw new Exception("NO_EMAIL");
                }
            }
        } else {
            chsIdFull = chs.getEqIdFull();
        }
        return chsIdFull;
    }


    /*
    * 1. IF New Id length is greater than ID recieved then return=NEWID
    * 2. IF newId Ends with a Checkdigit X and newId and Recieved Id have same lengths then do nothing
    * 3. IF Recived Id is less then newId then Skip Renumber
    * 4.
    */
    public String findorCreateChassis(String id)
    {
        String newId = findCreateFullIdOrPadCheckDigit(id);
        println("New = "+newId+" recieved="+id);
        try
        {
            if(newId == null){
                throw new Exception("Tos Warning for check digit lookup "+id+" system has no match");
            }

            if(!newId.equals(id)) {
                //println("Tos Warning for check digit lookup "+id+" system has "+newId);
                if(newId.length() > id.length()+1) {
                    newId = id;
                }
                else if( id.toUpperCase().endsWith("X") &&  id.length() == newId.length()) {
                    //println("Skipping renum for "+id+ " id found = "+newId);
                }
                else if(id.length() < newId.length()) {
                    //println("Skipping renum for "+id+ " id found = "+newId);
                } else {
                    //This code Might never be reached
                    String renumString = " Renum["+newId+"|"+id+"]";
                    def gvyRenum = getGroovyClassInstance("GvyRenumberUnit")
                    gvyRenum.renumber(renumString)
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            throw e;
        }
        return newId;
    }

    public String createChassis(String chasEquipId){
        if(chasEquipId == null) return null;
        //Chassis Being Created with space
        chasEquipId = chasEquipId.replace(' ','')
        def equipId = chasEquipId.length() < 11 ? chasEquipId+'X' : chasEquipId
        Chassis newChas = Chassis.createChassis(equipId, 'C40', DataSourceEnum.SNX)
        ScopedBizUnit bizOwner = ScopedBizUnit.findEquipmentOwner('MATU')
        ScopedBizUnit bizOperator = ScopedBizUnit.findEquipmentOperator('MAT')
        newChas.setFieldValue(ArgoBizMetafield.EQUIPMENT_OWNER, bizOwner.bzuGkey)
        newChas.setFieldValue(ArgoBizMetafield.EQUIPMENT_OPERATOR, bizOperator.bzuGkey)
        String chasId = newChas.getEqIdFull();
        def cntrNbr = equipSecondary != null ? equipPrimary : 'N/A'
        emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
        emailSender.custSendEmail(emailTo, chasId+" New Chassis Equipment Created in N4.", +chasId+" Chassis has been Created."+"\nContainer: "+cntrNbr+"\nStall: "+position);

        return  chasId
    }

    private UnitManager getMgr()
    {
        return (UnitManager)Roastery.getBean("unitManager");
    }

    private Map getActiveChassis(Object chassisEquipList,String equipPrimary, Object complex, Object unitFinder){
        def attachedUnit = null;
        def chasEquipId = '';
        def chasAttachedUnit = null; //A7

        if(chassisEquipList == null || chassisEquipList.size() == 0){
            return null;
        }
        try
        {
            //Dismount case: Check if chassis is attached to another equipment
            for(aEquip in chassisEquipList){
                Chassis chs = (Chassis)aEquip
                def tempChs = chs.getEqIdFull()
                //A14 - attachedUnit = findAttachedUnit(tempChs,complex,unitFinder) //101
                attachedUnit = findActiveUeUsingEquipmentInAnyRole(tempChs,complex,unitFinder)
                //println('aEquip in chassisEquipList='+tempChs+"  attachedUnit="+attachedUnit)
                if(attachedUnit != null && 'CONTAINER'.equals(attachedUnit.unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass.key)
                        && equipPrimary.equals(attachedUnit.unitId)){
                    chasEquipId = tempChs
                    chasAttachedUnit = attachedUnit
                    break;
                }else if(tempChs.endsWith("X") && !tempChs.equals(chs.getEqIdFull())){
                    chasEquipId = tempChs
                    chasAttachedUnit = attachedUnit
                }else if(tempChs.length() == chasEquipId.length()+1){
                    chasEquipId = tempChs
                    chasAttachedUnit = attachedUnit
                }else{
                    chasEquipId = chasEquipId.length() == 0 ? tempChs : chasEquipId
                    chasAttachedUnit = chasAttachedUnit == null ? attachedUnit : chasAttachedUnit
                }
            }//For Ends
        }catch(Exception e){
            e.printStackTrace();
            throw e;
        }
        println("chasEquipId="+chasEquipId+"  attachedUnit="+chasAttachedUnit)
        Map map = new HashMap();
        map.put(chasEquipId,chasAttachedUnit);
        return map;
    }

    public void autoUnitLoad(String position, Object unitfacilityvisit, String equipSecondary){

        def posObj = unitfacilityvisit.getFinalPlannedPosition();

        Boolean hasChassis = equipSecondary != null ? Boolean.valueOf(true) : Boolean.valueOf(false);

        if(posObj == null){ return; } // No Planned Position for Vessel

        def slot = posObj.getPosSlot()
        def loc = posObj.getPosLocId()
        def strPlannedPos = posObj != null ? ""+posObj : ''
        if(strPlannedPos.startsWith('V-ALE')){
            ILocationLoader cl = (ILocationLoader)Roastery.getBean("carrierLoader");
            ILocation carrierVisit = cl.loadCarrierByGkey(posObj.getPosLocType(), posObj.getPosLocGkey());
            if(carrierVisit != null){
                getMgr().loadUnitToOutboundVisit(unitfacilityvisit, carrierVisit, null, slot, null, hasChassis);
            }
        }
    }

    public Object dismountChassis(Object unit,Equipment eq, UnitFinder uf){
        def carriage = unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull")
        if(carriage == null){ return null }
        def unitPosition = unit.unitActiveUfv.ufvLastKnownPosition.posSlot
        def dismountedUnit = null;
        try {
            gvyApi.getGroovyClassInstance("DetachChassisFromUnitInComplex").detachChassisFromDepartedUnit(unit, gvyApi);
        } catch (Exception e) {
            //
        }
        if(!LocTypeEnum.YARD.equals(unit.unitActiveUfv.ufvLastKnownPosition.posLocType) || !UnitVisitStateEnum.ACTIVE.equals(unit.unitActiveUfv.ufvVisitState)){ //A8

            unit.swipeChsByOwnersChs()
            /*  unitPosition = "PKZ" // Default value for position swipe updates
         def equipment = Equipment.loadEquipment(carriage);
         UnitFacilityVisit chasUfv = getMgr().createYardBornUnit(facility, equipment, unitPosition, "Now posted New Chassis")
         def chasUnit = chasUfv.ufvUnit
         println("swipeChsByOwnersChs and Create New Chassis="+chasEquipId)
         chasUnit.setUnitLineOperator(LineOperator.findLineOperatorById('MAT'))
         chasUnit.setUnitFreightKind(FreightKindEnum.MTY);
         updatePosition(chasUnit, unitPosition, complex, unitFinder) //Position Update */
            //emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
            //emailSender.custSendEmail('1aktosdevteam@matson.com',"ChassisRfid : Swiped Chassis"+carriage+" From CNTR "+unit.unitId,"ChassisRfid : Swiped Chassis"+carriage+" From CNTR "+unit.unitId);

        }else if(LocTypeEnum.YARD.equals(unit.unitActiveUfv.ufvLastKnownPosition.posLocType)){
            dismountedUnit = unit.dismount();
            updatePosition(dismountedUnit, unitPosition, complex, unitFinder) //Position Update
            //println("Dismounted chassis of Unit ="+unit.unitId);
        }
        // The flush is required to synchronize memory state of the unit with database state of the unit
        HibernateApi.getInstance().flush()
        return dismountedUnit
    }

} //Class Ends