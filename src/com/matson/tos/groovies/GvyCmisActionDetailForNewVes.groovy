/*
*  Change   Changer  Date       Desc
*  A1       GR       08/30/10   Commented out HazOpenCloseFlag
*  A2       GR       09/01/10   Added Additional Blank Fields for Acets (SN4Q change)
*  A3       GR       09/15/10   DUE Date Formatted
*  A4       GR       10/15/10   Added last Free Date value & formatting
*           RI       05/28/13   Hardcoaded values '-snx-' for doer and lastdoer
*/
import java.text.DateFormat;

public class GvyCmisActionDetailForNewVes {
    def gvyEventUtil = null;

    public String doIt(Object gvyTxtMsgFmt, Object event, Object gvyBaseClass, Object unit)
    {
        String eventType =  event.getEventTypeId()
        try
        {
            println("In Class GvyCmisActionDetail.doIt()")
            //ACTION
            def action = ''
            def actionAttr = gvyTxtMsgFmt.doIt('action',action)

            //Event Type

            //DATE & TIME
            def evtAppliedDt = event.getEvntAppliedDate()
            def zone =  unit.getUnitComplex().getTimeZone();
            gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def aDate  = gvyEventUtil.formatDate(evtAppliedDt,zone)
            def aTime = gvyEventUtil.formatTime(evtAppliedDt,zone)
            def aDateAttr = gvyTxtMsgFmt.doIt('aDate',aDate)
            def aTimeAttr = gvyTxtMsgFmt.doIt('aTime',aTime)

            //DOER
            def doer = "-snx-";
            /*def doer = event.getEvntAppliedBy()
            try
           {
              String[] doerArr = doer.split(":");
              if(doerArr != null)
             {
                   if(doerArr.length == 1){
          doer = doerArr[0] ;
                    }
                    else if(doerArr.length == 2){
                          doer = doerArr[1];
                     }
                    else if(doerArr.length == 3){
                        doer = doerArr[1];
                     }
                     else if(doerArr.length > 3 ){
                        doer = doerArr[2];
                      }
               }
              println("doer >>"+doer);
              if(eventType.equals('UNIT_IN_GATE') || eventType.equals('UNIT_DELIVER'))
              {
                 doer = getGateLaneIdDoer(doer,unit,gvyBaseClass,eventType)
              }

            }catch(Exception e){
               e.printStackTrace()
            }*/
            def doerAttr = gvyTxtMsgFmt.doIt('doer',doer)

            //SECTION CODE - Currently hardcoded
            def sectionCode = '%'
            def equiClass =unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : equiClass
            if(!equiClassKey.equals('CHASSIS')){
                sectionCode = 'Z'
            }
            def sectionCodeAttr = gvyTxtMsgFmt.doIt('sectionCode',sectionCode)

            //LAST ACTION
            def lastAction = ''
            def lastActionAttr = gvyTxtMsgFmt.doIt('lastAction',lastAction)

            //LAST ADATE
            def lastADate = aDate
            def lastADateAttr = gvyTxtMsgFmt.doIt('lastADate',lastADate)

            //LAST ATIME
            def lastATime = aTime
            def lastATimeAttr = gvyTxtMsgFmt.doIt('lastATime',lastATime)

            //LAST DOER
            def lastDoer = doer
            def lastDoerAttr = gvyTxtMsgFmt.doIt('lastDoer',lastDoer)

            //Cmis BLANK FIELDS
            def blankFieldAttr = blankFields(gvyTxtMsgFmt)

            //Gems Blank Fields
            def gemsBlankFldAttr = gemsAdditionalFields(unit,event,gvyTxtMsgFmt,gvyBaseClass)

            def  actionFieldAttr = actionAttr+aDateAttr+aTimeAttr+doerAttr+sectionCodeAttr+lastActionAttr+lastADateAttr+lastATimeAttr+lastDoerAttr+blankFieldAttr+gemsBlankFldAttr

            // println('actionFieldAttr : '+actionFieldAttr)

            return  actionFieldAttr

        }catch(Exception e){
            e.printStackTrace()
        }

    }

