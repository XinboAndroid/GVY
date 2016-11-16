import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.EquipmentState
import com.navis.services.business.event.*
import com.navis.framework.persistence.BaseFinder

import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.framework.util.DateUtil;
import com.navis.argo.business.atoms.EquipClassEnum;
import com.navis.inventory.business.units.Routing

import com.navis.security.business.user.BaseUser

public class TestGvyPlugin
{

    public boolean getCsrUserRole(String userId)
    {
        try
        {
            BaseUser baseUser = new BaseUser()
            baseUser = baseUser.findBaseUser(userId)
            //Set groupList = baseUser != null ? baseUser.getBuserGroupList() : null
            def groupArr = baseUser != null ? baseUser.getUserRoleNames() : null
            println('groupArr ::'+groupArr)
            for(aGroup in groupArr){
                println("User Group Sucessfully :"+aGroup)
                if(aGroup.equals('No Email')){
                    println("Fetched User Group Sucessfully :"+aGroup)
                    return true
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return false
    }






    public void postCmisFeed(Object metaId,Object event,Object api)
    {
        def unit = event.getEntity()
        def evntObj  =   event.getEvent()

        def gvyTxtMsgFmt = api.getGroovyClassInstance("GvyCmisTxtMsgFormatter");
        def actionDetail = api.getGroovyClassInstance("GvyCmisActionDetail");
        def actionDetailAttr =actionDetail.doIt(gvyTxtMsgFmt, evntObj, api ,unit);

        String eventType =  evntObj.getEventTypeId()
        def eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType)

        StringBuffer strBuff = new StringBuffer();
        def tempValue;

        for(aField in metaId)
        {
            tempValue = event.getProperty(aField)
            strBuff = strBuff.append(gvyTxtMsgFmt.doIt(aField,tempValue)+' ')
        }

        println("strBuff ::"+strBuff)
        def groovyXml = gvyTxtMsgFmt.createGroovyXml(eventTypeAttr+' '+strBuff+' '+actionDetailAttr)
        println("groovyXml ::"+groovyXml)

    }

    public void getDate(Object event ,Object api)
    {
        def unit = event.getEntity()
        java.util.Date date = new java.util.Date()
        def gvyEvntObj = api.getGroovyClassInstance("GvyEventUtil");
        def zone =  unit.getUnitComplex().getTimeZone();
        def fmtDate =  gvyEvntObj.formatDate(date, zone)
        def fmtTime = gvyEvntObj.formatTime(date,zone)
        println("fmtDate ::"+fmtDate+"     fmtTime::"+fmtTime)

    }

    public void getData()
    {
        ArrayList activeUnits = new ArrayList()
        try
        {
            BaseFinder baseFinder = new BaseFinder()
            List unitList = baseFinder.findAll("Unit")
            println("unit List ::-------------"+unitList.size())

            if(unitList != null) {
                Iterator iter = unitList.iterator();
                while(iter.hasNext()) {
                    def unit = iter.next();
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE) && unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass").equals(com.navis.argo.business.atoms.EquipClassEnum.CONTAINER)) {
                        activeUnits.add(unit)
                    }//IF
                } //While
            }//outer If
            println("activeUnits ::-------------"+activeUnits.size())
            //  List unitFacilityList = baseFinder.findAll("UnitFacilityVisit")
            //  println("unitFacilityList  ::-------------"+unitFacilityList.size())
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method ends


    public String getPreviousUnitHolds(String eventTypeId,Object unit,Object gvyBaseClass)
    {
        def prevUnitHolds = ""

        try
        {
            def eventId = eventTypeId.endsWith("_HOLD") || eventTypeId.endsWith("_RELEASE") ? eventTypeId.substring(0,eventTypeId.indexOf("_")) : ""
            def holdslist = ["BND","CUS","CC","HP","ON","INB","AG","XT"]

            def gvyCommentObj = gvyBaseClass.getGroovyClassInstance("GvyCmisCargoStatusUtil")
            def activeUnitHldList = gvyCommentObj.getUnitActiveHolds(unit)

            for(Id in holdslist){
                if(eventTypeId.endsWith("_HOLD") && eventId.equals(Id)){
                    prevUnitHolds = activeUnitHldList.replace(eventId,"")
                    println("After Pulling out the Hold : "+ prevUnitHolds)
                }else if(eventTypeId.endsWith("_RELEASE") && eventId.equals(Id)){
                    prevUnitHolds = activeUnitHldList+" "+eventId
                    println("Appending Hold : "+ prevUnitHolds)
                } else{
                    prevUnitHolds = activeUnitHldList
                    println("Passing the Original Hold Value : "+ prevUnitHolds)
                }
            }//For Ends
            println("eventId : "+eventId+"   activeUnitHldList::"+activeUnitHldList )

        }catch(Exception e){
            e.printStackTrace()
        }
        return prevUnitHolds
    }




}//Class Ends



