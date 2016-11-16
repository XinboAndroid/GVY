/*
* Sr  doer  date      Change
* A1 GR    09/07/10   Update emailSender class method
* A2 GR    09/20/10   i)Add UserId to report ii)Add user email from the list
* 08/16/11 2.1 Updated Email Method
* A3 GR    11/01/11   Handled Null Pointer Exception
* A4 PS		12/5/11  Kulana/RFID
* A5 PS  03/15/12  Removed change made in A3
* A6 GR  04/04/12  Added change to set and unset flag for multiple posting
*/
import com.navis.inventory.business.units.UnitEquipDamages;
import com.navis.inventory.business.units.UnitEquipDamageItem;

import com.navis.argo.business.reference.EqComponent;
import com.navis.argo.business.reference.EquipDamageType;
import com.navis.argo.business.reference.Equipment;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.security.ArgoUser;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.argo.business.reports.DigitalAsset;
import com.navis.argo.ContextHelper
import com.navis.security.business.user.BaseUser
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.framework.business.Roastery
import com.navis.services.business.event.GroovyEvent




public class KulanaProcessor extends GroovyInjectionBase
{

    def emailSender = null;
    def unitId = null;
    String emailfrom = "matsonsupport@nowsol.com";  //A1
    String emailTo = getEmailId();
    //String emailfrom = "1aktosdevteam@matson.com";  //A1
    //String emailTo = "1aktosdevteam@matson.com";
    private static final String eol = "\r\n";


    public void process(Object event)
    {
        def gvyInj = null;
        try
        {
            def unit = event.getEntity();
            gvyInj = new GroovyInjectionBase();

            if(!event.event.evntAppliedBy.startsWith('snx')){
                unit.setFieldValue("unitFlexString06","N");
            }

            String strDate = ContextHelper.formatTimestamp(event.getEvent().getEventTime(), ContextHelper.getThreadUserTimezone())
            unitId = unit.unitId;
            unitId = unitId.replace('$','');

            def now = unit.getFieldValue("unitFlexString11")
            if(!'NOW'.equals(now)){
                return; //IF NOT NOW then DOnt Process Request
            }

            def yardLocation = unit.getFieldValue("unitActiveUfv.ufvFlexString03")
            def cntrNbr = unit.getFieldValue("unitActiveUfv.ufvFlexString04")
            def rptDateTime = unit.getFieldValue("unitActiveUfv.ufvFlexDate04")
            String rptDate =  ContextHelper.formatTimestamp(rptDateTime, ContextHelper.getThreadUserTimezone())
            def notes = unit.getUnitRemark();

            //A2 - Add user EmailId
            //BaseUser baseUser = new BaseUser()
            def doer = event.event.evntAppliedBy
            int indx = doer.indexOf(":");
            doer = doer.substring(indx+1);
            BaseUser user = BaseUser.findBaseUser(doer)
            String userId = null;  String userEmailId = null;
            if(user != null){
                userId = user.getBuserFirstName() +" "+ user.getBuserLastName();
                userEmailId = user.getBuserEMail();
                userEmailId = userEmailId == null ? emailfrom : userEmailId
            }else{
                userId = "";
                userEmailId = emailfrom
            }


            StringBuffer buf = new StringBuffer();
            buf.append(eol);
            buf.append("                    NOW Vehicle Trouble                Date:"+strDate+eol);
            buf.append("                Matson Navigation Honolulu, Hawaii                "+eol);
            buf.append("----------------------------------------------------------------------------");
            buf.append(eol);
            buf.append(eol);
            buf.append("Vehicle ID         : "+unitId);
            buf.append(eol);
            buf.append("Container Nbr      : "+cntrNbr);
            buf.append(eol);
            buf.append("Yard Location      : "+yardLocation);
            buf.append(eol);
            buf.append("Reported Date Time : "+rptDate);
            buf.append(eol);
            buf.append("Notes              : "+notes);
            buf.append(eol);
            buf.append("Reported By        : "+userId);
            buf.append(eol); buf.append(eol); buf.append(eol);
            buf.append(eol); buf.append(eol); buf.append(eol);

            //A2
            emailfrom = userEmailId != null && userEmailId.trim().length() > 0 ? userEmailId.trim() : emailfrom;
            emailSender = emailSender != null ? emailSender : gvyInj.getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail(emailfrom, emailTo.trim(), 'Vehicle Trouble Report: '+unitId , buf.toString()) //A1


/*     HashMap parameters = new HashMap();

	 parameters.put('Date',strDate);
	 parameters.put('vehicleId', unitId);
	 parameters.put('cntrNbr', cntrNbr)
	 parameters.put('location',yardLocation);
	 parameters.put('reportedDtTime', rptDate);
	 parameters.put('now', now);
     parameters.put('notes', notes)


     //Create and Mail Report
	 ArrayList arrList = new ArrayList() //Empty List
     JRDataSource ds = new JRMapCollectionDataSource(arrList);
     def reportRunner = gvyInj.getGroovyClassInstance("ReportRunner");
     reportRunner.emailReport(ds, parameters, "NOW VEHICLE TROUBLE REPORT", emailTo, 'Vehicle Trouble Report: '+unitId ,'Vehicle Trouble Report: '+unitId+'\n\n\nPlease Find Attached a PDF copy of the Vehicle Trouble Report'  );
*/
        }catch(Exception e){
            e.printStackTrace();
            //Email Code Here
            emailSender = emailSender != null ? emailSender : gvyInj.getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail(emailTo, "Kulana Error : Unit="+unitId+" Message Processing Error ", e.getMessage());
        }
    }//Method Ends

    //Kulana update from RFID
    public String execute(Map inParameters) {
        try{
            //1. fetch data from the xml
            def recorder = (String) inParameters.get("recorder");
            def unitNbr = (String) inParameters.get("unitNbr");
            def unitRemark = (String) inParameters.get("notes");
            unitRemark = unitRemark == null ? '' : " "+unitRemark
            def blnFlag = false;


            //2. set N4 values
            def complex = ContextHelper.getThreadComplex();
            def unitFinder = (UnitFinder)Roastery.getBean("unitFinder");
            def chsproc = getGroovyClassInstance("NowToN4ChasMessageProcessor")
            def unitObj = chsproc.findActiveUeUsingEquipmentInAnyRole("\$"+unitNbr,complex,unitFinder);
            def flgValue = unitObj.getFieldValue("unitFlexString06");
            if(flgValue == null || "N".equals(flgValue)){
                unitObj.setFieldValue("unitFlexString06","Y");
                blnFlag = true
            }
            if(blnFlag){
                unitObj.setFieldValue("unitFlexString11", "SHOP");
                def tempNotes = unitObj.getFieldValue("unitRemark");
                tempNotes = (tempNotes == null ? '' : tempNotes)+unitRemark
                unitObj.setFieldValue("unitRemark", tempNotes);
                def ufv = unitObj.unitActiveUfv
                ufv.setFieldValue("ufvFlexDate04", new Date());
                //3. Generate an Event
                def event = new GroovyEvent( null, unitObj);
                event.postNewEvent( "ASSIGN VEHICLE", "Kulana Automated Now Assignment");
            }


        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public boolean isValueSet(Object unit){
    }

    public String getEmailId(){

        String emailId = getReferenceValue("KULANA", "KULANA", null, null, 1)  //A16
        if(emailId == null){ return "1aktosdevteam@matson.com" }
        return emailId
    }

}//Class Ends