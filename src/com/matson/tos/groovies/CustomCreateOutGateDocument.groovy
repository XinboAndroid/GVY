/*

Groovy code called from the out gate to print an extra HAZARD (DCM) document if
the group is YB or XFER to WO for deliver import.

1/28/9: Meeting with Joce and Chris Scott. Remove extra HAZ doc for WO. Only need the original one.
10/19/2009 GR   unitNbr Null Check
03/11/2010 GR   Navis Code Optimization

*/
/*
* SrNo  Doer  Date      Change
* A1    Gopal 10/27/11  Workaround to remove Confirm Arrival
                        Create Ticket using groovy code till Bug Fix
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.adaptor.document.CreateDocument
import com.navis.argo.business.reference.Container
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction;

public class CustomCreateOutGateDocument extends GroovyInjectionBase
{
    public void execute(inDao)
    {
        def docTypeId = "TURN TIME"
        inDao.put("docTypeId", docTypeId)
        (new CreateDocument()).execute(inDao)
    }
    public void setCtrNbr(dao)
    {
        TruckTransaction tran = dao.tran
        tran.setTranCtrNbrAssigned(tran.tranCtrNbr)
    }
}