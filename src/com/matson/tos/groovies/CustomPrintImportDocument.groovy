/*

Groovy code called from the out gate to print an extra DEL IMPORT document if
the consignee is TARGET* deliver import.

1/27/2009  SKB  Fixed null pointer exception if no consignee
4/09/2009  CNB  Fixed the backwards null/continue
10/19/2009 GR   unitNbr Null Check
03/11/2010 GR   Navis Code Optimization
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.adaptor.document.PrintDocument
import com.navis.argo.business.reference.Container
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction

public class CustomPrintImportDocument extends GroovyInjectionBase
{
    public void execute(inDao, api)
    {
        def transactions = (Set<TruckTransaction>) inDao.tv.tvdtlsTruckTrans

        for (transaction in transactions) {
            if (transaction.tranSubType in [TranSubTypeEnum.DI]) {
                def unit = transaction.getTranUnit()

                if(unit == null) continue

                // Filter for consignee == TARGET*
                def consignee = unit.getFieldValue("unitGoods.gdsConsigneeAsString");
                if (consignee != null && consignee.startsWith('TARGET')) {
                    // Set the doc type id
                    def docTypeId = "DEL IMPORT"
                    inDao.put("docTypeId", docTypeId)

                    // Print the document
                    (new PrintDocument()).execute(inDao)
                    log('TARGET PRINT x2')
                }
            }
        }
    }
}