/*
* A1   GR   02/03/2010  Generated ADD for New CNTR in the System
* A2   GR   09/23/2010  TT#007772 IF HAZ IS UNKNOWN dont post HZU action
* A3   GR   09/29/2010  SN4Q : DTA before ADD action (Removed code : Incorrect)
* A4   GR   10/04/10    Gems : Set VNumber to Null on IGT transaction
* A5   GR   10/06/10    Gems : Add VVD Info for Empty Ingate booking  
* A5   GR   10/15/10    Gems : leg value cal for Expt Full Load 
* A6   GR   02/11/11    Handel Actual ves,voy for SIT out -> RE Ingate condition 
* A7   GR   03/08/11    Set Obcarrier for SIT_UNASSIGN comming back as Export 
* A8   GR   04/28/11    TT#007772 HAZ dropped inversion file 1.4 TT#007772 
* A9   GR   05/18/11    set YardLocation for WO,P2,PassPass Gate Ingate and Outgate cntr
                        for GateTypeCode Report 
*/
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Equipment;
import com.navis.apex.business.model.GroovyInjectionBase

public class GvyCmisEventUnitReceive
{
     def gvyCmisUtil = null

    public String processUnitRecieve(String xmlGvyData, Object unit,Object gvyBaseClass)
    {
        def xmlGvyString = xmlGvyData
       try
      {
        
         gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");  
       
         def freightkind=unit.getFieldValue("unitFreightKind")
         freightkind = freightkind != null ? freightkind.getKey() : ''
         def vesselCd =  unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesId");                       
         def vesVoyageNbr =  unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdObVygNbr")
         def aibcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorId")
         System.out.println("TESTING vesselCd::"+vesselCd+"    vesVoyageNbr::"+vesVoyageNbr);
         def expGateBkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr"); //A5
          
         //Location Status
         xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationStatus=",'1')
 	     xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"vNumber=",'null') //A4
     
         if(freightkind.equals('FCL') || (freightkind.equals('MTY') && expGateBkgNbr != null && vesselCd != null)){ //A5
            xmlGvyString = processUnitRecieveFull(xmlGvyString,gvyCmisUtil,vesselCd,vesVoyageNbr,unit)              
         } else if(freightkind.equals('MTY')){
            xmlGvyString = processUnitRecieveEmpty(xmlGvyString,gvyCmisUtil)
         } 
         
          def lanegateId = gvyCmisUtil.getFieldValues(xmlGvyString, "laneGateId=")  
          def msgType = gvyCmisUtil.getFieldValues(xmlGvyString, "msgType=") 
          if(lanegateId.equals('WO GATE'))
         {
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationTier=",'T3')
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=",aibcarrierId)
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"loc=","WOA 1")
          }
         else if(lanegateId.equals('PIER2'))
         {
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationTier=",'T2')
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=",aibcarrierId)  
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"loc=","29Z")

          }else if (msgType.equals('UNIT_IN_GATE')){
              xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=",aibcarrierId)  
          }

      }catch(Exception e){
            e.printStackTrace();	
      }
      
      return xmlGvyString
    }

   public String processUnitRecieveEmpty(String xmlGvyData,Object gvyCmisUtil)
   {
	println("Empty unit method called::::");
      try
      {
         def xmlGvyString = xmlGvyData 
           xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVessel=","null")
           xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVoyage=","null")	
           xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"leg=","null")
       
        return xmlGvyString

     }catch(Exception e){
         e.printStackTrace()
     }

   }

    //A8
    public String setWOAFlag(Object event, Object unit,Object api)
   {
      try
      {
         //Set YardLocation - So can filter out of Type code report  
         def carrierVisitGkey = null;
		if(event.event.eventTypeId.equals("UNIT_IN_GATE")){
		 carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvdGkey")
		}else if(event.event.eventTypeId.equals("UNIT_DELIVER")){
		 carrierVisitGkey = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvdGkey")
		}
		def gvyCmisGateData = api.getGroovyClassInstance("GvyCmisGateData");  
		def laneGateId = gvyCmisGateData.getGateId(carrierVisitGkey)
		println("carrierVisitGkey ="+carrierVisitGkey+"  laneGateId="+laneGateId)
		if("WO GATE".equals(laneGateId)){
		  unit.setFieldValue("unitActiveUfv.ufvFlexString03","WOA")
		}else if("PIER2".equals(laneGateId)){
		  unit.setFieldValue("unitActiveUfv.ufvFlexString03","PIER2")
		}else if("PASSPASS".equals(laneGateId)){
		  unit.setFieldValue("unitActiveUfv.ufvFlexString03","PASSPASS")
		}

     }catch(Exception e){
         e.printStackTrace()
     }

   }

    public String processUnitRecieveFull(String xmlGvyData,Object gvyCmisUtil,String vesselCd, Object vesVoyageNbr,Object unit){
        processUnitRecieveFull( xmlGvyData, gvyCmisUtil, vesselCd,  vesVoyageNbr, unit, Boolean.FALSE);
    }

    public String processUnitRecieveFull(String xmlGvyData,Object gvyCmisUtil,String vesselCd, Object vesVoyageNbr,Object unit,Boolean inUseSuppliedCvId)
   {
       def xmlGvyString = xmlGvyData
      try
     {
       def actualVessel = 'null'
       def actualVoyage = 'null'
       def leg = 'null'
      //ACTUAL VESSEL,ACTUAL VOYAGE,LEG
         def ObCarrier = vesselCd + vesVoyageNbr;
         if (!inUseSuppliedCvId)
             ObCarrier = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId");
         ObCarrier = ObCarrier != null ? ObCarrier : ""
     //POL & POD
     def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
     def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
     def vesselType = gvyCmisUtil.getVesselClassForVesCode(vesselCd)

     def dibcarrierId = unit.getFieldValue("unitDeclaredIbCv.cvId")
     def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"); 
	 	 
     def _category= unit.getFieldValue("unitCategory")
     def categoryKey = _category != null ? _category.getKey() : '' 

     def dIbcarrierMode= unit.getFieldValue("unitDeclaredIbCv.cvCarrierMode")
     dIbcarrierMode = dIbcarrierMode != null ? dIbcarrierMode.getKey() : ""

     def declaredIBVesType = gvyCmisUtil.getVesselClassType(dibcarrierId)
     declaredIBVesType = declaredIBVesType != null ? declaredIBVesType : ''
     def declaredIBInVoyNbrForKQA =unit.getFieldValue("unitDeclaredIbCv.cvCvd.vvdIbVygNbr")
     boolean postMsg = false
     println("categoryKey ::"+categoryKey+"   decaredIBVesType::"+declaredIBVesType+"  cmdyId::"+cmdyId+"   dibcarrierId::"+dibcarrierId) 
     System.out.println("categoryKey ::"+categoryKey+"   decaredIBVesType::"+declaredIBVesType+"  cmdyId::"+cmdyId+"   dibcarrierId::"+dibcarrierId+"   loadPort::"+loadPort) 
     if(categoryKey.equals('IMPRT') || "KQA".equalsIgnoreCase(loadPort))
    {
        // println("categoryKey ::"+categoryKey+"   decaredIBVesType::"+declaredIBVesType+"  cmdyId::"+cmdyId+"   dibcarrierId::"+dibcarrierId)

         if(declaredIBVesType.equals('CELL')){
            actualVessel = dibcarrierId.length() == 6 ? dibcarrierId.substring(0,3) : 'null'
            actualVoyage = dibcarrierId.length() == 6 ? dibcarrierId.substring(3) : 'null'
            leg = '%'
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"vesvoy=",dibcarrierId)	    
         }
         else if(declaredIBVesType.equals('BARGE')){
             if (!"KQA".equalsIgnoreCase(loadPort))   {
                      leg = dibcarrierId.length() > 6 ? dibcarrierId.substring(6) : 'null'
                actualVessel = dibcarrierId.length() > 5 ? dibcarrierId.substring(0,3) : 'null'
                actualVoyage = dibcarrierId.length() > 5 ? dibcarrierId.substring(3,6) : 'null'
             }  else {
	                leg = loadPort+'_'+dischargePort;
                 def declaredOBVesType = null;

                 if (ObCarrier != null) {
                     declaredOBVesType = gvyCmisUtil.getVesselClassType(ObCarrier);
                 }
                 declaredOBVesType = declaredOBVesType != null ? declaredOBVesType : '';

                if ( "BARGE".equals(declaredOBVesType)) {
                    actualVessel = dibcarrierId.length() > 5 ? dibcarrierId.substring(0,3) : 'null'
                    actualVoyage = declaredIBInVoyNbrForKQA != null ? declaredIBInVoyNbrForKQA :dibcarrierId.length() > 5 ? dibcarrierId.substring(3,6) : 'null'
                } else {
                    ObCarrier = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvId");
                    actualVessel = ObCarrier.length() > 5 ? ObCarrier.substring(0, 3) : 'null'
                    actualVoyage = ObCarrier.length() > 5 ? ObCarrier.substring(3, 6) : 'null'
                }
            }
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"misc1=",dibcarrierId) 	
         }else if(dIbcarrierMode.equals('TRUCK')) {
             //TRUCK T60
            def intdObCarrierId= unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId") 
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""
            actualVessel = intdObCarrierId.length() > 6 ? intdObCarrierId.substring(0,3) : 'null'
            actualVoyage = intdObCarrierId.length() > 6 ? intdObCarrierId.substring(3,6) : 'null'
            leg = intdObCarrierId.length() > 6 ? intdObCarrierId.substring(6) : 'null' 
        }
        postMsg = true
     }
     else if(categoryKey.equals('EXPRT')){
        System.out.println("TESTING EXPORT FLOW");
        if(ObCarrier.equals('GEN_VESSEL') || ObCarrier.equals('GEN_CARRIER')){
          actualVessel = "null"
          actualVoyage = "null"
          leg = "null"
        }else if (cmdyId != null && 'SAT'.equals(cmdyId)){ //A6 - Starts
  		  def dobcarrierId = unit.getFieldValue("unitRouting.rtgDeclaredCv.cvId")
		  println("dobcarrierId : "+dobcarrierId);
          actualVessel = dobcarrierId.substring(0,3);
          actualVoyage = dobcarrierId.substring(3);
 	      leg = loadPort+'_'+dischargePort
          xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"vesvoy=",dobcarrierId)
          gvyCmisUtil = gvyCmisUtil != null ? gvyCmisUtil : gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");  
          if(!ObCarrier.equals(dobcarrierId)){ //A7 
			  gvyCmisUtil.setObCarrier(unit, dobcarrierId)
			  unit.setUnitRemark(null);
          }
       }//A6 - Ends
		else{
          System.out.println(" TESTING ELSE CONDITION");
          actualVessel = vesselCd
          actualVoyage = vesVoyageNbr
          leg = loadPort+'_'+dischargePort	   //A6
         xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"vesvoy=",ObCarrier)	    
        }
        postMsg = true
     } 

      if(postMsg){
        if(actualVessel!=null && actualVessel.length()>3){
        actualVessel=actualVessel.substring(0,3);
        }
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVessel=",actualVessel)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVoyage=",actualVoyage)	
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"leg=",leg)
      }
    }catch(Exception e){
        e.printStackTrace()
    }
     return xmlGvyString
   }

   public String unitRecieveChassis(String xmlGvyData)
   {
     def xmlGvyString = xmlGvyData
     xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"chassisNumber=",'%')
     return xmlGvyString
   }

   public void postMsgHazOvd(String xmlData,Object unit,Object gvyBaseClass)
   {
      def xmlGvyString = xmlData
      def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");  
      def isHazardous=unit.getFieldValue("unitGoods.gdsIsHazardous")
      def outOfGauge  = unit.getFieldValue("unitIsOog");    
      if(isHazardous)
     { 
         def hazImdg = gvyCmisUtil.getFieldValues(xmlGvyString,"hazImdg=")
         if(!'HAZ'.equals(hazImdg)){ //A8
          def xmlGvyHazStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"msgType=","UNIT_HAZARDS_INSERT")
          gvyCmisUtil.postMsgForAction(xmlGvyHazStr,gvyBaseClass,"HZU")
          gvyCmisUtil.postMsgForAction(xmlGvyHazStr,gvyBaseClass,"EDT")
		 }
      }
      if(outOfGauge)
     {
          def xmlGvyOvuStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"msgType=","UNIT_OVERDIMENSIONS_UPDATE")
          gvyCmisUtil.postMsgForAction(xmlGvyOvuStr,gvyBaseClass,"OVU")
          gvyCmisUtil.postMsgForAction(xmlGvyOvuStr,gvyBaseClass,"EDT")
      }
   }  
  
   //A1
   public void postMsgAdd(String xmlData,Object unit,Object gvyBaseClass)
   {
      def xmlGvyString = xmlData
      def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");  
	  def equiId = unit.unitId
	  def injBase =	new GroovyInjectionBase();
	  def complex = ContextHelper.getThreadComplex();
	  def unitFinder = injBase.getUnitFinder();
  	  def eq = Equipment.loadEquipment(equiId);

	  Collection collection = unitFinder.findAllUnitsUsingEq(complex, eq);	
      //println("equiId="+equiId+"collection="+(collection == null ? 0: collection.size()))

      if(collection != null && collection.size() == 1){
         gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"ADD")
	  }
   }  

    //if the POD = NIS port, and the O/B Intended = GEN_TRUCK, set the O/B Intended = BARGE.
    public void setOBCarrierToBarge(Object unit)
   {
     try
     {
       def visit = ""
       def complex = com.navis.argo.ContextHelper.getThreadComplex();
       def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId") 
       intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ""
    
       def curDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
       curDischPort = curDischPort != null ? curDischPort : ""
       //Get gvyCmisUtil from Global Variable 
       if(gvyCmisUtil.isNISPort(curDischPort) && intdObCarrierId.equals("GEN_TRUCK")){
        //SET IT TO BARGE
        visit = com.navis.argo.business.model.CarrierVisit.findOrCreateVesselVisit(com.navis.argo.ContextHelper.getThreadFacility(), "BARGE")
        unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvIntendedObCv(visit)
        unit.getUfvForFacilityNewest(com.navis.argo.ContextHelper.getThreadFacility()).setUfvActualObCv(visit)
       }
      //println("curDischPort ::"+curDischPort+"  intdObCarrierId:: "+intdObCarrierId+"   Set the OB Carrrier to BARGE")
   }catch(Exception e){
        e.printStackTrace()
   }

 }//Method Ends
}