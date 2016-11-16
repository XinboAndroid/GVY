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
import com.navis.argo.ArgoRefField;
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

import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;

import com.navis.argo.ArgoConfig;
import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.reference.AgentRepresentation;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.reference.Agent;
import com.navis.road.business.model.TruckingCompany
import java.lang.*;
import com.navis.argo.business.model.GeneralReference;
// import com.navis.framework.ulc.server.application.controller.form.ShowDeleteFormCommand;
import com.navis.framework.ulc.server.application.controller.form.UlcDeleteUiProcessor
import com.navis.argo.business.reference.Chassis;
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.inventory.business.atoms.EqDamageSeverityEnum;


import com.navis.argo.business.reference.RoutingPoint;
/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

/**
 *
 * Patch Matson N4 Groovy for 2.1/2.6 upgrade
 *
 * Author: Peter Seiler
 * Date: 6 July 2014
 * JIRA: ARGO-59892
 * SFDC: None
 * Called from: Unkown
 *
 */

public class MatReportGCSDamageContainers extends GroovyInjectionBase
{
    private final String emailTo = "1aktosdevteam@matson.com";
    //private final String emailTo = "1aktosdevteam@matson.com";
    private final String  emailFrom = '1aktosdevteam@matson.com'
    def inj = new GroovyInjectionBase();
    String reportDate = null;
    String reportTime = null;
    String reportVesVoy = null;
    public boolean execute(Map params)
    {
        try{
            List ufvList = getDamageUnits();
            HashMap map = null;
            ArrayList reportUnitList =  new ArrayList();

            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    def vesselService  =unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.cvdService.srvcId")
                    if ("GCS".equals(vesselService)){
                        map = new HashMap();
                        def unitId = unit.unitId;
                        def damageNotes = unit.getUnitEquipDmgsItmNote();
                        def vesselCd =  ufv.getFieldValue("ufvActualObCv.cvId");
                        def outBoundATD = ufv.getFieldValue("ufvActualObCv.cvATD");
                        def POD = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
                        def POL = unit.getFieldValue("unitRouting.rtgPOL.pointId");
                        def EquipType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                        def positionSlot = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot");
                        println("<<<<vesselService>>>>>"+unit.unitId+vesselService+"<<>>"+"<<>>"+vesselCd+"<<>>"+outBoundATD+"<<>>"+POD+"<<>>"+POL+"<<>>"+EquipType+"<<>>"+positionSlot+"<<>>"+damageNotes);
                        String isDeparted = getLatestUfv(unitId);
                        println("<<<<<isDeparted>>>>>>>>"+isDeparted);
                        if (damageNotes == null || damageNotes == "null"){
                            damageNotes = "";
                        }
                        if (("NGB".equals(POD) || "SHA".equals(POD)) && ("Y".equals(isDeparted))){
                            map.put("UnitNbr", unitId);
                            map.put("OutboundCarrierId", vesselCd);
                            map.put("OutboundCarrierATD", outBoundATD);
                            map.put("POD", POD);
                            map.put("POL", POL);
                            map.put("EquipmentType", EquipType);
                            map.put("PositionSlot", positionSlot);
                            map.put("DamageDescription", damageNotes);
                            map.put("OutboundCarrierServiceId", vesselService);
                            map.put("UnitFlexString01", reportDate);
                            map.put("UnitFlexString02", reportTime);
                            reportVesVoy = vesselCd;
                            if(map != null) {
                                reportUnitList.add(map);
                            }
                        }
                    }
                }
                println("reportUnitList.size() "+reportUnitList.size());
                if (reportUnitList.size() > 0)
                {
                    HashMap parameters = new HashMap();

                    //Create and Mail Report
                    JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                    HashMap reportDesignsmap = new HashMap();
                    reportDesignsmap.put("DAMAGE CONTAINER REPORT",ds);
                    try
                    {
                        def reportRunner = inj.getGroovyClassInstance("ReportRunner");

                        reportRunner.emailReports(reportDesignsmap,parameters, emailTo, reportVesVoy+" Damage to NGB/SHA" ,"Attached Damage containers report for " +reportVesVoy);
                        println("reportUnitList ------- Success")
                    }catch (Exception e){
                        println("No design");
                    }
                }
                else {
                    def emailSender = inj.getGroovyClassInstance("EmailSender")
                    //emailSender.custSendEmail(emailFrom,emailTo, "Damage to NGB/SHA","No Damages are reported");
                    println("reportUnitList ------- No data to print")
                }
            }
        }catch (Exception e){
            println("Exception in MatReportGCSDamageContainers :: "+ e)
        }
    }

    public List getDamageUnits()
    {
        Date startDate = new Date() - 3;
        reportDate = new Date().format('MM/dd/yyyy');
        reportTime = new Date().format('HH:mm');
        String trimDate = startDate.format('yyyy-MM-dd')
        startDate = startDate.parse('yyyy-MM-dd', trimDate);
        Date endDate = startDate + 1;
        println("startDate "+ startDate);
        println("endDate "+ endDate);
        println("reportDate "+ reportDate);
        println("reportTime "+ reportTime);

        try {
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq = dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_PRIMARY_EQ_DAMAGE_SEVERITY,EqDamageSeverityEnum.MAJOR,EqDamageSeverityEnum.MINOR))
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_VISIT_STATE,"3DEPARTED"))
                    .addDqPredicate(PredicateFactory.ge(UnitField.UFV_TIME_OUT,startDate))
                    .addDqPredicate(PredicateFactory.le(UnitField.UFV_TIME_OUT,endDate));

            println(dq);
            List ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            return ufvList;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public String getLatestUfv(String unitId)
    {
        String isDeparted = "N";
        try {
            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitId)).addDqOrdering(Ordering.desc(InventoryField.UFV_TIME_OF_LAST_MOVE));

            println("dq:::::::::"+dq)
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    def ufvVisitState = ufv.getFieldValue("ufvVisitState");
                    ufvVisitState = ufvVisitState.getKey();
                    println("<<<<<<<<<<ufvVisitState>>>>>>>>>>>>"+ufvVisitState);
                    if ("3DEPARTED".equals(ufvVisitState)){
                        isDeparted= "Y"
                    }
                    break;
                }
            }
            return isDeparted;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}