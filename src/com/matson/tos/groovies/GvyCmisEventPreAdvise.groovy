import com.navis.argo.ContextHelper;

/*
* A1   KR    07/09/15  Alaska Ports
*/

public class GvyCmisEventPreAdvise
{
    def deptUnitDischPort = null

    public String processPreAdvise(String xmlData,Object event, Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        def unit = event.getEntity()
        def gvyEventObj = event.getEvent()
        String eventType =  gvyEventObj.getEventTypeId()
        println('EventType ::'+eventType)

        try
        {
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            if (eventType.equals("UNIT_PREADVISE"))
            {
                def bookingLineOperator = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoLine.bzuId");
                bookingLineOperator = bookingLineOperator != null ? bookingLineOperator : ''
                //SET Discharge port of Departed unit ELSE if no Departed unit then set to ?HON?
                //[comments] = ?YB EX ?+<port>
                def port = deptUnitDischPort != null ? deptUnitDischPort : ContextHelper.getThreadFacility().getFcyId();
                def comments = gvyCmisUtil.getFieldValues(xmlGvyString, "comments=")
                comments = comments.equals('null') ? '' : comments
                comments =  'YB EX '+port+' '+comments
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"comments=",comments)
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dss=","12")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dsc=","C")
            }
            else if (eventType.equals("CANCEL PREADVISE"))
            {
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dsc=","null")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dss=","null")

                //CANCEL_PREADVISE
                def aibcarrierOperatorId=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
                def truck = aibcarrierOperatorId != null ? aibcarrierOperatorId : ''
                if(truck.equals('GEN_TRUCK')){
                    truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
                }
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=",truck)
            }
            //Post Event
            def pol =  unit.unitRouting.rtgPOL;
            println("Preadvised for POL : "+pol);
            if (!"KQA".equalsIgnoreCase(pol)) {
                gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"EDT")
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }//Method Ends
}//Class PreAdvise Ends