    //Appending Blank Fields For Cmis Processing  (Not Req in CMIS)
    public String blankFields(Object gvyTxtMsgFmt)
    {
        //HAZFLAG OPEN/CLOSE
        /*  def hazOpenCloseFlag=''
          def hazOpenCloseFlagAttr=gvyTxtMsgFmt.doIt('hazOpenCloseFlag',hazOpenCloseFlag)*/

        //AEI
        def aei = ''
        def aeiAttr = gvyTxtMsgFmt.doIt('aei',aei)

        //DSS
        def _dss = '%'
        def dssAttr = gvyTxtMsgFmt.doIt('dss',_dss)

        //ERF
        def erf = ''
        def erfAttr = gvyTxtMsgFmt.doIt('erf',erf)

        def blankFields = aeiAttr+dssAttr+erfAttr

        return blankFields
    }

    public String getGateLaneIdDoer(String evntDoer, Object unit, Object gvyBaseClass,String eventType)
    {
        def doer = evntDoer
        def laneId = ''

        def groupCode = unit.getFieldValue("unitRouting.rtgGroup.grpId");
        groupCode = groupCode != null ? groupCode : ''

        def ibCarrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvdGkey")
        def obCarrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvdGkey")
        def gvyGateObj = gvyBaseClass.getGroovyClassInstance("GvyCmisGateData");
        def ingateId = ibCarrierVisitGkey != null ? gvyGateObj.getGateId(ibCarrierVisitGkey) : ''
        def outgateId = obCarrierVisitGkey != null ? gvyGateObj.getGateId(obCarrierVisitGkey) : ''

        if(doer.equals('passpass')){
            doer = '8'+doer
        }
        else if(ingateId.equals('PIER2') && eventType.equals('UNIT_IN_GATE')){
            doer = '7'+doer
        }
        else if(outgateId.equals('PIER2') && eventType.equals('UNIT_DELIVER')){
            doer = '9'+doer
        }
        else if(ingateId.equals('WO GATE') && eventType.equals('UNIT_IN_GATE')){
            doer = '10'+doer
        }
        else if(outgateId.equals('WO GATE') && eventType.equals('UNIT_DELIVER')){
            doer = '11'+doer
        }
        else if(eventType.equals('UNIT_IN_GATE')){
            laneId = gvyGateObj.getEntryLaneId(ibCarrierVisitGkey)
            doer = laneId+doer
        }
        else if(eventType.equals('UNIT_DELIVER')){
            println("doer on unit deliver ::"+doer)
            laneId = gvyGateObj.getExitLaneId(obCarrierVisitGkey)
            doer = laneId+doer
        }
        return doer
    }

