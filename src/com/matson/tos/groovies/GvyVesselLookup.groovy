/*******************************************************************
 * Srno   Date	            AuthorName	Change Description
 * A1      03/17/09       Glenn Raposo	NullPointer Check
 * A2      06/29/09       Steven Bauer	added advanceVV
 * A3      07/15/09       Steven Bauer	No phase VV for NI.
 * A4      04/11/09       Glenn Raposo   handle Multiple Phases
 * A5      07/13/11       Glenn Raposo   Barge Chassis on ALE and MKA added Desk position as RORO
 * 08/16/11 2.1 Updated Email Method
 * A6  GR 10/25/11  Removed Weblogic API
 * A7  GR 11/06/11  Corrected Email
 * A8  GR    11/10/11  TOS2.1 Get Environment Variable
 * A9     10/01/13        Kelvin Mikami  Increase tier for ISO schema from 14 to 16
 **********************************************************************/
import com.navis.argo.business.api.GroovyApi;
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import java.util.Iterator;
import java.util.Collection;
import com.navis.vessel.VesselField;
import com.navis.vessel.business.atoms.VesselTypeEnum;
import com.navis.argo.ArgoField;
import com.navis.vessel.business.operation.Vessel;
import com.navis.vessel.business.atoms.StowageSchemeEnum;
import com.navis.inventory.InventoryField;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum;
import com.navis.vessel.api.VesselVisitField;
import com.navis.framework.business.Roastery;
import com.navis.framework.persistence.HibernateApi;
import com.navis.argo.business.api.ServicesManager;
import com.navis.argo.business.atoms.EventEnum;

import com.navis.framework.portal.BizRequest;
import com.navis.framework.portal.BizResponse;
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.portal.CrudOperation;
import com.navis.framework.portal.CrudDelegate;
import com.navis.framework.util.TransactionParms;

import com.navis.argo.*;
import com.navis.framework.email.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.commons.lang.StringUtils;
import com.navis.argo.business.atoms.EquipClassEnum


public class GvyVesselLookup  extends GroovyInjectionBase {
    public static final String AboveDeck = "OD";
    public static final String BelowDeck = "BD";
    public static final String RORO      = "RO";
    public static final String UNK      = "UNK";
    GroovyApi groovyApi = new GroovyApi();

