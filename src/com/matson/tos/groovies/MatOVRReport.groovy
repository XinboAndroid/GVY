import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
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

import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.argo.business.model.Yard
import com.navis.xpscache.yardmodel.api.*;
import com.navis.xpscache.yardmodel.impl.*;

/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatOVRReport extends GroovyInjectionBase
{
    private final String emailTo = "riyer@matson.com";
    def inj = new GroovyInjectionBase();

    public boolean execute(Map params)
    {

        try
        {
            getUnitForId("CAXU6972024");

            println("reportUnitList ------- Success")
        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }


    public void getUnitForId(String containerId)
    {

        try {
            println("Inside getUnit");
            inj = new GroovyInjectionBase();
            ArrayList units = new ArrayList();
            //String containerId = "MATU2496256";
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S40_YARD))
            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def ufv = iterUnitList.next();
                    def unit = ufv.ufvUnit;
                    def unitNbr = unit.unitId;
                    def consignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                    def shipper = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName");
                    def vesVoy = unit.getFieldValue("unitDeclaredIbCv.cvId");
                    def loc = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")


                    def lkpSlot=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
                    lkpSlot = lkpSlot!= null ? lkpSlot : ''
                    def lkpSlotValue = lkpSlot.indexOf(".")== -1 ? lkpSlot : lkpSlot.substring(0,lkpSlot.indexOf("."));
                    lkpSlotValue = formatYardPosition(lkpSlotValue) //A1

                    def cargoNotes=unit.getFieldValue("unitRemark");

                    def dtnAvailDt = ufv.getFieldValue("ufvFlexDate02");
                    def dtnDueDt = ufv.getFieldValue("ufvFlexDate03");
                    def lastfreeDayStr = ufv.getFieldValue("ufvCalculatedLastFreeDay");
                    Date lastfreeDate = getlastFreeDate(dtnAvailDt, lastfreeDayStr);

                    println("Details ::: "+ consignee +" ::" + unitNbr + "::"+vesVoy+ "::"+loc+"::"+ lkpSlotValue +"::"+shipper +"::"+cargoNotes+"::"+dtnDueDt);
                }
            }
        }catch (Exception e){
            println("Error :" + e);
        }
    }

//Format yard position based onyard file
    public String formatYardPosition(String inYardSlot){
        def binName = inYardSlot;
        try{
            int lastDot = inYardSlot.lastIndexOf('.');

            def inFacility = com.navis.argo.ContextHelper.getThreadFacility()
            //10/20/10 - IF Facility HON Format as per N4yard file Else IF NIS Pass AS-IS
            if(inFacility != null && !"HON".equals(inFacility.getFcyId())){
                println("Dont Compute Location as not a HON Transaction")
                return binName;
            }
            Yard inYard =  Yard.findYard("SI", inFacility)
            IYardModel yardModel = inYard.getYardModel();
            if (lastDot > 0) {
                binName = inYardSlot.substring(0, lastDot);
            }
            IYardBin bin = yardModel.getBin(binName);
            if(bin == null){
                return binName
            }
            IYardBlock yardBlock = bin.getBlock();
            if(yardBlock != null && yardBlock.isWheeled()){
                if(binName.length() >=4){
                    binName = binName.substring(0,3)+' '+binName.substring(3);
                }
            }else{
                //println("yardBlock.getBlockType()------------------------- "+yardBlock.getBlockType())
                if(binName.length() > 3 && yardBlock != null &&
                        (yardBlock.getBlockType()== 13 || yardBlock.getBlockType()== 12 || yardBlock.getBlockType()== 1)){
                    binName = binName.substring(0,2)+' '+binName.substring(2);
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return binName
    }


    public Date getlastFreeDate(Object availDate, String lastfreeDay)
    {
        Date lastFreeDate = null;

        if(availDate == null){
            lastFreeDate = null
        }
        else if (lastfreeDay != null && lastfreeDay.indexOf("no") == -1)
        {
            def gvyUtil = inj.getGroovyClassInstance("GvyEventUtil");
            DateFormat formatter = new java.text.SimpleDateFormat("yyyy-MMM-dd");
            lastFreeDate = (Date)formatter.parse(lastfreeDay);
        }else if (lastfreeDay != null && lastfreeDay.indexOf("no") != -1) {
            lastFreeDate = null
        }
        return lastFreeDate
    }

}


