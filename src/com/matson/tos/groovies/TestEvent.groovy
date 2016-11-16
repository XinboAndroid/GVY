import com.navis.services.business.event.*
import com.navis.argo.business.api.Serviceable;

import com.navis.services.business.api.EventManager;
import com.navis.services.business.rules.EventType;
import com.navis.inventory.business.units.UnitFacilityVisit;

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.services.business.rules.*
import com.navis.argo.business.api.IFlagType
import com.navis.argo.business.api.ServicesManager
import com.navis.framework.business.Roastery;
import com.navis.inventory.business.units.Unit

import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.ArgoBizMetafield;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.reference.Chassis;
import com.navis.argo.business.reference.Accessory;
import com.navis.argo.ArgoRefField;


public class TestEvent{

    public void sendNewVesEmail(String note, Object api, String aibcarrierId)
    {
        String msg1 = "NewVes Completed";  String msg2 = "NIS Load Transaction Completed";
        String msg3 = "Supplemental Data Completed"
        try{
            def emailto = "1aktosdevteam@matson.com"; def sub = null;
            String unitCnt = note.substring(note.indexOf("=")+1,note.indexOf("=")+4);
            if(note.contains(msg2)){
                emailto = "1aktosdevteam@matson.com";
                sub = "NIS Load Transaction Process Completed for "+aibcarrierId+" in TOS."+unitCnt+" Units Processed";
            }else if(note.contains(msg3)){
                sub = "Supplemental Data Completed in TOS."+unitCnt+" Units Processed";
            }
            def emailSender = api.getGroovyClassInstance("EmailSender")
            emailSender.sendEmail(emailto,sub,sub);
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public String getMostRecentEvent(String inEventId, Object unit)
    {
        println("<<<getMostRecentEvent >>> ::: ")
        EventType eventType = EventType.findEventType(inEventId);
        if (eventType == null) {
            println("<<<EVENT TYPE IS NULL>>>")
            return null;
        }
        EventManager sem = (EventManager)Roastery.getBean("eventManager");
        List events = sem.getEventHistory(eventType, unit);
        if (events.isEmpty()) {
            return null;
        } else {
            Event eventObj = (Event)events.get(0);
            def eventTypeMsg = eventObj.getEventTypeId()
            println("<<<EVENT TYPE >>> ::: "+eventTypeMsg)
            return eventTypeMsg
        }
    }

    public Object getActiveUfvUnit(Object unit)
    {
        println('<<<getActiveUfvUnit-1>>>')
        GroovyInjectionBase  injBase = new GroovyInjectionBase()
        def facility = injBase.getFacility();
        println('<<<getActiveUfvUnit-2>>>::: '+facility)
        def activeUfv = unit.getUfvForFacilityLiveOnly(facility)
        println('<<<getActiveUfvUnit-3>>>::: '+activeUfv)
        return activeUfv
    }

    public Object getHoldAppliesToClass(String aHold)
    {
        ServicesManager sm = (ServicesManager)Roastery.getBean("servicesManager");
        IFlagType iflagType =  sm.getFlagTypeById(aHold)
        LogicalEntityEnum logicalEnum = iflagType.getFlgtypAppliesTo()
        if(logicalEnum instanceof Unit) {
            println("Print 123 ")
        }
        else{
            println("Print 456")
        }

    }

    public void fetchChassis(){
        try{
            ScopedBizUnit matBizOwner = ScopedBizUnit.findEquipmentOwner('MATU')
            ScopedBizUnit matBizOperator = ScopedBizUnit.findEquipmentOperator('MAT')

            ScopedBizUnit unkBizOperator = ScopedBizUnit.findEquipmentOperator('UNK')
            def unkOperGkey = unkBizOperator.bzuGkey

            ScopedBizUnit unkBizOwner = ScopedBizUnit.findEquipmentOwner('UNK')
            def unkOwnerGkey = unkBizOwner.bzuGkey

            DomainQuery dq = QueryUtils.createDomainQuery("Chassis")
            List chasList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            Chassis chs = null
            int i = 0;
            int j = 0;
            Iterator iter = chasList.iterator();
            while(iter.hasNext()) {
                chs = (Chassis)iter.next();
                def operGkey = chs.getEquipmentOperator() != null ? chs.getEquipmentOperator().bzuGkey : null
                def ownerGkey = chs.getEquipmentOwner() != null ? chs.getEquipmentOwner().bzuGkey : null

                if(chs.getEquipmentOperator() == null || (unkOperGkey.equals(operGkey))){
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OWNER, matBizOwner.bzuGkey)
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OPERATOR, matBizOperator.bzuGkey)
                    println("UNK Chas Operator set to MATU/MAT----------------"+chs.eqIdFull)
                    i++;
                } // If Ends

                if(chs.getEquipmentOwner() == null || (unkOwnerGkey.equals(ownerGkey))){
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OWNER, matBizOwner.bzuGkey)
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OPERATOR, matBizOperator.bzuGkey)
                    println("UNK Chas Owner set to MATU/MAT----------------"+chs.eqIdFull)
                    j++;
                } // If Ends
            }//While Ends
            println("Count of UNK OPERATOR ="+i);
            println("Count of UNK OWNER ="+j);
        }catch(Exception e){
            e.printStackTrace();
        }
    } //Method Ends


    public void fetchAccessory(){
        try{
            ScopedBizUnit matBizOwner = ScopedBizUnit.findEquipmentOwner('MATU')
            ScopedBizUnit matBizOperator = ScopedBizUnit.findEquipmentOperator('MAT')

            ScopedBizUnit unkBizOperator = ScopedBizUnit.findEquipmentOperator('UNK')
            def unkOperGkey = unkBizOperator.bzuGkey

            ScopedBizUnit unkBizOwner = ScopedBizUnit.findEquipmentOwner('UNK')
            def unkOwnerGkey = unkBizOwner.bzuGkey

            DomainQuery dq = QueryUtils.createDomainQuery("Accessory")
            List chasList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            Accessory chs = null
            int i = 0;
            int j = 0;
            Iterator iter = chasList.iterator();
            while(iter.hasNext()) {
                chs = (Accessory)iter.next();
                def operGkey = chs.getEquipmentOperator() != null ? chs.getEquipmentOperator().bzuGkey : null
                def ownerGkey = chs.getEquipmentOwner() != null ? chs.getEquipmentOwner().bzuGkey : null

                if(chs.getEquipmentOperator() == null || (unkOperGkey.equals(operGkey))){
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OWNER, matBizOwner.bzuGkey)
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OPERATOR, matBizOperator.bzuGkey)
                    println("UNK MG Operator set to MATU/MAT----------------"+chs.eqIdFull)
                    i++;
                } // If Ends

                if(chs.getEquipmentOwner() == null || (unkOwnerGkey.equals(ownerGkey))){
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OWNER, matBizOwner.bzuGkey)
                    chs.setFieldValue(ArgoBizMetafield.EQUIPMENT_OPERATOR, matBizOperator.bzuGkey)
                    println("UNK MG Owner set to MATU/MAT----------------"+chs.eqIdFull)
                    j++;
                } // If Ends
            }//While Ends
            println("Count of MG OPERATOR ="+i);
            println("Count of MG OWNER ="+j);
        }catch(Exception e){
            e.printStackTrace();
        }
    } //Method Ends


    public void getChassisUNKOperator(){
        try{
            DomainQuery dq = QueryUtils.createDomainQuery("Chassis").addDqPredicate(PredicateFactory.eq(ArgoRefField.BIZU_IS_EQ_OPERATOR, 'UNK'));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("List of chassis :"+(list != null ? list.size() : "ZERO"))
            /*
            //BIZU_IS_EQ_OWNER, BIZU_IS_EQ_OPERATOR, BZU_IS_EQ_OWNER, BZU_IS_EQ_OPERATOR
            ScopedBizUnit bizOwner = ScopedBizUnit.findEquipmentOwner('MATU')
            ScopedBizUnit bizOperator = ScopedBizUnit.findEquipmentOperator('MAT')
            newChas.setFieldValue(ArgoBizMetafield.EQUIPMENT_OWNER, bizOwner.bzuGkey)
            newChas.setFieldValue(ArgoBizMetafield.EQUIPMENT_OPERATOR, bizOperator.bzuGkey)
            */
        }catch(Exception e){
            e.printStackTrace()
        }
    }


}