import java.util.List;

import com.navis.argo.ContextHelper

import com.navis.argo.business.api.ArgoUtils

import com.navis.argo.business.api.GroovyApi

import com.navis.framework.portal.UserContext

import com.navis.inventory.business.units.Unit

import com.navis.services.business.event.GroovyEvent

import com.navis.apex.business.model.GroovyInjectionBase

import com.navis.inventory.business.units.UnitFacilityVisit

import com.navis.argo.business.model.CarrierVisit

import java.sql.Connection

import java.sql.ResultSet

import java.util.ArrayList

import com.navis.argo.business.atoms.EventEnum

import com.navis.services.business.api.EventManager

import com.navis.services.business.rules.EventType

import com.navis.framework.business.Roastery




/* EP000220294 dkannadasan Feb 18 2015 - Code to prevent NewVes complete if any errors pending to be corrected */
public class MATFinalCheckNewVesComplete extends GroovyApi {



    def inj = new GroovyInjectionBase();

    def emailSender = inj.getGroovyClassInstance("EmailSender");

    List errors = null;

    Boolean canProceed = Boolean.TRUE;

    HashMap errorsData = null;



    public Boolean execute(GroovyEvent event, Object api)  {

        // get vessel voyage id of the unit

        Unit unit = (Unit) event.getEntity();

        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive();

        CarrierVisit cv = ufv.getInboundCarrierVisit();

        String vesVoy = cv.getCvId();

        // vesVoy = "MHI289";

        ResultSet rs = null;

        ArrayList reportGenRefList =  new ArrayList();



        // get a connection to TDP database source

        try {

            def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");

            Connection conn;

            try {

                conn = GvyRefDataLookup.connect();

                rs = GvyRefDataLookup.getTdpNewVesErrorData(vesVoy,conn);

                if(rs!=null){

                    while (rs.next()) {

                        errorsData = new HashMap();

                        errorsData.put("ERROR_ID",rs.getString("ERROR_MESSAGE_ID"));

                        errorsData.put("ERROR_TYPE",rs.getString("ERROR_TYPE"));

                        errorsData.put("ERROR_DESC",rs.getString("ERROR_DESC"));

                        errorsData.put("VESVOY",rs.getString("VESVOY"));

                        println("errorsData:::"+errorsData);

                        reportGenRefList.add(errorsData);

                    }

                }

            } finally {

                GvyRefDataLookup.disconnect(conn);

            }



        }



        catch (e)

        {



        }





        // if there are errors, then send notification email and return false;

        if(reportGenRefList!=null && reportGenRefList.size()>0) {

            canProceed = Boolean.FALSE;

            // record a NEWVES_COMPLETE_FAILED service event

            EventManager sem = (EventManager) Roastery.getBean(EventManager.BEAN_ID);

            EventType eventType = EventType.findEventType("NEWVES_COMPLETE_FAILED");

            unit.recordUnitEvent(eventType, null, "Fix pending SN4 Errors before applying NEWVES_COMPLETED");

            // send email

            String emaiBody = "NewVes process for "+vesVoy+" cannot be completed as  "+reportGenRefList.size()+" errors are pending to be corrected\n\n";





            for (HashMap error : reportGenRefList)

            {

                emaiBody +=error.get("ERROR_DESC");

                emaiBody +="\n\n";

            }

            emailSender.custSendEmail( "1aktosdevteam@matson.com",  "1aktosdevteam@matson.com", "Error : NewVes cannot be completed for "+vesVoy,emaiBody );

            return canProceed;

        }



        return canProceed;

    }



}
