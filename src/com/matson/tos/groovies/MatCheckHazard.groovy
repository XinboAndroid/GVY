import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.argo.business.model.Facility;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.argo.business.reports.DigitalAsset;
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
import com.navis.road.RoadField;
import java.util.Calendar;

import com.navis.inventory.business.api.UnitField
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.*
import com.navis.inventory.business.api.UnitFinder

import org.apache.log4j.Logger



public class MatCheckHazard extends GroovyInjectionBase
{
    //public boolean execute(Map params)
    public void execute(String container)
    {
        try
        {
            LOGGER.warn("Calling MatCheckHazard for ::"+ container);
            //def container = "MATU5137732";
            def inj = new GroovyInjectionBase();
            inj = new GroovyInjectionBase();
            List unitList = null;
            ArrayList trkTransList = new ArrayList();
            List trkTrans = getGateTrans(container)

            Iterator iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                //println("aTrans.tranCtrNbr ::: "+ aTrans.tranCtrNbr +"::"+aTrans.getTranSubType().getKey() + "::" + aTrans.getTranStatus().getKey() + "::"+aTrans.tranCreated + "::"+aTrans.tranIsHazard);
                def cntnrHaz = aTrans.tranIsHazard;
                cntnrHaz = cntnrHaz == true ? 'Y' : 'N';
                unitList = getActiveUnits(aTrans.tranCtrNbr);
                LOGGER.warn("unitList.size() "+unitList.size());
                Iterator unitIterator = unitList.iterator();
                String ueEquipment = null;

                while(unitIterator.hasNext())
                {
                    def unit = unitIterator.next();
                    def isHazardous=unit.getFieldValue("unitGoods.gdsIsHazardous");
                    isHazardous = isHazardous == true ? 'Y' : '';
                    LOGGER.warn("Unit number ::"+unit.unitId + "::"+cntnrHaz);
                    if (cntnrHaz.equalsIgnoreCase("N")){
                        unit.getGoods().attachHazards(null);
                    }
                }
            }

        }catch(Exception e){
            LOGGER.warn("Exception ::"+ e);
        }
    }

    public List getGateTrans(String container)
    {

        List gateTranUnits = null;
        ArrayList units = new ArrayList();
        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction")
                    .addDqPredicate(PredicateFactory.in(RoadField.TRAN_SUB_TYPE, "RI"))
                    .addDqPredicate(PredicateFactory.eq(RoadField.TRAN_CTR_NBR, container))
                    .addDqOrdering(Ordering.desc(RoadField.TRAN_CREATED))
                    .addDqPredicate(PredicateFactory.in(RoadField.TRAN_STATUS, "COMPLETE","OK"));


            LOGGER.warn("dq---------------"+dq);
            gateTranUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            LOGGER.warn("Query executed");
            LOGGER.warn("gateTranUnits ::"+gateTranUnits != null ? gateTranUnits.size() : 0)
            if(gateTranUnits != null) {
                Iterator iter = gateTranUnits.iterator();
                while(iter.hasNext()) {
                    def tran = iter.next();
                    units.add(tran);
                    break;
                }
            }
        }catch(Exception e){
            LOGGER.warn("Exception ::"+ e);
        }
        return units;
    }

    public List getActiveUnits(String unitId)
    {

        try {
            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitId)).addDqOrdering(Ordering.desc(InventoryField.UFV_TIME_OF_LAST_MOVE));

            LOGGER.warn("dq:::::::::"+dq)
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            LOGGER.warn("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    units.add(unit);
                    break;
                }
            }
            LOGGER.warn("unitsSize" + units.size);
            return units;
        }catch(Exception e){
            LOGGER.warn("Exception ::"+ e);
        }
    }
    private static final Logger LOGGER = Logger.getLogger(MatCheckHazard.class);
}
