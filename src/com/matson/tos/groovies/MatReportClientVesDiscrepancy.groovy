import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import java.text.SimpleDateFormat
import java.text.DateFormat

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit

import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.road.business.model.*;
import java.util.Calendar;

import com.navis.argo.business.model.Facility;
import com.navis.services.business.event.Event;
import com.navis.argo.business.reference.Equipment
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.inventory.InventoryField;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.services.business.event.GroovyEvent;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import com.navis.argo.ArgoConfig;
import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.model.GeneralReference;
//import com.navis.framework.ulc.server.application.controller.form.ShowDeleteFormCommand;
import java.sql.Connection;

import com.navis.edi.EdiEntity;
import com.navis.edi.EdiField
import com.navis.edi.business.entity.EdiTransaction
import com.navis.edi.business.entity.EdiError;
import com.navis.edi.business.atoms.EdiStatusEnum;
import com.navis.edi.business.api.EdiFinder;
import com.navis.edi.business.entity.EdiSession;
import com.navis.framework.portal.UserContext;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.ContextHelper;
import com.navis.edi.business.atoms.EdiMessageDirectionEnum;
import com.navis.edi.business.entity.EdiBatch;
import java.sql.ResultSet;

/*
* Author : Raghu Iyer
* Date Written : 09/16/2013 
* Description: This groovy is used to generate the CVDR
*/

public class MatReportClientVesDiscrepancy extends GroovyInjectionBase
{
    private final String  emailFrom = '1aktosdevteam@matson.com';
    private final String emailTo = "1aktosdevteam@matson.com";
    String inputVesVoy = null;

    def inj = null;


    public void generateReport (GroovyEvent ipEvent, Object api){

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());
        System.out.println("MatReportClientVesDiscrepancy.generateReport Started !" + timeNow);
        String blNumber = null;
        String vesVoy = null;

        EdiSession session = (EdiSession) ipEvent.getEntity();
        Serializable inSessionGkey = (Serializable) session.getEdisessGkey();
        EdiMessageDirectionEnum ediDirection = EdiMessageDirectionEnum.R;
        EdiBatch currentEdiBatch = this.getLatestEdiBatch(inSessionGkey, ediDirection);