    public boolean isBarge(String id) {
        def type = lookupType(id);
        if(type != null && type.equals(VesselTypeEnum.BARGE)) {
            return true;
        }
        return false;
    }

// Lookup a vessel type by vessel id.
// Returns a com.navis.vessel.VesselField.VesselTypeEnum;
    public Object lookupType(String id) {
        //Example IDs
        // def id = "ALE";
        //def id = "MHI";

        try {
            DomainQuery dq = QueryUtils.createDomainQuery("VesselClass");

            dq.addDqPredicate(PredicateFactory.eq(VesselField.VESCLASS_ID,id ));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            if(list != null) {
                Iterator iter = list.iterator();
                while(iter.hasNext()) {
                    def item = iter.next();
                    // println("type="+item.getFieldValue("Type"));
                    return item.getFieldValue("vesclassVesselType");
                    // Possible values are
                    VesselTypeEnum.CELL // Containership
                    VesselTypeEnum.BARGE // Barge
                }
            }


            return null;
        } catch (Exception e) {
            println("Exception in Vessel Type lookup  "+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }


    public Object getCarrierVisit(String id) {
        DomainQuery dq = QueryUtils.createDomainQuery("CarrierVisit");
        dq.addDqPredicate(PredicateFactory.eq(ArgoField.CV_ID,id ));
        def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        if(list != null && list.size() > 0) return list.get(0);
        return null;
    }


    public String setDeckPositionType(Object unit) {
        String deck = getDeckPositionType(unit);
        if(deck != null) {
            def ufv = unit.unitActiveUfv;
            if(ufv == null) {
                def gvyBaseClass = new GroovyInjectionBase()
                def lookup =  gvyBaseClass.getGroovyClassInstance("GvyUnitLookup");
                ufv = lookup.lookupFacility(unit.primaryKey);
            }
            if(ufv != null)  ufv.setFieldValue("ufvFlexString06",deck);
        }
        return deck;
    }

    public String getDeckPositionType(Object unit) {
        def gvyBaseClass = new GroovyInjectionBase()
        def lookup =  gvyBaseClass.getGroovyClassInstance("GvyUnitLookup");
        //A11
        def primaryClass = unit.unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass

        def position  = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition");
        if(position == null) {
            def ufv = lookup.lookupFacility(unit.primaryKey);
            if(ufv != null) position = ufv.getFieldValue("ufvLastKnownPosition");
        }
        if(position == null) return null;
        if(!position.posLocType.equals(LocTypeEnum.VESSEL)) return null;

        def visit = position.posLocId;
        def cv =   getCarrierVisit(visit);
        def vesselId = cv.carrierVehicleId;
        def vessel =  Vessel.findVesselById(vesselId);
        def schema =  vessel != null ? vessel.vesStowageScheme : null; //A1
        boolean iso = schema != null && schema.equals(StowageSchemeEnum.ISO) ?  true : false;

        def slot = position.posSlot;
        // Need to be at least 2 char to analize it.
        if( !EquipClassEnum.CHASSIS.equals(primaryClass) && (slot == null || slot.length() < 2)) return UNK;

        if(vesselId.equals("KAU") || vesselId.equals("MAU") ) {
            if(vesselId.equals("KAU") ) {
                char c1 = slot.charAt(0);
                char c2 = slot.charAt(1);
                if(Character.isLetter(c1) || Character.isLetter(c2)) {
                    return RORO;
                }
            }
            String first2Char = slot.substring(0,2);
            try {
                int num = Integer.parseInt(first2Char);
                if(num >= 2 && num <= 9) return AboveDeck;
            } catch (Exception e) {
                // Do nothing, standard NON-ISO
            }

        } else if (vesselId.equals("LHE") ) {
            String lastChar = slot.substring(slot.length()-1);
            if(lastChar.equalsIgnoreCase("A") || lastChar.equalsIgnoreCase("A")) return AboveDeck;
            try {
                int lastNum = Integer.parseInt(lastChar);
                if(lastNum >= 7) return AboveDeck;
                else return BelowDeck;
            } catch (Exception e) {
                return UNK;
            }
        } else if(vesselId.equals("LUR") || vesselId.equals("MAT") ) {
            if(slot.length() == 3 || slot.length() == 4 ) {
                return RORO;
            }
        } else if(vesselId.equals("MKA")  ||  vesselId.equals("ALE") ) {
            if(EquipClassEnum.CHASSIS.equals(primaryClass)){ //A5
                return RORO;
            }
            if(slot.length() == 4 ) {
                return RORO;
            }
            char c1 = slot.charAt(0);
            char c2 = slot.charAt(1);
            if(Character.isLetter(c1) || Character.isLetter(c2)) {
                return RORO;
            }
        }

//log("Deck ISO="+iso);
        if(iso) {
            try {
                String last2Char = slot.substring(slot.length()-2);

                int lastNum = Integer.parseInt(last2Char);
//log("Deck Char="+lastNum);
                if(lastNum >= 80) return AboveDeck;
                if(lastNum <= 16) return BelowDeck;  //A9  14 to 16
                return UNK;
            } catch (Exception e) {
                log("Deck Error="+e.getMessage());
                return UNK;
            }
        } else {

            String secondLastChar = slot.substring(slot.length()-2,slot.length()-1);
            if (secondLastChar.equals("0")) return AboveDeck;
            if (secondLastChar.equals("1")) return BelowDeck;
            return UNK;
        }
        return UNK;

    }

    public boolean isClosed(CarrierVisitPhaseEnum state) {
        if(state == CarrierVisitPhaseEnum.DEPARTED  || state == CarrierVisitPhaseEnum.CLOSED || state == CarrierVisitPhaseEnum.CANCELED || state == CarrierVisitPhaseEnum.ARCHIVED ) {
            return true;
        }
        return false;
    }

    public void advanceVV(visit,phase) {
        def  originalUserContext = ContextHelper.getThreadUserContext();

        def sco = originalUserContext.getScopeCoordinate();
        def scoper = Roastery.getBean("entityScoper");
        def scopeNodeEntity = scoper.getScopeNodeEntity(sco);
        def context = ContextHelper.getSystemUserContextForScope(scopeNodeEntity.getScopeEnum(), scopeNodeEntity.getPrimaryKey());
        TransactionParms.getBoundParms().setUserContext(context);
        def request = new BizRequest(context);
        request.setUserContext(context);

        def response = new BizResponse();
        def changes = new FieldChanges();
        changes.setFieldChange(VesselVisitField.VVD_VISIT_PHASE, visit.vvdVisitPhase , phase);


        def crud = new CrudOperation(null, 2, "VesselVisitDetails", changes, visit.cvdGkey);
        request.addCrudOperation(crud);
        request.setApiTarget("vslAdvanceVesselVisit");

        def target =  request.getUpdateTarget();
        def task = Roastery.getBean(target);
        task.advanceVesselVisit(request,response);


        TransactionParms.getBoundParms().setUserContext(originalUserContext);

    }

    public void updateVVCheck(visit) {
        def emailto = "1aktosdevteam@matson.com";
        def emailfrom = "1aktosdevteam@matson.com";

        def phase = "";
        try {
            String note = visit.vvdNotes;

            if(note == null) return;
            String phaseNote = "phase_vv='";
            int index = note.indexOf("phase_vv='");
            if(index == -1) return;
            int endIndex = note.indexOf("'",index+phaseNote.length());
            if(endIndex == -1) return;
            //String phaseStr = note.substring(index+phaseNote.length(),endIndex);
            def phaseStr = note.substring(index+phaseNote.length(),endIndex);
            println("Visit_Id"+visit.cvdCv.toString()+"phaseStr ="+phaseStr)
            phaseStr = phaseStr.split(',')
            for(aPhase in phaseStr){
                println("Visit_Id"+visit.cvdCv.toString()+"aPhase ="+aPhase)
                phase =  com.navis.argo.business.atoms.CarrierVisitPhaseEnum.getEnum(aPhase);
                if(phase == null) return
                //note = note.substring(0,index)+note.substring(endIndex+1);
                // visit.vvdNotes = note;

                def facility = visit.getFieldValue("cvdCv.cvFacility.fcyId");
                if(ContextHelper.getThreadFacility().getFcyId().equals(facility) &&
                        ("ANK".equals(facility) || "DUT".equals(facility) || "KDK".equals(facility))) {
                    advanceVV(visit,phase);
                } else {
                    def changes = new FieldChanges();
                    changes.setFieldChange(VesselVisitField.VVD_VISIT_PHASE, visit.vvdVisitPhase , phase);
                    visit.vvdVisitPhase=phase;

                    ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
                    srvcMgr.recordEvent(EventEnum.PHASE_VV, "NI Phase change", null, null, visit, changes);
                    println("Visit_Id"+visit.cvdCv.toString()+"After Recording PhaseVV")
                }
            }//for ends A4

        } catch (Exception e) {
            log("ERR_GVY_PVV_001. Cound not advance vessel " + phase+". Error message is "+e.getMessage());
            def text = new StringBuffer();
            text.append("ERR_GVY_PVV_001. Cound not advance vessel ");
            text.append(visit.cvdCv.toString());
            text.append(" desired phase ");
            text.append(phase.getName());
            text.append("\n Error message is ");
            text.append(e.getMessage());
            text.append("\n");
            text.append(getStackTrace(e));


            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailto, ";,"));
            msg.setSubject(getEnvVersion()+"Phase_VV error "+visit.cvdCv.toString());
            msg.setText(text.toString());
            msg.setReplyTo(emailfrom);
            msg.setFrom(emailfrom);
            def  emailManager = Roastery.getBean("emailManager");
            emailManager.sendEmail(msg);
        }
    }

    public  String getEnvVersion()  {
        String envType = groovyApi.getReferenceValue("ENV", "ENVIRONMENT", null, null, 1)
        if("PRODUCTION".equals(envType)){
            return "";
        }
        return envType+" ";
    }

    public String getStackTrace(Throwable inException) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        inException.printStackTrace(pw);
        pw.flush();
        pw.close();
        return sw.toString();
    }

}