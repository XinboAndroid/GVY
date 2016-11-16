/*

Groovy code called from the gate form to print or not print the document based on the logic below.
The logic fails the transaction with an error message if ufvFlexString02 is not Y and user entered a chassis accessory number

Amine Nebri, anebri@navis.com - July 4 2008

Setup:  BAT # is a required field at the Trk Visit IN stage.

If the BAT # entered is between 101 ? 200, print the Doc Type ID = EIT.
If the BAT # entered is between 701 ? 799, do not print anything (this is an in-house UTR, no ticket needed).
If the BAT # entered is any other value, print the Doc Type ID = NO EIT.

4/28/09: CB: Added conditionals for the create and print. Default is to do both.
                      However, for SI GATE, the tx's will do the create and the tv will do the print.

11/06/14: Peter Seiler Change PrintDocument and CreateDocument calls to use 2.6 API.
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.adaptor.document.CreateDocument
import com.navis.road.business.adaptor.document.PrintDocument
import com.navis.road.business.api.GroovyRoadApi
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.road.portal.GateConfigurationParameterConstants

public class CustomCreateAndPrintDocument extends GroovyInjectionBase
{
    public void execute(TransactionAndVisitHolder inDao, GroovyRoadApi api) {
        execute(inDao, api, true, true)
    }

    public void execute(TransactionAndVisitHolder inDao, GroovyRoadApi api, boolean isCreate, boolean isPrint)
    {

        def batString = inDao.tv.tvdtlsBatNbr


        if (batString == null) {
            api.log("CustomCreateAndPrintDocument null");
            return
        }

        api.log("CustomCreateAndPrintDocument for bat ${batString}");

        def batNumber = -99

        try
        {
            // We convert the string into an integer
            batNumber = batString.toInteger()
        }
        catch (Exception e)
        {
            // We exit if the Bat Number is not numeric
            //throw new Exception("BAT # must be numeric")
            return
        }

        // No need to print anything in this case. This is an in house UTR
        if ( batNumber >= 701 && batNumber <= 799)
            return

        def docTypeId = "NO EIT"

        if (batNumber >= 101 && batNumber <= 200)
            docTypeId = "EIT"

        // Set the doc type id and the pop-up value and number of copies
        inDao.put("docTypeId", docTypeId)

        // Create the document
        if (isCreate)
        {
            new CreateDocument().execute(inDao)
        }

        // Print the document
        if (isPrint)
        {
            inDao.put("displayPrintPopup", 'true')
            inDao.put("printNoOfCopies", '1')
            new PrintDocument().execute(inDao)
        }
    }
}