    //Added Additional Fields for Gems
    public String gemsAdditionalFields(Object unit,Object event,Object gvyTxtMsgFmt,Object gvyBaseClass)
    {
        def gemsFldAttr = '';
        try{
            def dtnAvailDt = 'null'
            def dtnDueDt = 'null'
            def lastFreeStgDt = 'null'
            def OldVesvoy = '%'
            def lineTime = '%'
            def tractorNbr = '%'
            def vNumber = '%'
            def chassAei = '%'
            def mgAei = '%'
            def chasdamageCode='%'
            def dtnAvailDtAttr=null
            def dtnDueDtAttr=null
            def lastFreeStgDtAttr=null;
            println("DATES BEFORE FIFTH "+dtnAvailDt+" - "+" - "+dtnDueDt+" - "+lastFreeStgDt);
            dtnAvailDt = unit.getFieldValue("unitActiveUfv.ufvFlexDate02")
            dtnAvailDt = gvyEventUtil.dateFormat(dtnAvailDt,'MM/dd/yyyy') //A3
            println("dtnAvailDt AFTER "+dtnAvailDt)
            if (dtnAvailDt!=null && !'null'.equals(dtnAvailDt)) {
                dtnAvailDtAttr =  gvyTxtMsgFmt.doIt('availDt',dtnAvailDt)
            }


            dtnDueDt = unit.getFieldValue("unitActiveUfv.ufvFlexDate03")
            dtnDueDt = gvyEventUtil.dateFormat(dtnDueDt,'MM/dd/yyyy') //A3
            println("dtnDueDt AFTER "+dtnDueDt)
            if (dtnDueDt!=null && !'null'.equals(dtnDueDt)) {
                dtnDueDtAttr =  gvyTxtMsgFmt.doIt('dtnDueDt',dtnDueDt)
            }

            def lastfreeDayStr = unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay");
            Date lastfreeDate = getlastFreeDate(dtnAvailDt, lastfreeDayStr, gvyBaseClass)
            lastFreeStgDt = lastfreeDate != null ? gvyEventUtil.dateFormat(lastfreeDate,'MM/dd/yyyy') : lastfreeDate
            println("lastFreeStgDt AFTER "+lastFreeStgDt)
            lastFreeStgDtAttr =  gvyTxtMsgFmt.doIt('lastFreeStgDt',lastFreeStgDt)
            // if (lastFreeStgDt!=null && !'null'.equals(lastFreeStgDt)) {
            // lastFreeStgDtAttr =  gvyTxtMsgFmt.doIt('lastFreeStgDt',lastFreeStgDt)
            // }


            // if (dtnAvailDt!=null && !'null'.equals(dtnAvailDt) && dtnDueDt!=null && !'null'.equals(dtnDueDt) && lastFreeStgDt!=null && !'null'.equals(lastFreeStgDt) ) {
            if (dtnAvailDt!=null && !'null'.equals(dtnAvailDt) && dtnDueDt!=null && !'null'.equals(dtnDueDt)) {
                println("IF OTHER ATTRIBUTES in ACTION DETAILS")
                def OldVesvoyAttr =  gvyTxtMsgFmt.doIt('oldVesvoy',OldVesvoy)
                def lineTimeAttr =  gvyTxtMsgFmt.doIt('lineTime',lineTime)
                def tractorNbrAttr =  gvyTxtMsgFmt.doIt('tractorNbr',tractorNbr)
                def vNumberAttr =  gvyTxtMsgFmt.doIt('vNumber',vNumber)
                def chassAeiAttr =  gvyTxtMsgFmt.doIt('chassAei',chassAei)
                def mgAeiAttr =  gvyTxtMsgFmt.doIt('mgAei',mgAei)
                def chsDmgCodeAttr =  gvyTxtMsgFmt.doIt('chasdamageCode',chasdamageCode)
                gemsFldAttr =  dtnAvailDtAttr+dtnDueDtAttr+lastFreeStgDtAttr+OldVesvoyAttr+lineTimeAttr+tractorNbrAttr+vNumberAttr+chassAeiAttr+mgAeiAttr+chsDmgCodeAttr
            } else {
                println("ELSE OTHER ATTRIBUTES in ACTION DETAILS")
            }


        }catch(Exception e){
            e.printStackTrace()
            println("Error in gemsFldAttr    "+e)
        }

        println("gemsFldAttr:::::::::::::::"+gemsFldAttr)
        return gemsFldAttr
    }


    public Date getlastFreeDate(Object availDate, String lastfreeDay, Object gvyBaseClass)
    {
        Date lastFreeDate = null;

        if(availDate == null){
            lastFreeDate = null
        }
        else if (lastfreeDay != null && lastfreeDay.indexOf("no") == -1)
        {
            def gvyUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            DateFormat formatter = new java.text.SimpleDateFormat("yyyy-MMM-dd");
            lastFreeDate = (Date)formatter.parse(lastfreeDay);
        }else if (lastfreeDay != null && lastfreeDay.indexOf("no") != -1) {
            lastFreeDate = null
        }
        return lastFreeDate
    }

}//Class Ends