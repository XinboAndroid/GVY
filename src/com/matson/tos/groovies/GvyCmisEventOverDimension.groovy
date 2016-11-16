import com.navis.apex.business.model.GroovyInjectionBase

public class GvyCmisEventOverDimension{

    def ACTION = "action='null'"
    def LAST_ACTION = "lastAction='null'"

    //UNIT_DIMENSION_UPDATE
    public String getUnitDimensionUpdate(String xmlGvyData,Object event,Object unit)
    {
        def  xmlGvyString = xmlGvyData
        try
        {
            def outOfGauge  = unit.getFieldValue("unitIsOog");

            if( (event.wasFieldChanged('OOGFront') || event.wasFieldChanged('OOGBack') ||
                    event.wasFieldChanged('OOGTop') || event.wasFieldChanged('OOGLeft') ||
                    event.wasFieldChanged('OOGRight')) && outOfGauge.equals(Boolean.FALSE) )
            {
                xmlGvyString = xmlGvyString.replace(LAST_ACTION,"lastAction='OVD'");
                xmlGvyString = xmlGvyString.replace(ACTION,"action='OVD'")
            }
        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString;
    }

    //MAP ACTION EDT
/*   public String getCmisActionEDT(String xmlGvyData, Object appendObj)
  {
    def  xmlGvyString = xmlGvyData
    try
   {
      def action = appendObj.getFieldValues(xmlGvyString, "action=");
      def lastaction = appendObj.getFieldValues(xmlGvyString, "lastAction=");
      def actionOld = "action='"+action+"'";
      def lastActionOld = "lastAction='"+lastaction+"'"

      xmlGvyString = xmlGvyString.replace(lastActionOld,"lastAction='EDT'");
      xmlGvyString = xmlGvyString.replace(actionOld,"action='EDT'")

   }catch(Exception e){
       e.printStackTrace()
   }

    return xmlGvyString;
   }
*/

}//Class Ends