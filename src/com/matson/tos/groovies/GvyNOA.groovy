/*
* Srno doer date      Change
* A1   GR   12/15/10  Create xml for USCG dept -10
* A2   GR   03/02/11  Comment out Arrival Notice as it doesnt have CDC Info
* A3   GR   03/03/11  Added Departure Date fix
* A4   GR   03/03/11  Added Next port fix
* A5   GR   03/03/11  Added HAZ item Destination Fix
* A6   GR   05/11/11  Arrival Notice as Per USCG
* A7   GR   05/11/11  Cannot have multiple next ports. So only information about the next port.
* A8   GR   08/16/11  Appended HazPeoperName with Imdg Code
* A9   GR   08/17/11  Stopped attaching the CDC.TXT file
* 08/16/11 2.1 Updated Email Method
* A10  GR   10/25/11  Removed Weblogic API
* A11  GR   11/10/11  TOS2.1 Get Environment Variable
* A12  KM   04/11/12  2.1 updated email method - emailManager.sendEmail
* A13  AA   05/31/12  Removed the underscore from the file names of the attachments for NOA
*      RI	12/16/13  Added LONGSHOREMAN_WORK_DECLARATION('NOT PROVIDED') under VESSEL and CREW nodes and updated xsd version as per as per the USCG_ENOAD_Schema_3.3.xsd
* A14  KM   01/24/14  NOA change RLamb to Dwthompson
* A15  RI   02/10/14  Added Kokua to TUG
* A16  RI   03/04/14  Removed COMP_CERT and SFTYMGMT_CERT nodes under VESSEL as per the TT - EP000202148
*/


import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.ContextHelper;
import com.navis.vessel.business.operation.Vessel;
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.vessel.business.schedule.VesselVisitDetails;
import com.navis.framework.email.*;
import com.navis.framework.business.Roastery;
import org.springframework.core.io.ByteArrayResource;
import com.navis.argo.business.reports.DigitalAsset;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.util.ArrayList;
import com.navis.inventory.business.imdg.ImdgClass;
import com.navis.inventory.business.imdg.HazardItem;
import com.navis.inventory.business.units.UnitFacilityVisit;
import org.apache.commons.lang.StringUtils;
//-- CDC
import org.springframework.core.io.ByteArrayResource;
import com.navis.argo.business.reports.DigitalAsset;
import org.springframework.core.io.Resource;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.HashMap;
//--To Read CrewList Spreadsheet
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import jxl.Cell;
import jxl.CellType
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import jxl.DateCell;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;
import com.navis.argo.business.api.GroovyApi;

//Math.round(weight*2.20462262)

public class GvyNOA extends GroovyInjectionBase {
    private static final List hazardList = [ImdgClass.IMDG_11, ImdgClass.IMDG_12, ImdgClass.IMDG_15, ImdgClass.IMDG_51, ImdgClass.IMDG_7];
    private static final String emailTo = "1aktosdevteam@matson.com"; //,pschroeder@matson.com //A14
    private static final String errorEmailTo = "1aktosdevteam@matson.com"; //,pschroeder@matson.com
    //private static final String errorEmailTo = "1aktosdevteam@matson.com"; //,pschroeder@matson.com
    //private static final String emailTo = "1aktosdevteam@matson.com";
    //private static final String errorEmailTo = "1aktosdevteam@matson.com"; //,pschroeder@matson.com
    private static final String emailFrom = "1aktosdevteam@matson.com";  //"1aktosdevteam@matson.com";  //A14
    private static final String eol = "\r\n";
    private static final String cdcClassCodeA = "Division 1.1 or 1.2 Explosives";
    private static final String cdcClassCodeB = "Division 1.5D Blasting Agents";
    private static final String cdcClassCodeC = "Division 2.3 Poisonous Gas";
    private static final String cdcClassCodeD = "Division 5.1 Oxidizing Materials";
    private static final String cdcClassCodeE = "A Liquid Material of Division 6.1 'Poisonous Material'";

    GroovyApi groovyApi = new GroovyApi();


    HashMap classMap = new HashMap();
    HashMap unNaMap = new HashMap();
    HashMap typweightMap = new HashMap();

    //Changes for NOA XML TO USCG
    def util = null;  def zone = null;  def nextVV = null;  def lastVV = null;
    String arrPort = null; String eta = null; String etd = null;
    String sailDate = null; String dPort1= null;  String dPort2= null;
    String eta2 = null; String etd2 = null; String allPort = null;
    String vesselName = null; String abs = null; String sailDeptdate = null;

    //ArrayList cdcHazardList = new ArrayList();
    HashMap cdcHazmap = new HashMap();
    VesselVisitDetails vesVisit = null;
    String crewListXml  = null;

    public void execute(VesselVisitDetails vv, Object event)
    {
        vesVisit = vv;
        try
        {
            if(!"BARGE".equalsIgnoreCase(vv.vvdVessel.vesVesselClass.vesclassVesselType.name)) {
                return;
            }
            readCdcSpreadsheet()

            if(!notify(vv) ) {

                return;
            }
            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));

            msg.setSubject(getEnvVersion()+"USCG-Matson NOD/NOA for "+vv.cvdCv.toString());

            msg.setText(noaMessage(vv, event));

            msg.setReplyTo(emailFrom);

            msg.setFrom(emailFrom);

            DefaultAttachment dcm = new DefaultAttachment();
            ByteArrayResource bar = new ByteArrayResource(generateDCM(vv).getBytes());
            dcm.setAttachmentContents(bar);
            dcm.setAttachmentName(vv.cvdCv.toString()+"DCM.txt");
            dcm.setContentType("text/html");
            //A9 - msg.addAttachment(dcm);

            //SET ARRIVAL XML - A2
            /*DefaultAttachment uscgArrXml = new DefaultAttachment();
            ByteArrayResource uscgArrBar = new ByteArrayResource(arrivalNoaXml().getBytes());
            uscgArrXml.setAttachmentContents(uscgArrBar);
            uscgArrXml.setAttachmentName(vv.cvdCv.toString()+"arrival.XML");
            uscgArrXml.setContentType("text/xml");
            msg.addAttachment(uscgArrXml); */

            //SET DEPARTURE XML
            DefaultAttachment uscgDeptXml = new DefaultAttachment();
            ByteArrayResource uscgDeptBar = new ByteArrayResource(arrivalNoaXml().getBytes());
            uscgDeptXml.setAttachmentContents(uscgDeptBar);
            //A13 change by Amit Asija
            uscgDeptXml.setAttachmentName(vv.cvdCv.toString()+getPortName(dPort1) +"Arrival.xml");
            uscgDeptXml.setContentType("text/xml");
            msg.addAttachment(uscgDeptXml);


