/*
*  SrNo     Date                 Changer      Desc
*  A1       04/27/09            GR              NullPointer check on Turntime attribute Div
*  A2  GR   08/15/2011  Pulling out SingletonService class call
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import com.navis.framework.util.DateUtil;


public class GvyCmisTruckDataProcessor {

    public String getLaneIdDoer(Object doer, Object gateId, Object entryLane, Object exitLane, String gateType) {
        if(gateId.equals("PASSPASS") || gateId.equals("passpass")) {
            return '8'+doer;
        } else if( gateId.equals('PIER2') && gateType.equals('OGATE') ) {
            return '9'+doer;
        } else if( gateId.equals('PIER2') && gateType.equals('IGATE') ) {
            return '7'+doer;
        } else if( gateId.equals('WO GATE') && gateType.equals('OGATE') ) {
            return '11'+doer;
        } else if( gateId.equals('WO GATE') && gateType.equals('IGATE') ) {
            return '10'+doer;
        } else if(gateType.equals('IGATE')  ) {
            if(entryLane != null) {
                return entryLane.laneId+doer;
            } else {
                return '1'+doer;
            }
        } else if(gateType.equals('OGATE') ) {
            if(exitLane != null) {
                return exitLane.laneId+doer;
            } else {
                return '4'+doer;
            }
        }

        return '';
    }

    public String getDoer(Object event) {
        def doer = event.getEvent().getEvntAppliedBy();
        if(doer == null) return '';

        String[] doerArr = doer.split(":");
        if(doerArr.length == 1){
            doer = doerArr[0] ;
        } else if(doerArr.length == 2 || doerArr.length == 3){
            doer = doerArr[1];
        } else if(doerArr.length > 3 ) {
            doer = doerArr[2];
        }
        return doer;
    }


    public Object getStat(Object tvd) {
        if(tvd == null) return null;
        if(tvd.tvdtlsStats != null) {
            def stats = tvd.tvdtlsStats.iterator();
            if(stats.hasNext()) return stats.next();
        }
        return null;
    }


    public String gateSequenceNo(Object currentStat, Object zone, Object gvyEventUtil)
    {
        def gateSeqNo = ''
        try
        {

            Date dateObj = null;
            if(currentStat != null)
            {
                dateObj = currentStat.tvstatStart;

                //Date Formatting
                def aDate  = gvyEventUtil.convertToJulianDate(dateObj)
                def aTime = gvyEventUtil.formatTime(dateObj,zone)
                def datefmt = aDate+aTime
                gateSeqNo = datefmt != null ? datefmt.replace(":","") : datefmt
                return gateSeqNo;



            }//If ends
        }//try Ends
        catch(Exception e){
            e.printStackTrace();
        }
        return gateSeqNo;

    }//method execute ends


    public String doIt(Object event, Object tran)
    {
        println("In Class GvyCmisDataProcessor.doForTruck()");
        def gvyBaseClass = '';
        def groovyXml  = '';

        try
        {

            //Calling Msg Formater class
            gvyBaseClass = new GroovyInjectionBase();
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");

            def zone = tran.tranComplex.getTimeZone();
            def stat = getStat(tran.tranTruckVisit);

            Event gvyEventObj = event.getEvent();
            String eventType =  gvyEventObj.getEventTypeId();
            println('EventType ::'+eventType);

            def eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType);
            gvyBaseClass.log("Truck tran type="+eventTypeAttr);

            def unitClassAttr = gvyTxtMsgFmt.doIt('unitClass','TRUCKVISIT');

            def gateSeqNoAttr = gvyTxtMsgFmt.doIt('gateSeqNo',gateSequenceNo(stat,zone,gvyEventUtil));

            def stage = "OUTGATE".equalsIgnoreCase(tran.tranStageId) ? "OGATE" : "IGATE";
            def actionAttr    = gvyTxtMsgFmt.doIt('action',stage);


            def aDate  = event.getEvent().getEvntAppliedDate();
            def aDateAttr = gvyTxtMsgFmt.doIt("aDate",gvyEventUtil.formatDate(aDate,zone));
            def aTimeAttr = gvyTxtMsgFmt.doIt("aTime",gvyEventUtil.formatTime(aDate,zone));

            //DOER
            def doer = getDoer(event);
            def lane = getLaneIdDoer(doer, tran.tranTruckVisit.tvdtlsGate.gateId, tran.tranTruckVisit.tvdtlsEntryLane, tran.tranTruckVisit.tvdtlsExitLane, stage)
            def doerAttr = gvyTxtMsgFmt.doIt('consigneeAddr3',doer);

            def turnTimeAttr = '';
            if(stat != null) {
                //A1
                double ttmin = stat.tvstatTurnTime != null ? stat.tvstatTurnTime/60000.0 : 0.0;
                long value = Math.round(ttmin);
                turnTimeAttr = gvyTxtMsgFmt.doIt('turnTime',value+"");
            }

            def transAttr = gvyTxtMsgFmt.doIt('trans','BobTail');
            def tractorAttr = gvyTxtMsgFmt.doIt('tractor',tran.tranTruckVisit.tvdtlsTruckId);
            def truckAttr = gvyTxtMsgFmt.doIt('truck',tran.tranTruckingCompany.bzuId);
            def truckerNameAttr = gvyTxtMsgFmt.doIt('truckerName',tran.tranTruckingCompany.bzuName);
            def driversLicenseAttr = gvyTxtMsgFmt.doIt('driversLicense',  tran.tranTruckVisit.tvdtlsOutBatNbr);

            def msgString =  eventTypeAttr + unitClassAttr + gateSeqNoAttr + actionAttr + aDateAttr + aTimeAttr + doerAttr + turnTimeAttr + transAttr + tractorAttr + truckAttr + truckerNameAttr + driversLicenseAttr

            groovyXml = gvyTxtMsgFmt.createGroovyXml(msgString)

        }catch(Exception e){
            e.printStackTrace()
            //def gvyExceptionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisExceptionProcess");
            //gvyExceptionObj.processException(e)
        }

        return groovyXml;
    }


    public boolean isOutgate(Object tran) {
        if("OUTGATE".equalsIgnoreCase(tran.tranStageId) ) return true;
        return false;
    }

}//Class Ends