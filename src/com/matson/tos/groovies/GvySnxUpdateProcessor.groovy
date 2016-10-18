/*
* SrNo  Doer Date      Change
* A1    GR   07/08/10  Optimized UNIT_SNX_UPDATE  event
                       Added Consignee DAS message Change
* A2    GR   10/20/10  Post NewVes  EDT and BDC Directly to Gems
* A3    GR   10/22/10  Merging NV,NLT,SUP Processing into method procBatchExecution
                       Remove item8 from Unit_Snx_Update
* A4    GR   12/10/10  Switched Order On BDC and EDT
* A5    GR   02/02/11  POST to SAF queue for Gems
* A6    GR   02/16/11  Flip LinOpt for MAESRK Containers
* A7    GR   04/08/11  PDU for WO transfer units direct to gems
* A8    GR   04/13/11  Roll Booking  commodity over to Unit notes
* A9    GR   05/25/11  TT#12506 Set EqOperator and BLnr space check
* A10   GR   10/06/11  TT# Added EDT and BDC to supplimental
* A11   GR   10/27/11  TOS2.1 : Update  code to set EqOwner in Snx NLT process
* A11   GR   10/27/11  TOS2.1 : Added Equipment
* A12   GR   11/14/11  Supp Check to only post EDT&BDC is unit-ufv is not departed
* A13   GR   11/14/11  Issue Updating Equipment Operator
* A14   GR   12/13/11  Update HOLD FOR LNK
* A15   GR   12/16/11  TOS2.1 : Update EqOpreator and Line Operator Multiple lines
* A16   GR   12/16/11  Added Owners to  General Reference
* A17   GR   01/04/11  Refresh Unit on Vessel
* A18   GR   01/11/12  YB cargoStatus  notificatio issue on Supplemental
* A19   GR   01/11/12  Suppress Add/Release hold on departed Supplemental  unit TT#15076
* A20   GR   03/08/12  Lookup currentFacility ufvState for Departed unit.
* A21   GR   04/12/12  NullPOinter Check on ufv.
        GB   05/21/12  NewVes Refresh on Last Unit.
  A22   LC   03/11/13  Add procCmisDataRefresh for NLT process
  A23   LC   06/13/13  Add Client NewVes CmisDataRefresh
  A24   KM   06/12/14  Release hold on departed unit TT# EP000205569
*/
import com.navis.inventory.business.units.Routing;
import  com.navis.services.business.event.GroovyEvent
import com.navis.framework.business.Roastery
import  com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.api.IFlagType
import  com.navis.argo.business.atoms.FlagPurposeEnum
import com.navis.argo.business.reference.*
import  com.navis.argo.business.atoms.LogicalEntityEnum;
import com.navis.argo.business.api.LogicalEntity;
import  com.navis.argo.business.api.Serviceable;
import com.navis.argo.business.reference.LineOperator;
import  com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.argo.business.api.IBizUnitManager;
import  com.navis.inventory.business.units.UnitEquipment;
import com.navis.argo.business.atoms.DataSourceEnum;
import  com.navis.argo.business.reference.Equipment;
import com.navis.argo.business.atoms.BizRoleEnum;
import  com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import  com.navis.argo.business.model.Operator;
import java.util.Calendar
import com.navis.services.business.event.Event;
import  com.navis.services.business.rules.EventType;
import com.navis.argo.business.atoms.EventEnum;
import  com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.framework.persistence.HibernateApi;
import  com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import  com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import  com.navis.argo.business.reference.Group

import com.navis.services.business.api.EventManager;
import com.navis.services.business.rules.EventType;
import com.navis.framework.portal.FieldChanges;
import com.navis.argo.business.reference.RoutingPoint;

import com.navis.inventory.business.units.Unit;
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.InventoryEntity;

public class GvySnxUpdateProcessor
{
    def  servicesMgr = (ServicesManager)Roastery.getBean("servicesManager");