            def crewName = vv.vvdVessel.vesNotes;
            if(crewName != null ) {
                crewName = crewName.toUpperCase();
            }

/*  -- A2 Stop Reading the CrewList
       DefaultAttachment crewList = new DefaultAttachment();
       if(crewName != null) {
              //def b2 = new ByteArrayResource(DigitalAsset.findImage(crewName));
              //crewList.setAttachmentContents(b2);
              //crewList.setAttachmentName("crewlist.snp");
	       //crewList.setContentType("application/octet-stream");
	      //msg.addAttachment(crewList);
	   } else {
	      msg.setTo(StringUtils.split(errorEmailTo, ";,"));
	      msg.setSubject(getEnvVersion()+"Error: No crewlist USCG-Matson NOD/NOA for "+vv.cvdCv.toString());
	   } */

            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            //emailManager.custSendEmail(msg);
            emailManager.sendEmail(msg);  //A12

        }catch(Exception e){
            e.printStackTrace()
        }

    }

    public boolean notify(VesselVisitDetails vv) {
        List units = getUnitFinder().findAllUfvOnBoard(vv.cvdCv);

        if(units == null) return false;

        Iterator iter = units.iterator();
        while(iter.hasNext() ) {
            def ufv = iter.next();
            if(ufv.ufvUnit.getUnitGoods().getGdsHazards() == null) continue;
            Iterator hazardIter = ufv.ufvUnit.getUnitGoods().getGdsHazards().getHazardItemsIterator();
            while(hazardIter.hasNext()) {
                def hazard = hazardIter.next();
                def imdgClass = hazard.getHzrdiImdgCode() != null ? hazard.getHzrdiImdgCode().getKey() : null
                def nbrType = hazard.getHzrdiNbrType() != null ? hazard.getHzrdiNbrType().getKey() : null
                boolean isUnitCdc = isUnitCdc(imdgClass,hazard.hzrdiUNnum,nbrType,hazard.hzrdiWeight)

                if(isUnitCdc){
                    return true;
                }
            }
        }
        return false;

    }

    public String getABS(String id) {
        if(id.equalsIgnoreCase("HAL")) return "676972";
        if(id.equalsIgnoreCase("LOA")) return "676973";
        if(id.equalsIgnoreCase("ALE")) return "978516";
        if(id.equalsIgnoreCase("ISL")) return "933804";
        return "";
    }

    public String getRadioCallSign(String id) {
        id = id.toUpperCase();
        if("HENRY SR".equalsIgnoreCase(id))return "WTW9260";
        if("MARY CATHERINE".equalsIgnoreCase(id)) return "WTW9261";
        if("CAP LES EASOM".equalsIgnoreCase(id)) return "WTW8587";
        if("KOKUA".equalsIgnoreCase(id)) return "WBR3137";
        return "WTW1111";
    }

    public String getOfficialNbr(String id) {
        id = id.toUpperCase();
        if("HENRY SR".equalsIgnoreCase(id))return "7128679";
        if("MARY CATHERINE".equalsIgnoreCase(id)) return "7209435";
        if("CAP LES EASOM".equalsIgnoreCase(id)) return "535868";
        if("KOKUA".equalsIgnoreCase(id)) return "6516934";
        return "111111";
    }

    public String getPort(String id) {
        if(id.equalsIgnoreCase("HIL")) return "Hilo,Hawaii";
        if(id.equalsIgnoreCase("KHI")) return "Kawaihae,Hawaii";
        if(id.equalsIgnoreCase("NAW")) return "Nawiliwili,Hawaii";
        if(id.equalsIgnoreCase("KAH")) return "Kahului,Hawaii";
        if(id.equalsIgnoreCase("EBY")) return "EBEYE,MARSHALL ISLANDS";
        if(id.equalsIgnoreCase("JIS")) return "JOHNSTON ISLAND,MARSHALL ISLANDS";
        if(id.equalsIgnoreCase("KWJ")) return "KWAJALEIN,MARSHALL ISLANDS";
        if(id.equalsIgnoreCase("LNI")) return "Lanai,Hawaii";
        if(id.equalsIgnoreCase("MAJ")) return "MAJURO,MARSHALL ISLANDS";
        if(id.equalsIgnoreCase("MOL")) return "Molokai,Hawaii";
        if(id.equalsIgnoreCase("HON")) return "Honolulu, Hawaii";
        return id;

    }

    public String getPortName(String id) {
        if(id.equalsIgnoreCase("HIL")) return "Hilo";
        if(id.equalsIgnoreCase("KHI")) return "Kawaihae";
        if(id.equalsIgnoreCase("NAW")) return "Nawiliwili";
        if(id.equalsIgnoreCase("KAH")) return "Kahului";
        if(id.equalsIgnoreCase("EBY")) return "EBEYE";
        if(id.equalsIgnoreCase("JIS")) return "JOHNSTON ISLAND";
        if(id.equalsIgnoreCase("KWJ")) return "KWAJALEIN";
        if(id.equalsIgnoreCase("LNI")) return "Lanai";
        if(id.equalsIgnoreCase("MAJ")) return "MAJURO";
        if(id.equalsIgnoreCase("MOL")) return "Molokai";
        if(id.equalsIgnoreCase("HON")) return "Honolulu";

        if(id.equalsIgnoreCase("Hilo,Hawaii")) return "Hil";
        if(id.equalsIgnoreCase("Kawaihae,Hawaii")) return "Khi";
        if(id.equalsIgnoreCase("Nawiliwili,Hawaii")) return "Naw";
        if(id.equalsIgnoreCase("Kahului,Hawaii")) return "Kah";
        if(id.equalsIgnoreCase("EBEYE,MARSHALL ISLANDS")) return "Eby";
        if(id.equalsIgnoreCase("JOHNSTON ISLAND,MARSHALL ISLANDS")) return "Jis";
        if(id.equalsIgnoreCase("KWAJALEIN,MARSHALL ISLANDS")) return "Kwj";
        if(id.equalsIgnoreCase("Lanai,Hawaii")) return "Lni";
        if(id.equalsIgnoreCase("MAJURO,MARSHALL ISLANDS")) return "Maj";
        if(id.equalsIgnoreCase("Molokai,Hawaii")) return  "Mol";
        if(id.equalsIgnoreCase("Honolulu, Hawaii")) return "Hon";
        return id;

    }

    public String noaMessage(VesselVisitDetails vv, Object event) {
        util = getGroovyClassInstance("GvyEventUtil");
        zone = vv.cvdCv.cvComplex.getTimeZone();
        nextVV = vv.findNextVvd();
        lastVV = null;
        if(nextVV != null) lastVV = nextVV.findNextVvd();

        StringBuffer buf = new StringBuffer();
        arrPort = getPort(vv.cvdCv.cvFacility.id);
        eta = null;
        etd = null;
        //sailDate = util.formatDateTime(vv.cvdCv.cvATD, zone);
        sailDate = util.formatDateTime(event.getEvent().getEventTime(), zone);
        dPort1= null;
        if(nextVV != null) {
            dPort1=  getPort(nextVV.cvdCv.cvFacility.id);;
            eta =  util.formatDateTime(nextVV.cvdETA, zone);
            etd =  util.formatDateTime(nextVV.cvdETD, zone);
        }
        dPort2= null;
        if(lastVV != null) {
            dPort2 = getPort(lastVV.cvdCv.cvFacility.id );
        }
        eta2 = null;
        etd2 = null;
        allPort = dPort1;
        if(dPort2 != null && dPort2 != arrPort)  {
            eta2 = util.formatDateTime(lastVV.cvdETA, zone);
            etd2 = util.formatDateTime(lastVV.cvdETD, zone);
            allPort += "/"+dPort2;
        }
        vesselName = vv.vvdVessel.vesName
        abs = getABS(vv.vvdVessel.vesId);
        def contact = "Dave Thompson, ph:(808)848-1258, cell:(808)479-9872";   //A14
        buf.append("Matson Navigation Company, Honolulu, Hawaii                  "+eol);
        buf.append(eol);
        buf.append("Notice of Departure"+eol);
        buf.append("-------------------"+eol);
        buf.append(" 1) Arrival Port(s): ${allPort}"+eol);
        buf.append(" 2) ETA: ${eta} (${dPort1})"+eol);
        buf.append("    ETD: ${etd} (${dPort1})"+eol);
        if(eta2 != null) {
            buf.append(" 2) ETA: ${eta2} (${dPort2})"+eol);
            buf.append("    ETD: ${etd2} (${dPort2})"+eol);

        }
        buf.append(" 3) ${vesselName} (unmanned barge) "+eol);
        buf.append(" 4) USA "+eol);
        buf.append(" 5) N/A "+eol);
        buf.append(" 6) ABS, official nbr. ${abs}"+eol);
        buf.append("10) ETD: $sailDate "+eol);
        buf.append("    Port of: ${arrPort}"+eol);
        buf.append("11) ${contact}"+eol);
        buf.append("12) Dock-side, port of: Honolulu,Hawaii"+eol);
        buf.append("13) Per DCM"+eol);
        buf.append("14) Per DCM"+eol);
        buf.append("15) Per DCM"+eol);
        buf.append("16) Containerized cargo, freight all kinds."+eol);
        buf.append("17) Equipment operational per 164.35"+eol);
        buf.append("18) Unmanned barge, no crew"+eol);
        buf.append("19) Unmanned barge, no personnel on board"+eol);
        buf.append(eol);
        buf.append("Notice of Arrival "+eol);
        buf.append("------------------"+eol);
        buf.append(" 1) Arrival Port(s): ${allPort}"+eol);
        buf.append(" 2) ETA: ${eta} (${dPort1})"+eol);
        buf.append("    ETD: ${etd} (${dPort1})"+eol);
        if(eta2 != null) {
            buf.append(" 2) ETA: ${eta2} (${dPort2})"+eol);
            buf.append("    ETD: ${etd2} (${dPort2})"+eol);

        }
        buf.append(" 3) ${vesselName} (unmanned barge) "+eol);
        buf.append(" 4) USA "+eol);
        buf.append(" 5) N/A "+eol);
        buf.append(" 6) ABS, official nbr. ${abs}"+eol);
        buf.append("10) ETD: $sailDate "+eol);
        buf.append("    Last Port: ${arrPort}"+eol);
        buf.append("11) ${contact}"+eol);
        buf.append("12) Dock-side as noted in NOD"+eol);
        buf.append("13) as noted in NOD"+eol);
        buf.append("14) as noted in NOD"+eol);
        buf.append("15) as noted in NOD"+eol);
        buf.append("16) Containerized cargo, freight all kinds."+eol);
        buf.append("17) Equipment operational per 164.35"+eol);
        buf.append("18) Unmanned barge, no crew"+eol);
        buf.append("19) Unmanned barge, no personnel on board"+eol);
        buf.append(eol);

        return buf.toString();
    }

    public String generateDCM(VesselVisitDetails vv) {
        StringBuffer dcm = new StringBuffer();
        String pageLine = "    DCM DATA FOR USCG OUTBOUND FROM Honolulu BY DESCRIPTION     Page ";
        int page = 1;
        int pagesize = 18; // (62-8)/3;
        int cnt = 0;
        StringBuffer header = new StringBuffer();
        header.append("                        ");
        header.append(vv.vvdVessel.vesName);
        header.append(" Voyage: ");
        header.append(vv.vvdObVygNbr);
        header.append("\n\n                        Official Number: ");
        header.append(getABS(vv.vvdVessel.vesId));
        header.append("\n============================================================================"+eol);
        header.append(" Description"+eol);
        header.append(" Pkg No. Stowage Weight  Container    Dport Shipment#  Shipper"+eol);
        header.append(" ------- ------- ------- ------------ ----- ---------- ---------------------"+eol);

        dcm.append(pageLine);
        dcm.append(page);
        dcm.append(eol);
        dcm.append(header);
        page++;

        // todo, sort order.
        List units = getUnitFinder().findAllUfvOnBoard(vv.cvdCv);
        if(units == null) return dcm.toString;

        ArrayList hazardList = new ArrayList();
        ArrayList cdcHazardList = null;
        Iterator iter = units.iterator();
        while(iter.hasNext() ) {
            def ufv = iter.next();
            //if(ufv.ufvUnit.getUnitGoods() == null) continue;
            if(ufv.ufvUnit.getUnitGoods().getGdsHazards() == null) continue;
            Iterator hazardIter = ufv.ufvUnit.getUnitGoods().getGdsHazards().getHazardItemsIterator();
            cdcHazardList = new ArrayList();
            while(hazardIter.hasNext()) {
                def hazard = hazardIter.next();
                def imdgClass = hazard.getHzrdiImdgCode() != null ? hazard.getHzrdiImdgCode().getKey() : null
                def nbrType = hazard.getHzrdiNbrType() != null ? hazard.getHzrdiNbrType().getKey() : null
                boolean isUnitCdc = isUnitCdc(imdgClass,hazard.hzrdiUNnum,nbrType,hazard.hzrdiWeight)
                if(isUnitCdc){
                    cdcHazardList.add(hazard) //A2 Added for Xml
                    HazardItemHolder item = new HazardItemHolder(ufv,hazard,classMap,unNaMap);
                    hazardList.add(item);
                }
            }
            //105
            cdcHazmap.put(ufv,cdcHazardList)
        }
        //println("cdcHazmap ==================="+(cdcHazmap != null ? cdcHazmap.size() : "--------NULL-------"));
        Collections.sort(hazardList);  //Dont Need This sorting anymore




        iter = hazardList.iterator();
        while(iter.hasNext() ) {
            def item = iter.next();
            def ufv = item.ufv;
            def hazard = item.hazard;
            if(cnt == pagesize) {
                dcm.append(pageLine);
                dcm.append(page);
                dcm.append(eol);
                dcm.append(header);
                page++;
                cnt = 1;
            } else {
                cnt ++;
            }
            dcm.append(formatDcmLine(ufv,hazard));

        }

        return dcm.toString();

    }

    private String formatDcmLine(ufv,hazard) {
        StringBuffer buf = new StringBuffer();
        buf.append(description(hazard));
        buf.append(eol);
        buf.append(" ");
        // Pkg No.
        buf.append(pad(hazard.hzrdiQuantity,hazard.hzrdiPackageType,7));
        buf.append(" ");
        //Stowage
        if(ufv.getUfvLastKnownPosition() != null) {
            buf.append(pad(ufv.getUfvLastKnownPosition().posSlot,7));
        } else {
            buf.append(pad("",7));
        }
        buf.append(" ");
        // Weight
        buf.append(padWeight(hazard.hzrdiWeight,7));
        buf.append(" ");
        // Container
        buf.append(pad(ufv.ufvUnit.unitId,12));
        buf.append(" ");
        // Dport
        buf.append(pad(ufv.getFieldValue("ufvUnit.unitRouting.rtgPOD1.pointId"),5));
        buf.append(" ");
        // Shipment #
        buf.append(pad(ufv.ufvUnit.getUnitGoods().gdsBlNbr,10));
        buf.append(" ");
        // Shipper
        buf.append(pad(ufv.ufvUnit.getUnitGoods().getFieldValue("gdsShipperBzu.bzuName"),21));
        buf.append(eol);
        buf.append(eol);
        return buf;


    }

    private String pad(Object data1, Object data2, int size) {
        if(data1 == null) data1 = "";
        else data1 = data1.toString()+" ";
        if(data2 == null) data2 = "";
        pad(data1+data2,size);

    }

    private String pad(String data, int size) {
        if(data == null) data = "";
        StringBuffer buf = new StringBuffer();
        buf.append(data);
        if(buf.length() > size) {
            buf.setLength(size);
        } else {
            while(buf.length() < size) buf.append(" ");
        }
        return buf.toString();

    }

    private String padWeight(Double weight, int size) {
        String result = "";
        try {
            double weightDbl = weight.doubleValue();
            weightDbl = weightDbl/2.20462262;
            result = Math.round(weightDbl)+"LB";
        } catch (Exception e) {
        }
        return pad(result,size);

    }

    private String description(hazard) {
        StringBuffer buf = new StringBuffer();
        String properName = hazard.hzrdiProperName;
        String techName   = hazard.hzrdiTechName;
        String imdgclass  = hazard.hzrdiImdgClass.name;
        def im01       = hazard.hzrdiSecondaryIMO1;
        def im02       = hazard.hzrdiSecondaryIMO2;
        String un         = hazard.hzrdiUNnum;
        String nbrType = hazard.getHzrdiNbrType() != null ? hazard.getHzrdiNbrType().getKey() : null
        def pkg           = hazard.hzrdiPackingGroup;
        if(pkg != null) pkg = ", PG"+pkg.name;
        else pkg = "";
        def flashPoint    = hazard.hzrdiFlashPoint;
        def limited	     = hazard.hzrdiLtdQty ? ", LTD QTY" : "";
        def marine        = hazard.hzrdiMarinePollutants ? ", MARINE POLLUTANTS" : "";


        buf.append(" ");
        if(properName == null ) properName = "";
        buf.append(properName);
        if(techName != null ) {
            buf.append(", ");
            buf.append(techName);
        }
        buf.append(", ");
        buf.append(imdgclass);
        if(im01 != null || im02 != null) buf.append(",(");
        if(im01 != null) buf.append(im01.name);
        if(im02 != null) {
            buf.append(", ");
            buf.append(im02.name);
        }
        if(im01 != null || im02 != null) buf.append(")");
        if(un != null) {
            buf.append(", "+nbrType);
            buf.append(un);
        }
        buf.append(pkg);
        if(flashPoint != null) {
            buf.append(", F/P-");
            buf.append(flashPoint);
            buf.append("C");
        }
        buf.append(limited);
        buf.append(marine);

        return buf;


    }

    public  String getEnvVersion()  {
        String envType = groovyApi.getReferenceValue("ENV", "ENVIRONMENT", null, null, 1)
        if("PRODUCTION".equals(envType)){
            return "";
        }
        return envType+" ";
    }

    //----- CDC CODE -----------------
    public void readCdcSpreadsheet()
    {
        try{
            ByteArrayResource byteResource  = new ByteArrayResource(DigitalAsset.findImage("CDC"));
            InputStream inputStream = byteResource.getInputStream()
            DataInputStream dstream = new DataInputStream(inputStream);
            BufferedReader br = new BufferedReader(new InputStreamReader(dstream));
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if(count != 0) { processLine(line,count) }
                count++;
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //process each line of code
    public void processLine(String msg, int lineNum)
    {
        try{
            String[] cdcColValues = msg.split(",");

            if(cdcColValues[0] != null && cdcColValues[0].trim().length() > 0){
                String ImdgClass = cdcColValues[0];
                String classType = cdcColValues[2] != null && cdcColValues[2].trim().length() > 0 ? cdcColValues[2] : "NA";
                classMap.put(ImdgClass, classType);
            }else if(cdcColValues[1] != null && cdcColValues[1].trim().length() > 0){
                String unNa = cdcColValues[1];
                String unNaType = cdcColValues[2] != null && cdcColValues[2].trim().length() > 0 ? cdcColValues[2] : "NA";
                unNaMap.put(unNa, unNaType);
            }

            //Populate Map for material type
            if(lineNum == 1 || lineNum == 2 || lineNum ==3)
            {
                if(lineNum == 2 && (cdcColValues[13] != null && cdcColValues[13].trim().length() > 0)
                        && (cdcColValues[16] != null && cdcColValues[16].trim().length() > 0)){
                    typweightMap.put(cdcColValues[13], cdcColValues[16]);
                }
                else if((cdcColValues[12] != null && cdcColValues[12].trim().length() > 0)
                        && (cdcColValues[15] != null && cdcColValues[15].trim().length() > 0))
                {
                    typweightMap.put(cdcColValues[12], cdcColValues[15]);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //Check if Unit is a CDC unit
    public boolean isUnitCdc(String hazImdg,String unNum,String nbrType,Double hazWeight)
    {
        String imdg = hazImdg;//get unit class
        String un = nbrType+unNum; //hard coding the UN will need to ADD NA
        Double dblWeight = hazWeight != null ? hazWeight*2.20462262 : null;
        long weight = dblWeight != null ? Math.round(dblWeight.doubleValue()) : 0
        boolean isUnitCdc = false;
        try{

            if(imdg != null && classMap.containsKey(imdg)){
                return isUnitCdc = true;
            }else if(unNum != null && "1.5D".equalsIgnoreCase(unNum)){
                return isUnitCdc = true;
            }
            else if(un != null && unNaMap.containsKey(un))
            {
                if("Permit".equalsIgnoreCase(unNaMap.get(un))){
                    return isUnitCdc = true;
                }
                else if(unNaMap.get(un) != null && hazWeight == null){
                    return isUnitCdc = true;
                }
                else{
                    String unNaType = unNaMap.get(un) != null ? (String)unNaMap.get(un) : null;
                    isUnitCdc = unNaType != null ? weight >= Long.parseLong(typweightMap.get(unNaType).toString())  : false;
                    return isUnitCdc;
                }
            }//Else if ends
        }catch(Exception e){
            e.printStackTrace();
        }
        return isUnitCdc;
    }


    //A1 - ADDING CODE FOR NOA XML TO USCG
    public String populateCDCList(){
        //cdcHazmap  -  we can add map
        //A8
        def gvyCmisCrsUtil = getGroovyClassInstance("GvyCmisCargoStatusUtil");

        //ArrayList cdcList = cdcHazardList;
        ArrayList cdcList = null;
        def aUfv = null;  def dest = null;
        String cdcXml = null;
        StringBuffer strCdcBuff = new StringBuffer()
        //Removed the Arrival xml
        //cdcXml = "<CARGO><GENERAL_DESC>Containerized Cargo Vessel - "+vesselName+"</GENERAL_DESC><CDC_ON_BOARD>Yes</CDC_ON_BOARD><CARGO>Yes</CARGO></CARGO><CDC_LIST>";
        //Collections.sort(cdcList);
        def writer = new StringWriter();
        def xml = new groovy.xml.MarkupBuilder(writer);
        xml.CARGO(){
            GENERAL_DESC("Containerized Cargo Vessel - "+vesselName)
            CDC_ON_BOARD('Yes')
            CARGO('Yes')
        }
        writer.toString()

        xml.CDC_LIST(){
            Iterator it1 = cdcHazmap.keySet().iterator(); //A5
            while (it1.hasNext())
            {
                aUfv = it1.next();
                cdcList = cdcHazmap.get(aUfv)
                Iterator iter = cdcList.iterator();
                while(iter.hasNext() ) {
                    def item = iter.next();
                    String imdgclass  = item.hzrdiImdgClass.name;
                    String hazProper = item.hzrdiProperName != null ? gvyCmisCrsUtil.replaceQuotesUtil(item.hzrdiProperName) : ''
                    String un = item.hzrdiUNnum;
                    String nbrType = item.getHzrdiNbrType() != null ? item.getHzrdiNbrType().getKey() : null
                    String weight = item.hzrdiWeight
                    dest = aUfv.ufvUnit.unitGoods.gdsDestination

                    String cdcClass = " ";
                    if (imdgclass.contains("1.1") || imdgclass.contains("1.2")) {
                        cdcClass = "A";
                    } else if (imdgclass.contains("1.5")) {
                        cdcClass = "B";
                    } else if (imdgclass.contains("2.3")) {
                        cdcClass  = "C"
                    } else if (imdgclass.contains("5.1")) {
                        cdcClass  = "D"
                    } else if (imdgclass.contains("6.1")) {
                        cdcClass = "E";
                    } else {
                        cdcClass = " "; //no class can be determined
                    }

                    xml.CDC(){
                        CDC_NAME(imdgclass+" "+hazProper)
                        UN_NUMBER(nbrType+un)
                        AMT(weightFromKgToLB(weight)+" LB")
                        DESTINATION_COUNTRY("UNITED STATES")
                        DESTINATION_COUNTRY_CODE("US")
                        DESTINATION_STATE("Hawaii")
                        DESTINATION_PORT_NAME(getPortName(dest))
                        DESTINATION_PORT_CODE()
                        DESTINATION_PLACE(getPortName(dest)+", HI")
                        CDC_CLASS(cdcClass)
                        PACKAGED("Yes")
                        RESIDUE("Yes")
                    }



                    /*String aCdcItem = "<CDC><CDC_NAME>"+imdgclass+" "+hazProper+"</CDC_NAME> "+  //A8
                      "<UN_NUMBER>"+nbrType+un+"</UN_NUMBER>"+
                       "<AMT>"+weightFromKgToLB(weight)+" LB</AMT> "+
                       "<DESTINATION_COUNTRY>UNITED STATES</DESTINATION_COUNTRY> "+
                       "<DESTINATION_COUNTRY_CODE>US</DESTINATION_COUNTRY_CODE>"+
                        "<DESTINATION_STATE>Hawaii</DESTINATION_STATE>"+
                       "<DESTINATION_PORT_NAME>"+getPortName(dest)+"</DESTINATION_PORT_NAME>"+
                        "<DESTINATION_PORT_CODE></DESTINATION_PORT_CODE>"+
                       "<DESTINATION_PLACE>"+getPortName(dest)+", HI"+"</DESTINATION_PLACE> "+
                     "<CDC_CLASS>" +
                     cdcClass +
                     "</CDC_CLASS><PACKAGED>Yes</PACKAGED><RESIDUE>Yes</RESIDUE></CDC>";

                       strCdcBuff.append(aCdcItem); */
                }// Inner while Ends

            }//Outer while Ends
        } // CD_LIST tag ends
        xml.PREVIOUS_FOREIGN_PORT_LIST{
        }
        writer.toString()
        //cdcXml= cdcXml+strCdcBuff.toString()+"</CDC_LIST><PREVIOUS_FOREIGN_PORT_LIST/> ";
        //return cdcXml;
    }


    public String populateCrewlist(){
        crewListXml = procCrewList(vesVisit)
        return crewListXml;
    }


    public String populateNoCrewList(){
        def writer = new StringWriter();
        def xml = new groovy.xml.MarkupBuilder(writer);
        xml.NONCREW_LIST(){
        }
        writer.toString()
    }

    public String populateVesselInfo(){

        String vesName = vesVisit.vvdVessel.vesNotes
        String callsign = getRadioCallSign(vesVisit.vvdVessel.vesNotes)
        String imoNbr = getOfficialNbr(vesVisit.vvdVessel.vesNotes);


        def writer = new StringWriter();
        def xml = new groovy.xml.MarkupBuilder(writer);
        xml.VESSEL(){
            NAME(vesName)
            CALL_SIGN(callsign)
            ID_NUM(imoNbr)
            ID_TYPE('Official Number')
            FLAG('UNITED STATES')
            FLAG_CODE('US')
            OWNER('Matson Navigation Co.')
            OPERATOR('Matson Navigation Company')
            CLASS_SOCIETY('American Bureau of Shipping')
            CHARTERER('None')
            xml.REPORTING_PARTY(){
                NAME('Matson TOS Application')
                COMPANY('Matson Navigation Co.')
                RELATIONSHIP()
                PHONE('808-848-8382')
                FAX('808-841-4502')
                EMAIL(emailFrom)
            }
            xml.ISSC(){
                ISSUED_DT('2007-11-18')
                VSP_IMPLEMENTATION('Yes')
                ISSC_TYPE('Coast Guard Approved VSP')
                INTERIM_ISSC()
                CSO_NAME('Roger Franz')
                CSO_EMAIL('rfranz@matson.com')
                CSO_PHONE('1-510-507-2043')
                CSO_FAX('1-510-628-7344')
            }
            xml.VESSEL_LOC(){
                LOCATION_DESC('Honolulu, Hawaii')
            }
            OCE('Operational')
            OCE_DESC()
            CVSSA_ONBOARD('No')
            NON_TANK_VESSEL_RESPONSE_PLAN('No')
            LONGSHOREMAN_WORK_DECLARATION('NOT PROVIDED')
        }
        writer.toString()

        /*
        Raghu Iyer : 03/04/2014 Removed these 2 nodes as per the TT - EP000202148
                                xml.COMP_CERT(){
                                        ISSUED_DT('2008-09-26')
                                        EXPIRATION_DT('2013-10-26')
                                        AGENCY('American Bureau of Shipping')
                                }
                                xml.SFTYMGMT_CERT(){
                                        ISSUED_DT('2008-09-26')
                                        EXPIRATION_DT('2013-10-13')
                                        AGENCY('American Bureau of Shipping')
                                }
        */

        /*String vesselInfo = "<VESSEL>"+
      "<NAME>"+vesName+"</NAME>"+
      "<CALL_SIGN>"+callsign+"</CALL_SIGN>"+
      "<ID_NUM>"+imoNbr+"</ID_NUM>"+
      "<ID_TYPE>Official Number</ID_TYPE> "+
      "<FLAG>UNITED STATES</FLAG> "+
      "<FLAG_CODE>US</FLAG_CODE>"+
      "<OWNER>Matson Navigation Co.</OWNER> "+
      "<OPERATOR>Matson Navigation Company</OPERATOR> "+
      "<CLASS_SOCIETY>American Bureau of Shipping</CLASS_SOCIETY> "+
      "<CHARTERER>None</CHARTERER>"+

        "<REPORTING_PARTY>"+  //A101 - Reporting party
         "<NAME>Matson TOS Application</NAME> "+
         "<COMPANY>Matson Navigation Co.</COMPANY> "+
         "<RELATIONSHIP></RELATIONSHIP>"+
         "<PHONE>808-848-8382</PHONE>"+
         "<FAX>808-841-4502</FAX>"+
         "<EMAIL>"+emailFrom+"</EMAIL> "+
       "</REPORTING_PARTY>"+

         "<COMP_CERT>"+
        "<ISSUED_DT>2008-09-26</ISSUED_DT>"+
        "<EXPIRATION_DT>2013-10-26</EXPIRATION_DT>"+
        "<AGENCY>American Bureau of Shipping</AGENCY>"+
      "</COMP_CERT>"+

         "<SFTYMGMT_CERT>"+
        "<ISSUED_DT>2008-09-26</ISSUED_DT>"+
        "<EXPIRATION_DT>2013-10-13</EXPIRATION_DT>"+
        "<AGENCY>American Bureau of Shipping</AGENCY>"+
      "</SFTYMGMT_CERT>"+

         "<ISSC>"+
      "<ISSUED_DT>2007-11-18</ISSUED_DT>"+
      "<VSP_IMPLEMENTATION>Yes</VSP_IMPLEMENTATION>"+
      "<ISSC_TYPE>Coast Guard Approved VSP</ISSC_TYPE>"+
      "<INTERIM_ISSC></INTERIM_ISSC>"+
      "<CSO_NAME>Roger Franz</CSO_NAME> "+
      "<CSO_EMAIL>rfranz@matson.com</CSO_EMAIL>"+
      "<CSO_PHONE>1-510-507-2043</CSO_PHONE>"+
      "<CSO_FAX>1-510-628-7344</CSO_FAX>"+
      "</ISSC>"+

         "<VESSEL_LOC>"+
           "<LOCATION_DESC>Honolulu, Hawaii</LOCATION_DESC> "+
              "</VESSEL_LOC>"+
      "<OCE>Operational</OCE><OCE_DESC/>"+
      "<CVSSA_ONBOARD>No</CVSSA_ONBOARD>"+
    "<NON_TANK_VESSEL_RESPONSE_PLAN>Yes</NON_TANK_VESSEL_RESPONSE_PLAN>

         </VESSEL>"*/

        //return vesselInfo
    }

/*	public String populateDepartureNotice()
	{
	 def util = util == null ? getGroovyClassInstance("GvyEventUtil") : util;
     def zone = zone == null ? vesVisit.cvdCv.cvComplex.getTimeZone() : zone;
	 String createdDate = util.formatDateTime(new Date(), zone, "yyyy-MM-dd'T'HH:mm:ss")
     String voyageNbr = vesVisit.vvdObVygNbr


	 String notice = "<NOTICE_DETAILS>"+
      "<RECEIVED_DATE_TIME xsi:nil='true' />"+
      "<NOTICE_ID />"+
      "<NOTICE_TRANSACTION_TYPE>Initial</NOTICE_TRANSACTION_TYPE>"+
      "<NOTICE_TYPE>Departure</NOTICE_TYPE>"+
      "<VERSION>3.1</VERSION>"+
    "</NOTICE_DETAILS>"+
    "<VOYAGE>"+
       "<VOYAGE_TYPE>US to US</VOYAGE_TYPE>"+
       "<VOYAGE_NUMBER>"+voyageNbr+"</VOYAGE_NUMBER>"+
     "<CLOSED_LOOP_VOYAGE>Yes</CLOSED_LOOP_VOYAGE></VOYAGE>";
	 return notice;
	} A2 */

    public String populateArrivalNotice()
    {
        def util = util == null ? getGroovyClassInstance("GvyEventUtil") : util;
        def zone = zone == null ? vesVisit.cvdCv.cvComplex.getTimeZone() : zone;
        String createdDate = util.formatDateTime(new Date(), zone, "yyyy-MM-dd'T'HH:mm:ss")
        String voyageNbr = vesVisit.vvdObVygNbr

        def writer = new StringWriter();
        def xml = new groovy.xml.MarkupBuilder(writer);
        xml.NOTICE_DETAILS(){
            NOTICE_ID('2aa176b8-f20b-4518-9bce-684a1156eb1b')
            NOTICE_TRANSACTION_TYPE('Update')
            NOTICE_TYPE('Arrival')
            VERSION('3.3')
        }
        xml.VOYAGE() {
            VOYAGE_TYPE('US to US')
            VOYAGE_NUMBER(voyageNbr)
            CLOSED_LOOP_VOYAGE('Yes')
        }
        writer.toString()
    }

    public String populateArrDetails(String type){

        String tempArrDate = util.convertTimeZone(eta, "HST","EST", "MM/dd/yyyy @ HH:mm");
        String arrDate = util.formatDate(tempArrDate,"MM/dd/yyyy @ HH:mm","yyyy-MM-dd")
        String arrTime = util.formatDate(tempArrDate,"MM/dd/yyyy @ HH:mm","HH:mm:ss")

        String tempSailDt = util.convertTimeZone(sailDate, "HST","EST", "MM/dd/yyyy @ HH:mm");
        sailDeptdate = util.formatDate(tempSailDt,"MM/dd/yyyy @ HH:mm","yyyy-MM-dd")
        String sailtime = util.formatDate(tempSailDt,"MM/dd/yyyy @ HH:mm","HH:mm:ss")

        String tempDeptDate = util.convertTimeZone(etd, "HST","EST", "MM/dd/yyyy @ HH:mm");
        String deptDate = util.formatDate(tempDeptDate,"MM/dd/yyyy @ HH:mm","yyyy-MM-dd")
        String deptTime = util.formatDate(tempDeptDate,"MM/dd/yyyy @ HH:mm","HH:mm:ss")

        String arrivalPort = null;
        String	tempPort = dPort1

        /* if(eta2 != null) {
          String tempArrDate2 = util.convertTimeZone(eta2, "HST","EST", "MM/dd/yyyy @ HH:mm");
           arrDate = util.formatDate(tempArrDate2,"MM/dd/yyyy @ HH:mm","yyyy-MM-dd")
          arrTime = util.formatDate(tempArrDate2,"MM/dd/yyyy @ HH:mm","HH:mm:ss")

          String tempDeptDate2 = util.convertTimeZone(etd2, "HST","EST", "MM/dd/yyyy @ HH:mm");
          deptDate = util.formatDate(tempDeptDate2,"MM/dd/yyyy @ HH:mm","yyyy-MM-dd")
          deptTime = util.formatDate(tempDeptDate2,"MM/dd/yyyy @ HH:mm","HH:mm:ss")

           tempPort = dPort2
         }*/
        arrivalPort = getPortName(tempPort);
        println("arrDate="+arrDate+"  arrTime="+arrTime+" sailDate="+sailDate+"  deptDate="+deptDate+"  deptTime="+deptTime)
        String arrivalDeparture =  null;
        def currentFcy = vesVisit.cvdCv.cvFacility.fcyId


        def lstportArrDate = util.formatDateTime(vesVisit.cvdCv.cvATD, zone);
        String tempLastArrDate = util.convertTimeZone(lstportArrDate, "HST","EST", "MM/dd/yyyy @ HH:mm");
        String lstArrDate = util.formatDate(tempLastArrDate,"MM/dd/yyyy @ HH:mm","yyyy-MM-dd")

        String tempPort1 = getPortName(arrPort)
        String netxport1 = "";
        String netxport2 = "";
        String netxport = "";

        if('Arrival'.equalsIgnoreCase(type)){
            def writer = new StringWriter();
            def xml = new groovy.xml.MarkupBuilder(writer);
            xml.ARRIVE_DEPART(){
                xml.ARRIVE(){
                    ARRIVE_DT(arrDate)
                    ARRIVE_TIME(arrTime)
                    PORT_OR_PLACE(getPortName(arrivalPort))
                    CITY(getPortName(arrivalPort))
                    STATE('Hawaii')
                    FACILITY('Matson')
                    ANCHORAGE()
                }
                xml.DEPART(){
                    DEPART_DT(deptDate)
                    DEPART_TIME(deptTime)
                    PORT_OR_PLACE(getPortName(arrivalPort))
                    CITY(getPortName(arrivalPort))
                    STATE('Hawaii')
                }
                xml.NEXT_PORT(){
                    ARRIVE_DT ('xsi:nil':'true')
                    ARRIVE_TIME ('xsi:nil':'true')
                    NEXT_PORT_COUNTRY()
                    NEXT_PORT_COUNTRY_CODE()
                    NEXT_PORT_STATE()
                    NEXT_PORT_NAME()
                    NEXT_PORT_CODE()
                    NEXT_PORT_PLACE()
                }
                xml.LAST_PORT(){
                    ARRIVE_DT(lstArrDate)   //Change
                    DEPARTURE_DT(sailDeptdate)    //Change
                    LAST_PORT_COUNTRY('UNITED STATES')
                    LAST_PORT_COUNTRY_CODE('US')
                    LAST_PORT_STATE('Hawaii')
                    LAST_PORT_NAME(getPortName(tempPort1))
                    LAST_PORT_CODE(tempPort1)
                    LAST_PORT_PLACE(getPortName(tempPort1))
                }
                xml.CONTACT(){
                    NAME('Dave Thompson')  //A14
                    COMPANY('Matson Navigation Co.')
                    PHONE('1-808-848-1258')
                    EMAIL('dwthompson@matson.com')  //A14
                }

            }
            writer.toString()
        }
        else {

            def writer = new StringWriter();
            def xml = new groovy.xml.MarkupBuilder(writer);
            xml.ARRIVE_DEPART(){
                xml.NEXT_PORT(){
                    ARRIVE_DT ('xsi:nil':'true')
                    ARRIVE_TIME ('xsi:nil':'true')
                    NEXT_PORT_COUNTRY()
                    NEXT_PORT_COUNTRY_CODE()
                    NEXT_PORT_STATE()
                    NEXT_PORT_NAME()
                    NEXT_PORT_CODE()
                    NEXT_PORT_PLACE()
                }
                xml.LAST_PORT(){
                    ARRIVE_DT(lstArrDate)   //Change
                    DEPARTURE_DT(sailDeptdate)    //Change
                    LAST_PORT_COUNTRY('UNITED STATES')
                    LAST_PORT_COUNTRY_CODE('US')
                    LAST_PORT_STATE('Hawaii')
                    LAST_PORT_NAME(getPortName(tempPort1))
                    LAST_PORT_CODE(tempPort1)
                    LAST_PORT_PLACE(getPortName(tempPort1))
                }
                xml.CONTACT(){
                    NAME('Dave Thompson')   //A14
                    COMPANY('Matson Navigation Co.')
                    PHONE('1-808-848-1258')
                    EMAIL('dwthompson@matson.com')  //A14
                }

            }
            writer.toString()
        }

        /*arrivalDeparture =  "<ARRIVE>"+
           "<ARRIVE_DT>"+arrDate+"</ARRIVE_DT>"+ //A01
           "<ARRIVE_TIME>"+arrTime+"</ARRIVE_TIME>"+
           "<PORT_OR_PLACE>"+getPortName(arrivalPort)+"</PORT_OR_PLACE>"+
           "<CITY>"+getPortName(arrivalPort)+"</CITY>"+
           "<STATE>Hawaii</STATE>"+
           "<FACILITY>Matson</FACILITY>"+
           "<ANCHORAGE></ANCHORAGE>"+
          "</ARRIVE>"+
          "<DEPART>"+
           "<DEPART_DT>"+deptDate+"</DEPART_DT>"+
           "<DEPART_TIME>"+deptTime+"</DEPART_TIME>"+
           "<PORT_OR_PLACE>"+getPortName(arrivalPort)+"</PORT_OR_PLACE> "+
           "<CITY>"+getPortName(arrivalPort)+"</CITY>"+
           "<STATE>Hawaii</STATE>"+
          "</DEPART>";
        }else if('Departure'.equalsIgnoreCase(type)){
            arrivalDeparture =  "<ARRIVE>"+
           "<ARRIVE_DT>"+arrDate+"</ARRIVE_DT>"+ //A01
           "<ARRIVE_TIME>"+arrTime+"</ARRIVE_TIME>"+
          "<PORT_OR_PLACE/><CITY/><STATE/><FACILITY/><PLACE/><ANCHORAGE/>"+
          "</ARRIVE>"+
          "<DEPART>"+
           "<DEPART_DT>"+sailDeptdate+"</DEPART_DT>"+
           "<DEPART_TIME>"+sailtime+"</DEPART_TIME>"+
           "<PORT_NAME>"+getPortName(currentFcy)+"</PORT_NAME >"+
           "<CITY>"+getPortName(currentFcy)+"</CITY>"+
           "<STATE>Hawaii</STATE>"+
           "<PLACE>"+getPortName(currentFcy)+"</PLACE>"+
          "</DEPART>";
        }*/

        //Hon Arrival
    }



    //Departure XML
    /*   public String getDepartureNotice()
       {
        def util = util == null ? getGroovyClassInstance("GvyEventUtil") : util;
        def zone = zone == null ? vesVisit.cvdCv.cvComplex.getTimeZone() : zone;
        String createdDate = util.formatDateTime(new Date(), TimeZone.getTimeZone('America/New_York'), "yyyy-MM-dd'T'HH:mm:ss")
        String voyageNbr = vesVisit.vvdObVygNbr


        String notice = "<NOTICE_DETAILS>"+
         "<CREATED_DATE_TIME>"+createdDate+"</CREATED_DATE_TIME>"+
         "<RECEIVED_DATE_TIME xsi:nil='true' />"+
         "<NOTICE_ID />"+
         "<NOTICE_TRANSACTION_TYPE>Initial</NOTICE_TRANSACTION_TYPE>"+
         "<NOTICE_TYPE>Departure</NOTICE_TYPE>"+
         "<VERSION>3.1</VERSION>"+
       "</NOTICE_DETAILS>"+
       "<VOYAGE>"+
          "<VOYAGE_TYPE>US to US</VOYAGE_TYPE>"+
          "<VOYAGE_NUMBER>"+voyageNbr+"</VOYAGE_NUMBER>"+
        "</VOYAGE>";
        return ;
       }
   */

    public String populateLastNextPort(String arrDate,String deptDate,String arrPort){
        String tempPort = getPortName(arrPort)
        //arrPort = tempPort
        String netxport1 = "";
        String netxport2 = "";
        String netxport = "";
        /*if(eta2 != null) {
            buf.append(" 2) ETA: ${eta2} (${dPort2})"+eol);
            buf.append("    ETD: ${etd2} (${dPort2})"+eol);
          }	*/

        /* -- 05.09.11 - to handel posting ---
        if(eta != null){
          String noticeArrDate = util.formatDate(eta,TimeZone.getTimeZone('America/New_York'),"yyyy-MM-dd")
          String noticeArrTime = util.formatDate(eta,TimeZone.getTimeZone('America/New_York'),"HH:mm:ss")
          String tempNextPort = getPortName(dPort1);

           netxport1 = "<NEXT_PORT>"+
         "<ARRIVE_DT>"+noticeArrDate+"</ARRIVE_DT>"+
          "<ARRIVE_TIME>"+noticeArrTime+"</ARRIVE_TIME>"+
          "<NEXT_PORT_COUNTRY>US</NEXT_PORT_COUNTRY>"+
         "<NEXT_PORT_COUNTRY_CODE>US</NEXT_PORT_COUNTRY_CODE>"+
         "<NEXT_PORT_STATE>Hawaii</NEXT_PORT_STATE>"+
         "<NEXT_PORT_NAME>"+getPortName(tempNextPort)+"</NEXT_PORT_NAME>"+
         "<NEXT_PORT_CODE>"+tempNextPort+"</NEXT_PORT_CODE>"+
         "<NEXT_PORT_PLACE>"+getPortName(tempNextPort)+"</NEXT_PORT_PLACE>"+
        "</NEXT_PORT>";
        }

        if(eta2 != null){
          String noticeArrDate = util.formatDate(eta2,TimeZone.getTimeZone('America/New_York'),"yyyy-MM-dd")
          String noticeArrTime = util.formatDate(eta2,TimeZone.getTimeZone('America/New_York'),"HH:mm:ss")
          String tempNextPort = getPortName(dPort2);

           netxport2 = "<NEXT_PORT>"+
         "<ARRIVE_DT>"+noticeArrDate+"</ARRIVE_DT>"+
          "<ARRIVE_TIME>"+noticeArrTime+"</ARRIVE_TIME>"+
          "<NEXT_PORT_COUNTRY>US</NEXT_PORT_COUNTRY>"+
         "<NEXT_PORT_COUNTRY_CODE>US</NEXT_PORT_COUNTRY_CODE>"+
         "<NEXT_PORT_STATE>Hawaii</NEXT_PORT_STATE>"+
         "<NEXT_PORT_NAME>"+getPortName(tempNextPort)+"</NEXT_PORT_NAME>"+
         "<NEXT_PORT_CODE>"+tempNextPort+"</NEXT_PORT_CODE>"+
         "<NEXT_PORT_PLACE>"+getPortName(tempNextPort)+"</NEXT_PORT_PLACE>"+
        "</NEXT_PORT>";
        }
    */
        //if(eta == null && eta2 == null){
        netxport = "<NEXT_PORT>"+
                "<ARRIVE_DT xsi:nil='true'></ARRIVE_DT>"+
                "<ARRIVE_TIME xsi:nil='true'></ARRIVE_TIME>"+
                "<NEXT_PORT_COUNTRY></NEXT_PORT_COUNTRY>"+
                "<NEXT_PORT_COUNTRY_CODE></NEXT_PORT_COUNTRY_CODE>"+
                "<NEXT_PORT_STATE></NEXT_PORT_STATE>"+
                "<NEXT_PORT_NAME></NEXT_PORT_NAME>"+
                "<NEXT_PORT_CODE></NEXT_PORT_CODE>"+
                "<NEXT_PORT_PLACE></NEXT_PORT_PLACE>"+
                "</NEXT_PORT>";

        //}

        //Added for Multiple visits - 105
        //netxport = netxport1+netxport2+netxport


        String lastnextport = netxport+"<LAST_PORT>"+
                "<ARRIVE_DT>"+arrDate+"</ARRIVE_DT>"+   //Change
                "<DEPARTURE_DT>"+sailDeptdate+"</DEPARTURE_DT>"+    //Change
                "<LAST_PORT_COUNTRY>UNITED STATES</LAST_PORT_COUNTRY> "+
                "<LAST_PORT_COUNTRY_CODE>US</LAST_PORT_COUNTRY_CODE>"+
                "<LAST_PORT_STATE>Hawaii</LAST_PORT_STATE>"+
                "<LAST_PORT_NAME>"+getPortName(tempPort)+"</LAST_PORT_NAME>"+
                "<LAST_PORT_CODE>"+tempPort+"</LAST_PORT_CODE>"+
                "<LAST_PORT_PLACE>"+getPortName(tempPort)+"</LAST_PORT_PLACE>"+
                "</LAST_PORT>"+
                "<CONTACT>"+   //A101 - The value shall be the name of the 24-hour Contact for the ship.
                "<NAME>Dave Thompson</NAME>"+   //A14
                "<COMPANY>Matson Navigation Co.</COMPANY>"+
                "<PHONE>1-808-848-1258</PHONE>"+
                "<EMAIL>dwthompson@matson.com</EMAIL>"+  //A14
                "</CONTACT>";

        return lastnextport
    }


    public String arrivalNoaXml(){
        println("new amit");
        String arrivalXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><NOTICE xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"USCG_ENOAD_Schema_3.3.xsd\">"+populateArrivalNotice()+populateVesselInfo()+populateArrDetails("Arrival")+populateCDCList()+populateCrewlist()+populateNoCrewList()+"</NOTICE>";
        return arrivalXml;
    }

/*	public String departureNodXml(){
        //A6 - Change this to Be ARRIVAL Notice from Departed Notice
		//String departureXml = "<?xml version='1.0' standalone='yes'?><NOTICE xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"+populateArrivalNotice()+populateVesselInfo()+populateArrDetails("Arrival")+populateCDCList()+populateCrewlist()+populateNoCrewList()+"</NOTICE>";
	    String departureXml = "<?xml version='1.0' standalone='yes'?><NOTICE xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"+populateDepartureNotice()+populateVesselInfo()+populateArrDetails("Arrival")+populateCDCList()+populateCrewlist()+populateNoCrewList()+"</NOTICE>";
		return departureXml;
    } */

    public static String weightFromKgToLB(String kgWeight)
    {
        String lbsWeight = null;
        if(kgWeight == null || kgWeight.trim().length()==0){
            return "0";
        }
        double convtWeight = Double.parseDouble(kgWeight)* 2.20462262;
        long result = Math.round(convtWeight);
        lbsWeight = String.valueOf(result);
        return lbsWeight;
    }



    public String procCrewList(Object vv) {
        def crewName = vv.vvdVessel.vesNotes;
        if(crewName != null ) {
            crewName = crewName.toUpperCase();
        }
        ByteArrayResource byteResource  = null;
        InputStream inputStream = null;
        StringBuffer strBulder = null;
        String position = null;
        def gvyEventUtil = util == null ? getGroovyClassInstance("GvyEventUtil") : util;
        try {
            byteResource  = new ByteArrayResource(DigitalAsset.findImage(crewName));

            inputStream = byteResource.getInputStream()
            ArrayList crewList =  contentReading(inputStream);
            //strBulder = new StringBuffer("<CREW_LIST>");


            def writer = new StringWriter();
            def xml = new groovy.xml.MarkupBuilder(writer);
            xml.CREW_LIST(){
                for(aCrew in crewList){
                    LinkedHashSet crewDetail = (LinkedHashSet)aCrew;
                    LinkedHashSet crewDetail1 = (LinkedHashSet)aCrew;
                    Iterator it2 = crewDetail.iterator();
                    Iterator it3 = crewDetail1.iterator();
                    String tempStr = null;
                    String gender = null;
                    int checkLoop = 0;
                    int count=0;
                    checkLoop = crewDetail.size()
                    println ("checkLoop    " + checkLoop)
                    if (checkLoop > 0)
                    {
                        xml.CREW(){
                            while(it3.hasNext()){
                                String temp = (String)it3.next();
                                if(temp == null || temp.trim().length() == 0){
                                    //Email FSS Team to rectify CREW Information
                                }
                                println("---------COUNT IT3------------:"+count + " ---------- "+temp.trim().length())
                                if (count == 7){
                                    POSITION(temp)
                                    break;
                                }
                                count++;
                            }
                            count = 0;
                            while(it2.hasNext()){
                                String temp = (String)it2.next();
                                if(temp == null || temp.trim().length() == 0){
                                    //Email FSS Team to rectify CREW Information
                                }
                                println("---------COUNT ------------:"+count + " ---------- "+temp.trim().length())
                                if(count ==0){
                                    LAST_NAME(temp)
                                }else if(count == 1){
                                    FIRST_NAME(temp)
                                }else if (count == 2){
                                    temp = gvyEventUtil.formatDate(temp,"MM/dd/yyyy", "yyyy-MM-dd");
                                    BIRTH_DT(temp)
                                }else if (count == 3){
                                    gender = "M".equalsIgnoreCase(temp) ? "Male" : "Female"
                                    GENDER(gender)
                                }else if (count == 4){
                                    NATIONALITY("UNITED STATES")
                                    NATIONALITY_CODE("US")
                                    COUNTRY_RESIDENCE("UNITED STATES")
                                    COUNTRY_RESIDENCE_CODE("US")
                                }else if (count == 5){
                                    ID_TYPE(getPersonId(temp))
                                }else if (count == 6){
                                    ID_NUM(temp)
                                    ID_COUNTRY("UNITED STATES")
                                    ID_COUNTRY_CODE("US")
                                    ID_EXPIRATION_DT('xsi:nil':'true')
                                }
                                //else if (count == 7){
                                //POSITION(temp)
                                //}
                                else if (count == 8){
                                    EMBARK_COUNTRY("UNITED STATES")
                                    EMBARK_COUNTRY_CODE("US")
                                    EMBARK_STATE("Hawaii")
                                    EMBARK_PORT_NAME("Honolulu")
                                    EMBARK_PORT_CODE("HON")
                                    EMBARK_PLACE(temp)
                                }else if (count == 9){
                                    temp = gvyEventUtil.formatDate(temp,"MM/dd/yyyy", "yyyy-MM-dd");
                                    EMBARK_DATE(temp)
                                }
                                count++;
                            }

                            DEBARK_COUNTRY()
                            DEBARK_COUNTRY_CODE()
                            DEBARK_STATE()
                            DEBARK_PORT_NAME()
                            DEBARK_PORT_CODE()
                            DEBARK_PLACE()
                            DEBARK_DATE ('xsi:nil':'true')
                            LONGSHOREMAN_WORK_DECLARATION('NOT PROVIDED')
                        }
                        count=0;
                    }
                }
            }
            writer.toString()



        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //return strBulder.toString();
    }

    private String getPersonId(String idType)
    {
        if("Mariner Document".equals(idType)){
            return "U.S. Merchant Mariner Document";
        }else if("PassPort".equals(idType)){
            return "Passport Number";
        }else if ("Govt Picture ID".equals(idType)){
            return "Govt Issued Picture ID(US)";
        }
    }


    public ArrayList contentReading(InputStream fileInputStream) {
        WorkbookSettings ws = null;
        Workbook workbook = null;
        Sheet s = null;
        Cell[] rowData = null;
        int rowCount = '0';
        int columnCount = '0';
        DateCell dc = null;
        int totalSheet = 0;
        ArrayList crewList = null;
        try {
            ws = new WorkbookSettings();
            ws.setLocale(new Locale("en", "EN"));
            workbook = Workbook.getWorkbook(fileInputStream, ws);

            totalSheet = workbook.getNumberOfSheets();

            //Getting Default Sheet i.e. 0
            s = workbook.getSheet(0);

            //Total Total No Of Rows in Sheet, will return you no of rows that are occupied with some data
            //println("Total Rows inside Sheet:" + s.getRows());
            rowCount = s.getRows();

            //Total Total No Of Columns in Sheet
            //println("Total Column inside Sheet:" + s.getColumns());
            columnCount = s.getColumns();

            LinkedHashSet crewSet = null;
            crewList = new ArrayList();


            for (i in 0..15)
            {
                crewSet = new LinkedHashSet();
                rowData = s.getRow(i);
                if (i > 5 && !CellType.EMPTY.equals(rowData[0].getType()) ) { // the first date column must not null
                    for (j in 0..9) {
                        crewSet.add(rowData[j].getContents());
                    }
                }
                crewList.add(crewSet);
            }

            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (BiffException e) {
            e.printStackTrace();
        }
        return crewList;
    }





}//class GvyNOA ends

class HazardItemHolder implements Comparable{
    public HazardItem hazard;
    public UnitFacilityVisit ufv;
    HashMap classMapHazItm ;
    HashMap unNaMapHazItm ;

    public HazardItemHolder(UnitFacilityVisit ufv, HazardItem hazard,HashMap classMap,HashMap unNaMap) {
        this.hazard = hazard;
        this.ufv = ufv;
        this.classMapHazItm = classMap;
        this.unNaMapHazItm = unNaMap;
    }


    public int hashCode() {
        return getHazClass();
    }

    public boolean equals(Object o) {
        if(compareTo(o) == 0) return true;
        return false;
    }

    public int compareTo(Object o) {
        if(! (o instanceof HazardItemHolder)) return -1;
        HazardItemHolder h = (HazardItemHolder)o;
        int diff = getHazClass() - h.getHazClass();
        if(diff != 0) return diff*100;
        diff = hazard.hzrdiImdgClass.compareTo(h.hazard.hzrdiImdgClass);
        if(diff != 0) return diff*10;

        return ufv.ufvUnit.unitId.compareTo(h.ufv.ufvUnit.unitId);

    }

    private int getHazClass() {
        if(hazard.hzrdiImdgClass != null && classMapHazItm.containsKey(hazard.hzrdiImdgClass)) return 1;
        if(hazard.hzrdiUNnum != null && unNaMapHazItm.containsKey(hazard.hzrdiUNnum)) return 2;
        return 8;
    }

} //End Class HazardItemHolder