        if (currentEdiBatch == null){
            println (" No EDI Batches found");
            println("MatGetStowplanTrankey Ended !" + timeNow);
            return;
        } else {

            println("Batch Number:" + currentEdiBatch.getEdibatchNbr().toString());
            println ("Batch Transaction Count:" + currentEdiBatch.getTransactionCount().toString());
            List<EdiTransaction> tranList = this.getEdiFndr().findTxnForBatch(currentEdiBatch);
            if (tranList.size() == 0) {
                println ("Batch has no transaction;")
                return;
            }

            EdiTransaction trans = tranList.get(0);
            Serializable tranGkey = trans.getEditranGkey();
            blNumber = trans.getEditranPrimaryKeywordValue();
            inputVesVoy = getUnitVesVoy (blNumber);
            this.createReport(inputVesVoy);
        }
        println("MatGetStowplanTrankey Ended !" + timeNow);
    }

    public static EdiBatch getLatestEdiBatch(Serializable inSessionGkey, EdiMessageDirectionEnum inDirection) {
        DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_BATCH)
                .addDqPredicate(PredicateFactory.eq(EdiField.EDIBATCH_SESSION, inSessionGkey))
                .addDqPredicate(PredicateFactory.eq(EdiField.EDIBATCH_DIRECTION, inDirection))
                .addDqOrdering(Ordering.desc(EdiField.EDIBATCH_CREATED));
        List batches = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        return batches != null && !batches.isEmpty() ? (EdiBatch) batches.get(0) : null;
    }

    private EdiFinder getEdiFndr() {
        return (EdiFinder) Roastery.getBean(EdiFinder.BEAN_ID);
    }

    public void createReport(String  reportVesVoy)
    {
        try
        {
            inj = new GroovyInjectionBase();
            def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");
            Connection conn;
            ResultSet rs = null;
            HashMap reportMap = null;
            ArrayList reportGenRefList =  new ArrayList();

            try {
                conn = GvyRefDataLookup.connect();
                rs = GvyRefDataLookup.getDiscrepancyData(reportVesVoy,conn);
                println("GvyRefDataLookup.getDiscrepancyDate Calling");

                while (rs.next()) {
                    reportMap = new HashMap();

                    String vesVoy = rs.getString("VESVOY");
                    String value = rs.getString("VALUE");
                    String unitId = rs.getString("CONTAINER_ID");
                    String type = rs.getString("TYPE_CODE");
                    String seal = rs.getString("SEAL_NBR");
                    String tempReq = rs.getString("TEMP_REQ");
                    String grossWt = rs.getString("GROSS_WT");
                    String tareWt = rs.getString("TARE_WT");
                    String unitVesVoy = rs.getString("UNIT_VESVOY");

                    reportMap.put("UnitNbr", unitId);
                    reportMap.put("UnitFlexString01", vesVoy);
                    reportMap.put("UnitFlexString02", value);
                    reportMap.put("UnitFlexString03", type);
                    reportMap.put("UnitFlexString04", seal);
                    reportMap.put("UnitFlexString05", tempReq);
                    reportMap.put("UnitFlexString06", tareWt);
                    reportMap.put("UnitFlexString07", grossWt);
                    reportMap.put("UnitFlexString08", unitVesVoy);

                    reportGenRefList.add(reportMap);

                    println("Details :::"+vesVoy+" :: "+value+" :: "+unitId+" :: "+type+" :: "+seal+" :: "+tempReq+" :: "+grossWt+" :: "+tareWt+" :: "+unitVesVoy)
                }

                println("reportGenRefList.size() ::: "+reportGenRefList.size());
                if (reportGenRefList.size() > 0) {
                    HashMap parameters = new HashMap();

                    //Create and Mail Report
                    JRDataSource ds = new JRMapCollectionDataSource(reportGenRefList);

                    def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                    reportRunner.emailExcelReport(ds,parameters, "CLIENT_VESSEL_DESC_REPORT",emailTo, "Client Vessel Discrepancy for : " + reportVesVoy  ,"Attached report for CVDR");

                    try {
                        GvyRefDataLookup.delDiscrepancyData(reportVesVoy,conn);
                        println("GvyRefDataLookup.delDiscrepancyData Calling");
                    } catch (Exception e) {
                        println("GvyRefDataLookup.delDiscrepancyData Ended with errors::" + e);
                    }

                    println("reportGenRefList ------- Success")
                }
                else {
                    println("reportGenRefList ------- No data to print")
                }

            } finally {
                GvyRefDataLookup.disconnect(conn);
                rs.close();
                println("GvyRefDataLookup.getDiscrepancyDate Ended");
            }

        }catch (Exception e) {
            println("Exception while getting data from tos_client_ves_discrepancy table::"+e);
        }
    }

    public void execute(Map map)
    {
        try
        {
            inj = new GroovyInjectionBase();
            def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");
            Connection conn;
            ResultSet rs = null;
            HashMap reportMap = null;
            ArrayList reportGenRefList =  new ArrayList();
            String reportVesVoy = "CBA889";

            try {
                conn = GvyRefDataLookup.connect();
                rs = GvyRefDataLookup.getDiscrepancyData("CBA889",conn);
                println("GvyRefDataLookup.getDiscrepancyDate Calling");

                while (rs.next()) {
                    reportMap = new HashMap();

                    String vesVoy = rs.getString("VESVOY");
                    String value = rs.getString("VALUE");
                    String unitId = rs.getString("CONTAINER_ID");
                    String type = rs.getString("TYPE_CODE");
                    String seal = rs.getString("SEAL_NBR");
                    String tempReq = rs.getString("TEMP_REQ");
                    String grossWt = rs.getString("GROSS_WT");
                    String tareWt = rs.getString("TARE_WT");
                    String unitVesVoy = rs.getString("UNIT_VESVOY");

                    reportMap.put("UnitNbr", unitId);
                    reportMap.put("UnitFlexString01", vesVoy);
                    reportMap.put("UnitFlexString02", value);
                    reportMap.put("UnitFlexString03", type);
                    reportMap.put("UnitFlexString04", seal);
                    reportMap.put("UnitFlexString05", tempReq);
                    reportMap.put("UnitFlexString06", tareWt);
                    reportMap.put("UnitFlexString07", grossWt);
                    reportMap.put("UnitFlexString08", unitVesVoy);

                    reportGenRefList.add(reportMap);

                    println("Details :::"+vesVoy+" :: "+value+" :: "+unitId+" :: "+type+" :: "+seal+" :: "+tempReq+" :: "+grossWt+" :: "+tareWt+" :: "+unitVesVoy)
                }

                println("reportGenRefList.size() ::: "+reportGenRefList.size());
                if (reportGenRefList.size() > 0) {
                    HashMap parameters = new HashMap();

                    //Create and Mail Report
                    JRDataSource ds = new JRMapCollectionDataSource(reportGenRefList);

                    def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                    reportRunner.emailExcelReport(ds,parameters, "CLIENT_VESSEL_DESC_REPORT",emailTo, "Client Vessel Discrepancy for : " + reportVesVoy  ,"Attached report for CVDR");

                    try {
                        GvyRefDataLookup.delDiscrepancyData(reportVesVoy,conn);
                        println("GvyRefDataLookup.deleteGenRefData Calling");
                    } catch (Exception e) {
                        println("GvyRefDataLookup.deleteGenRefData Ended with errors::" + e);
                    }

                    println("reportGenRefList ------- Success")
                }
                else {
                    println("reportGenRefList ------- No data to print")
                }

            } finally {
                GvyRefDataLookup.disconnect(conn);
                println("GvyRefDataLookup.getDiscrepancyDate Ended");
            }

        }catch (Exception e) {
            println("Exception while getting data from tos_client_ves_discrepancy table::"+e);
        }
    }

    public String getUnitVesVoy(String blNbr)
    {
        String vesVoy = null;
        try {
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("Unit").addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR, blNbr));
            println("Bl Query : "+ dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def unit = iter.next();
                    vesVoy = unit.getFieldValue("unitDeclaredIbCv.cvId");
                    if (vesVoy != null){
                        break;
                    }
                }
            }
            println(" vesVoy "+ vesVoy);
            return vesVoy;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}