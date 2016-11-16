/*

Groovy code called from the out gate to print an extra HAZARD (DCM) document if
the group is YB or XFER to WO for deliver import.

1/28/9: Meeting with Joce and Chris Scott. Remove extra HAZ doc for WO. Only need the original one.
10/19/2009 GR   unitNbr Null Check
03/11/2010 GR   Navis Code Optimization

*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.adaptor.document.PrintDocument
import com.navis.argo.business.reference.Container
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction

public class CustomPrintHazardDocument extends GroovyInjectionBase
{
    public void execute(inDao, api)
    {
        def transactions = (Set<TruckTransaction>) inDao.tv.tvdtlsTruckTrans

        for (transaction in transactions) {
            if (transaction.tranSubType in [TranSubTypeEnum.DE]) {
                def unit = transaction.getTranUnit()

                // Filter for group == YB
                if (unit!=null && unit.getFieldValue("unitRouting.rtgGroup.grpId") in ['YB']) {
                    // Set the doc type id
                    def docTypeId = "HAZARD"
                    inDao.put("docTypeId", docTypeId)

                    // Print the document
                    (new PrintDocument()).execute(inDao)
                }
            }
        }
    }
}



