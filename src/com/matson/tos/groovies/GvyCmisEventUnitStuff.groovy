import com.navis.argo.ContextHelper

public class GvyCmisEventUnitStuff
{
    public String processUnitStuff(String xmlGvyData, String comodtiy, Object gvyCmisUtil)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            if(comodtiy.equals('AUTO')){
                xmlGvyString = setCommodityAutoFields(xmlGvyString,gvyCmisUtil)
            }
            else{
                xmlGvyString = setCommodityNonAutoFields(xmlGvyString,gvyCmisUtil)
            }
            xmlGvyString = setUnitStuffFields(xmlGvyString,gvyCmisUtil)
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    public String setCommodityAutoFields(String xmlGvyData, Object gvyCmisUtil)
    {
        def xmlGvyString = xmlGvyData
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazF=","Y")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"cargoNotes=","AUTOMOBILE")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"shipper=","AUTO")

        return xmlGvyString
    }

    public String setCommodityNonAutoFields(String xmlGvyData, Object gvyCmisUtil)
    {
        def xmlGvyString = xmlGvyData
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"commodity=","null")

        return xmlGvyString
    }

    public String setUnitStuffFields(String xmlGvyData, Object gvyCmisUtil)
    {
        def xmlGvyString = xmlGvyData
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dPort=",ContextHelper.getThreadFacility().getFcyId())
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dischargePort=",ContextHelper.getThreadFacility().getFcyId())
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dir=","IN")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=","null")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"leg=","null")
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"misc1=","null")

        return xmlGvyString
    }
}