    public void  procBatchExecution(Object api,Object event,Object unit,Object dtnFlag){
        def note = event.event.evntNote;
        if(note.contains("Supplemental")){
            procSupplemental(api,event,unit,dtnFlag)
        }else if(note.contains("NewVes")){
            procNewVes(api,event,unit,dtnFlag)
        }else if(note.contains("NIS Load Transaction")){
            procNLT(api,event,unit)
        }
    }

    private void procCmisDataRefresh(Object api, Object event, Object unit) {
        def note = event.event.evntNote;
        try{
            def doer = event.event.evntAppliedBy;
            doer = doer.replace('user:','');
            com.navis.argo.ContextHelper.setThreadExternalUser(doer);
            def sendEvent = new GroovyEvent(null,unit);
            sendEvent.postNewEvent("CMIS_DATA_REFRESH", note);
            api.logWarn("GvySnxUpdateProcessor.procCmisDataRefresh() completed");
        }catch(Exception  e){
            api.log("Exception in GvySnxUpdateProcessor.procCmisDataRefresh() " + e);
        }
    }

    public void procSupplemental(Object api,Object event,Object unit,Object dtnFlag){
        try{
            def gvyEventUtil = api.getGroovyClassInstance("GvyEventUtil")
            def  gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")

            def updtdischPort =  gvyEventUtil.wasFieldChanged(event,'rtgPOD1')
            def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            //5.1 Reroute Supplemental units to Barge
            if(updtdischPort){
                def gvyRerouteObj =  api.getGroovyClassInstance("GvyCmisEventUnitReroute")
                gvyRerouteObj.setOBCarrierOnPODChng(unit,  gvyEventUtil,gvyCmisUtil,event)
            }
            //5.2 - 06/16/2010  DAS Messages Consignee Updt and  On AvailDate Proc Flag
            def consigneeChng = gvyEventUtil.wasFieldChanged(event, 'gdsConsigneeAsString')
            def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
            def unitDtl =  unitDetails.doIt(event)
            if(dtnFlag || consigneeChng){
                def gvyDentObj = api.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                gvyDentObj.detentionProcess(unitDtl,event,api)
            }

            //A10 Starts - Blanket EDT [Remark,Shipper,consignee,freightkind]
            //A11 - IF unit is departed
            def fcy = com.navis.argo.ContextHelper.getThreadFacility();
            def ufv = unit.getUfvForFacilityCompletedOnly(fcy); //If Departed get facility ufv
            def visitState  = ufv != null ? ufv.getFieldValue("ufvVisitState") : null; //A21
            if(visitState ==  null){
                visitState = unit.getFieldValue("unitActiveUfv.ufvVisitState");
            }
            boolean isDeparted = visitState == null || UnitVisitStateEnum.DEPARTED.equals(visitState)  ? true : false
            if(!isDeparted){
                def xml = gvyCmisUtil.eventSpecificFieldValue(unitDtl,"dischargePort=",dischPort);
                gvyCmisUtil.postMsgForAction(xml,api,"EDT")


                def blNbrChng = gvyEventUtil.wasFieldChanged(event,  'gdsBlNbr')
                def destinationChng = gvyEventUtil.wasFieldChanged(event, 'gdsDestination')
                def polChng = gvyEventUtil.wasFieldChanged(event, 'rtgPOL')

                if(blNbrChng || destinationChng  || updtdischPort || polChng ){
                    gvyCmisUtil.postMsgForAction(xml,api,"BDC")
                }
            }
            //A10 - Ends

            //5.3. Cargo Status Report
            def prevGroup  = gvyEventUtil.getPreviousPropertyAsString(event,"rtgGroup"); //event.getPreviousPropertyAsString("RoutingGroup");  // A19
            if(prevGroup != null){ prevGroup = lookupGroupId(prevGroup)}
            def group=  unit.getFieldValue("unitRouting.rtgGroup.grpId");
            def isNisPort = gvyCmisUtil.isNISPort(dischPort)
            if('YB'.equals(prevGroup) && group == null && isDeparted && isNisPort){
                println("Dont Post CRS for YB")
            }else{
                def cargoStatusGvy = api.getGroovyClassInstance(  "GvyUnitCargoStatus");
                def ret = cargoStatusGvy.sendXml( "CARGO_STATUS",  event);
                api.sendXml( ret);
            }

            // Invoke SIT_UNASSSIGN if notes contain SIT_DEL
            // This is fix supplemental SIT remove issue, after invoking the SIT_UNASSIGN removing SIT_DEL from unit notes
            def unitRemark = unit.getFieldValue("unitRemark");
            def commodity =  unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");

            commodity = commodity!=null ? commodity :"NOSIT";
            println("<<<<<<<<<<<<<unitRemark>>>>>>>>>>>>>>>"+unitRemark);
            println("<<<<<<<<<<<<<commodity>>>>>>>>>>>>>>>"+commodity);

            Routing routing = unit.getUnitRouting();
            String rtgGroup = routing.getRtgGroup();
            String drayStatus = unit.getFieldValue("unitDrayStatus");

            rtgGroup = rtgGroup!=null ? rtgGroup :"NOWO";

            println("<<<<<<<<<<<<<rtgGroup 1 >>>>>>>>>>>>>>>"+rtgGroup);
            //println("<<<<<<<<<<<<<drayStatusKey>>>>>>>>>>>>>>>"+drayStatusKey);

            if(unitRemark!=null && unitRemark.contains("~1") && commodity=="SIT") {
                unitRemark = unitRemark.replace("~1","");
                unitRemark = unitRemark.trim();
                unit.setFieldValue("unitRemark", unitRemark);
                println("Recording SIT_UNASSIGN");
                def sendEvent = new GroovyEvent(null,unit);
                def note = event.event.evntNote;
                sendEvent.postNewEvent("SIT_UNASSIGN", note);
            }

            println("<<<<<<<<<<<<<rtgGroup 2>>>>>>>>>>>>>>>"+rtgGroup);
            println("<<<<<<<<<<<<<drayStatus>>>>>>>>>>>>>>"+drayStatus);

            if(unitRemark!=null && unitRemark.contains("~2") && drayStatus!=null && drayStatus.contains("OFFSITE")) {
                println("<<<<<<<<<<<<<Inside WO_Update>>>>>>>>>>>>>>>"+drayStatus);
                try{
                    unitRemark = unitRemark.replace("~2","");
                    unitRemark = unitRemark.trim();
                    unit.setFieldValue("unitRemark", unitRemark);
                    println("Recording TRANSFER_CANCEL");
                    def sendEvent = new GroovyEvent(null,unit);
                    def note = event.event.evntNote;
                    sendEvent.postNewEvent("TRANSFER_CANCEL", note);
                }catch(Exception e){
                    println("Exception in WO rtgGroup changes " + e);
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //A2,A4,A7
    public void procNewVes(Object api,Object event,Object unit,Object dtnFlag){
        try{

            def category = unit.getFieldValue("unitCategory");

            println("<<<<<<<<<<<<<<<unitCategory-procNewVes>>>>>>>>>>>>>>>"+category);

            def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
            def unitDtl = unitDetails.doIt(event)
            def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
            println("::::procNewVes:::::")
            //Post Directly to Gems(N4Topic)Bypass TDP
            //def jmsTopicSender = api.getGroovyClassInstance("JMSTopicSender")
            def jmsQueueSender = api.getGroovyClassInstance("JMSQueueSender") //A5
            def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId");

            def comm = gvyCmisUtil.getFieldValues(unitDtl,"commodity=");
            println("Commodity is :::"+comm);
            //PDU for WO transfer units
            if('XFER-WO'.equals(groupCode)){
                def xmlpdu = gvyCmisUtil.eventSpecificFieldValue(unitDtl,"action=","PDU")
                xmlpdu = gvyCmisUtil.eventSpecificFieldValue(xmlpdu,"lastAction=","PDU")
                //A23
                if (xmlpdu!=null && (!(xmlpdu.contains("DO NOT EDIT-NEWVES") || xmlpdu.contains("ZZZZ")) ))
                {
                    println("SENDING PDU during SNX IMPORT of NEWVES")
                    jmsQueueSender.send(xmlpdu);
                } else {
                    println(" PDU NOT POSTED DURING NEWVES IMPORT BECAUSE OF BOOKING HAVING DO NOT EDIT STRING")
                }
                println("PDU DIRECT TO GEMS="+xmlpdu)
                // println("FOR TDP-NEWVES PROCESS: STRIPPED DO NOT EDIT & ZZZZ ::: PDU DIRECT TO GEMS="+xmlpdu)
            }

            //BDC
            def xmlBdc = gvyCmisUtil.eventSpecificFieldValue(unitDtl,"action=","BDC")
            xmlBdc = gvyCmisUtil.eventSpecificFieldValue(xmlBdc,"lastAction=","BDC")
            //A23
            if (xmlBdc!=null && (!(xmlBdc.contains("DO NOT EDIT-NEWVES") || xmlBdc.contains("ZZZZ"))) )
            {
                println("SENDING BDC during SNX IMPORT of NEWVES")
                jmsQueueSender.send(xmlBdc);
            } else {
                println(" BDC NOT POSTED DURING NEWVES IMPORT BECAUSE OF BOOKING HAVING DO NOT EDIT STRING")
            }

            println("BDC DIRECT TO GEMS="+xmlBdc)
            // println("FOR TDP-NEWVES PROCESS: STRIPPED DO NOT EDIT & ZZZZ ::: BDC DIRECT TO GEMS="+xmlBdc)

            //EDT
            def xmlEdt = gvyCmisUtil.eventSpecificFieldValue(unitDtl,"action=","EDT")
            xmlEdt = gvyCmisUtil.eventSpecificFieldValue(xmlEdt,"lastAction=","EDT")
            //A23
            if (xmlEdt!=null && (!(xmlEdt.contains("DO NOT EDIT-NEWVES") || xmlEdt.contains("ZZZZ"))) )
            {
                println("SENDING EDT during SNX IMPORT of NEWVES")
                jmsQueueSender.send(xmlEdt);
            } else {
                println(" EDT NOT POSTED DURING NEWVES IMPORT BECAUSE OF BOOKING HAVING DO NOT EDIT STRING")
            }

            println("EDT DIRECT TO GEMS="+xmlEdt)
            //println("FOR TDP-NEWVES PROCESS: STRIPPED DO NOT EDIT & ZZZZ ::: EDT DIRECT TO GEMS="+xmlEdt)

            println("Setting editFlag to empty... "+unit.unitId)
            unit.setUnitFlexString11("");


        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void  procNLT(Object api,Object event,Object unit){
        try{
            def gvyEquiObj = api.getGroovyClassInstance("GvyCmisEquiDetail");
            //Flip Mty Cli Cntr from MAT to Cli Operator(11/23/2009)
            gvyEquiObj.flipMtyCliCntrOperator(unit)
            //05/17/2010 - Post ALE bare chassis to NOW
            if('CHASSIS'.equals(unit.unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass.key)){
                def gvyNow = api.getGroovyClassInstance("NowChassisTrackingBuilder")
                gvyNow.nowMessagesProcessor(event,  api)
            }

            //A8-04/13/11 setting BKGNBR  to BL_NBR  and BKG Notes(commodity) to UnitNotes
            def expGateBkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")
            def bkgNotes = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoNotes")
            def commodity = bkgNotes != null ? bkgNotes.substring(0,bkgNotes.indexOf(":")) : ""
            commodity = commodity.trim().length() > 0 ? commodity : null
            if(expGateBkgNbr != null){
                if(commodity != null){ unit.setFieldValue("unitRemark",commodity) }
            }

            //A6 - Flip Maersk Container unit line operator on the NLT execution
            def freightkind=unit.getFieldValue("unitFreightKind")
            def equiOwner =unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId")
            def blNbr = unit.getFieldValue("unitGoods.gdsBlNbr")
            if(FreightKindEnum.MTY.equals(freightkind)  && ownersToFlipLine(equiOwner,api) && (blNbr == null || blNbr.trim().length()== 0)){
                setUnitAndEqOperator(unit, equiOwner)
            }//A15
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //Method Get Active Holds for Unit
    public  String releaseHoldsPermissions(Object  unit, String notes)
    {
        if(notes == null){ return  }
        //A19 - Do not Add/Release Hold for Departed unit
        com.navis.argo.ContextHelper.setThreadExternalUser("-jms-");
        def fcy = com.navis.argo.ContextHelper.getThreadFacility();
        def ufv = unit.getUfvForFacilityCompletedOnly(fcy);  //If Departed get facility ufv
        def ufvVisit = ufv != null ? ufv.getFieldValue("ufvVisitState")  : null ;
        if(ufvVisit == null){
            ufvVisit = unit.getFieldValue("unitActiveUfv.ufvVisitState");
        }
        /* boolean isDeparted = UnitVisitStateEnum.DEPARTED.equals(ufvVisit) ? true : false  //A24 km**
          if(notes.contains("Supplemental") && isDeparted){
             return;
          } */
        try
        {
            def snxflags = notes.indexOf('(') != -1 && notes.indexOf(')')  != -1 ? notes.substring(notes.indexOf('(')+1,notes.indexOf(')')) : ''
            def holdFlags = unit.getFieldValue("unitAppliedHoldOrPermName")
            def flagIds = holdFlags != null ? holdFlags.split(",") : ''
            for(holdId in flagIds)
            {
                def  iFlageType = servicesMgr.getFlagTypeById(holdId)
                def logicalEntity  =   iFlageType.getAppliesTo()
                def flagPurpose =  iFlageType.getPurpose().getKey()
                if(flagPurpose.equals('HOLD') && !snxflags.contains(holdId))
                {
                    //Releasing Equip Holds
                    if(logicalEntity.equals(LogicalEntityEnum.EQ) ||  logicalEntity.equals(LogicalEntityEnum.CTR)){
                        //Commented on 05/15/09 as Equi Holds Should be Released
                        /* def equipmentId =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqIdFull")
                         def equipObj   =  Equipment.loadEquipment(equipmentId);
                         def operator = com.navis.argo.business.model.Operator.findOperator("MATSON");
                          def equipmentState =    com.navis.inventory.business.units.EquipmentState.findEquipmentState(equipObj,operator);
                          releaseHold(equipmentState,holdId) */
                    }else if(logicalEntity.equals(LogicalEntityEnum.UNIT)  && filterHoldToRel(holdId)){
                        releaseHold(unit,holdId);
                    }
                }
            }//for ends
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method Ends

    //FOR NLT Process Set the Unit and Equipment Line Operator
    public void setUnitAndEqOperator(Object unit, String owner){
        try{
            def line = owner.substring(0,owner.length()-1);
            def  operator = LineOperator.findLineOperatorById(line);
            unit.setUnitLineOperator(operator); //Update  unit Operator
            def eqOperator = Operator.findOperator("MATSON")
            Equipment  equipment = unit.unitPrimaryUe.ueEquipment
            def state =  com.navis.inventory.business.units.EquipmentState.findOrCreateEquipmentState(equipment,eqOperator);
            def  bzu = ScopedBizUnit.findScopedBizUnit( line, BizRoleEnum.LINEOP);
            state.upgradeEqOperator(  equipment, bzu, DataSourceEnum.USER_DBA);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public String releaseHold(Object unit,String holdId) {
        try
        {
            com.navis.argo.ContextHelper.setThreadExternalUser("-jms-");
            servicesMgr.applyPermission(holdId,  unit, null, "Hold Released by NV/NLT/SUPP", true)
        }catch(Exception e){
            e.printStackTrace()
        }

    }

    public boolean filterHoldToRel(String holdId){
        try{
            String arrHolds = ["TD","TI","TS","ULK","XT","OUTGATE","HOLD  FOR LNK","ST","RM","CAR","GX"]
            if(!arrHolds.contains(holdId)){
                return  true;
            }
            return false
        }catch(Exception e){
            e.printStackTrace()
        }

    }

    public boolean ownersToFlipLine(String  owners, Object api){
        try{
            String ownerNlt = api.getReferenceValue("NLT_OWNER",  "NLT_OWNER", null, null, 1)  //A16
            if(ownerNlt.contains(owners)){
                return true;
            }
            return false
        }catch(Exception e){
            e.printStackTrace()
        }
    }

// A17 - Refresh Newves and NLT units
    public  void refreshUnitsOnVessel(Object unit)
    {
        try{
            Date tagTime = new Date(com.navis.argo.business.api.  ArgoUtils.timeNowMillis() + 1);
            def gvyBase = new GroovyInjectionBase();
            def cv = unit.unitActiveUfv.ufvActualIbCv;
            def unitFinder = gvyBase.getUnitFinder();
            def list = unitFinder.findAllUnitsByDeclaredIbCarrier(cv);
            def iter = list.iterator();
            while(iter.hasNext()) {
                def aUnit = iter.next();
                com.navis.inventory.business.units.ImpedimentsBean impedimentsBean = aUnit.calculateImpediments(true);
                if (impedimentsBean != null) {
                    aUnit.updateImpediments(impedimentsBean, tagTime);
                    HibernateApi.getInstance().saveOrUpdate(aUnit);
                    println("Refreshed NV/NLT unit ="+aUnit.unitId)
                }//if ends
            }//while ends
        }catch(Exception e){
            e.printStackTrace();
        }//try  ends

    }//Method Ends

    // A17 - Refresh Supplemental units
    public void refreshSuppUnits()
    {
        try{
            Date tagTime = new Date(com.navis.argo.business.api. ArgoUtils.timeNowMillis() + 1);
            def gvyBase = new GroovyInjectionBase();
            Calendar calendarHst = Calendar.getInstance();
            def endDateTimeHst = calendarHst.getTime()

            calendarHst.add(Calendar.MINUTE, -30);
            def startDateTimeHst = calendarHst.getTime()

            EventType evntSnx = EventType.resolveIEventType(EventEnum.UNIT_SNX_UPDATE);
            EventType[]  evntTypeArr = [evntSnx]

            def gvyRptEventUtil  = gvyBase.getGroovyClassInstance("GvyReportEventUtil")
            List list = gvyRptEventUtil.getEventsByCreatedDate(startDateTimeHst,endDateTimeHst,evntTypeArr)
            def iter = list.iterator();
            while(iter.hasNext()) {
                def aEvent = iter.next();
                def inCtrId = aEvent.getEventAppliedToNaturalKey();
                def ufv = gvyBase.findActiveUfv(inCtrId)
                def aUnit = ufv.getUfvUnit();
                com.navis.inventory.business.units.ImpedimentsBean impedimentsBean  = aUnit.calculateImpediments(true);
                if (impedimentsBean != null) {
                    aUnit.updateImpediments(impedimentsBean,  tagTime);
                    HibernateApi.getInstance().saveOrUpdate(aUnit);
                }//if ends
                println("Refreshed Supp unit ="+aUnit.unitId)
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }

    }//Method Ends

    public String lookupGroupId(String Gkey)  {
        DomainQuery dq = QueryUtils.createDomainQuery("Group").addDqPredicate(PredicateFactory.eq(com.navis.argo.ArgoRefField.GRP_GKEY,  Gkey));
        Group grp = (Group)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
        if(grp == null)  {
            return "";
        }
        return grp.grpId;
    }

}