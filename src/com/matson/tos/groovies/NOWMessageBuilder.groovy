/*

Groovy code called to build an XML message to be used for now solutions. See Sample below

<tracking>
  <truck-arrival>
    <gate-id>SI GATE</gate-id>
    <stage-id>SI GATE</stage-id>
    <lane-id>2</lane-id>
    <truck trucking-co-id='AABV' id='1234' />
    <truck-visit tracking-tag-nbr='101' tv-key='5067538' />
    <dropoff>
      <chassis eqid='AMIN012826X' length-mm='6068' height-mm='0' type='C20' />
      <position slot='' />
    </dropoff>
    <timestamp>2008-07-03T00:06:41</timestamp>
  </truck-arrival>
</tracking>

Amine Nebri, anebri@navis.com - June 25 2008
Added OutgateMessage SKB - Aug 12 2008

*/

/*
* Srno   Doer  Date          Desc
* A1     GR    04/06/2010    Added Method and Changes to pass ChasType and NO-EIT transactions
* A2     GR    04/21/2010    Strip Chassis CheckDigit
* A3     GR    05/25/2010    Handel Null Chassis Feed from Transaction. Set ChasType to STD
* A4     GR    06/04/10      Truck ID Add LicenceNbr check for PassPass Gate
* A5     GR    06/08/10      Adding ChaType to outgate Transactions
* A6     GR    07/07/10      Added Check for Null Chassis value in Container Element
*/

import groovy.xml.MarkupBuilder
import com.navis.argo.business.reference.Accessory
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.reference.Container
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.argo.business.reference.EquipType
import com.navis.road.business.model.TruckTransaction

class NOWMessageBuilder {
    public String formIngateMessage(inOutDao, verify) {
        def fixNull = {s -> s == null ? "" : s }
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        xml.tracking(verify: verify)
                {
                    'truck-arrival'()
                            {
                                'gate-id'(inOutDao.gate.gateId)
                                'stage-id'(inOutDao.gate.gateId)
                                'lane-id'(inOutDao.tv.tvdtlsEntryLane == null ? '99' : inOutDao.tv.tvdtlsEntryLane.laneId)
                                truck(id: (inOutDao.tv.tvdtlsTruckId == null ? (inOutDao.tv.tvdtlsTruckLicenseNbr != null ? inOutDao.tv.tvdtlsTruckLicenseNbr : fixNull(inOutDao.tv.tvdtlsTruckId)) : inOutDao.tv.tvdtlsTruckId) , 'trucking-co-id': fixNull(inOutDao.tv.carrierOperator.bzuId))
                                if(isValidEit(inOutDao)){//A1 Do not pass tracking-tag-nbr for NON-EIT
                                    'truck-visit'('tv-key': inOutDao.tv.cvdCv.cvGkey, 'tracking-tag-nbr': fixNull(inOutDao.tv.tvdtlsBatNbr))
                                }else{
                                    'truck-visit'('tv-key': inOutDao.tv.cvdCv.cvGkey)
                                }

                                dropoff()
                                        {
                                            def transactions = (Set<TruckTransaction>) inOutDao.tv.tvdtlsTruckTrans

                                            for (transaction in transactions) {
                                                if (transaction.tranSubType in [TranSubTypeEnum.RE, TranSubTypeEnum.RM, TranSubTypeEnum.RC, TranSubTypeEnum.RI]) {
                                                    if (transaction.tranCtrNbr != null) {
                                                        def container_ = Container.findContainer(transaction.tranCtrNbr)

                                                        if (container_)
                                                            container(eqid: transaction.tranCtrNbr, type: container_.eqEquipType.eqtypId, 'length-mm': container_.eqLengthMm, 'height-mm': container_.eqHeightMm, 'on-chassis-id': transaction.tranChsNbr, slot: fixNull(transaction.tranCtrPosition))
                                                    }
                                                    if (transaction.tranChsNbr) {
                                                        def chassis_ = Chassis.findChassis(transaction.tranChsNbr)

                                                        if (chassis_) {
                                                            def chassisId = transaction.tranChsNbr.substring(0,transaction.tranChsNbr.length()-1); //A2
                                                            chassis(eqid: chassisId, type:chassis_.eqEquipType.eqtypId , 'length-mm': chassis_.eqLengthMm, 'height-mm': chassis_.eqHeightMm, 'chastype':getChassisType(transaction))
                                                            position(slot: fixNull(transaction.tranChsPosition))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                timestamp(java.text.MessageFormat.format("{0,date,yyyy-MM-dd'T'HH:mm:ss}", new Date()))
                            }
                }

        //com.navis.road.business.util.RoadBizUtil.getMessageCollector().appendMessage(com.navis.framework.util.BizFailure.create(writer.toString()))

        return writer.toString()
    }

    public String formOutgateMessage(inOutDao) {
        def fixNull = {s -> s == null ? "" : s }
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        xml.tracking()
                {
                    'truck-departure'()
                            {
                                'gate-id'(inOutDao.gate.gateId)
                                'stage-id'(inOutDao.gate.gateId)
                                'lane-id'(inOutDao.tv.tvdtlsEntryLane == null ? '99' : inOutDao.tv.tvdtlsEntryLane.laneId)
                                truck(id: (inOutDao.tv.tvdtlsTruckId == null ? (inOutDao.tv.tvdtlsTruckLicenseNbr != null ? inOutDao.tv.tvdtlsTruckLicenseNbr : fixNull(inOutDao.tv.tvdtlsTruckId)) : inOutDao.tv.tvdtlsTruckId) , 'trucking-co-id': fixNull(inOutDao.tv.carrierOperator.bzuId))
                                if(isValidEit(inOutDao)){ //A1 Do not pass tracking-tag-nbr for NON-EIT
                                    'truck-visit'('tv-key': inOutDao.tv.cvdCv.cvGkey, 'tracking-tag-nbr': fixNull(inOutDao.tv.tvdtlsBatNbr))
                                }else{
                                    'truck-visit'('tv-key': inOutDao.tv.cvdCv.cvGkey)
                                }

                                pickup()
                                        {
                                            def transactions = (Set<TruckTransaction>) inOutDao.tv.tvdtlsTruckTrans

                                            for (transaction in transactions) {
                                                if (transaction.tranSubType in [TranSubTypeEnum.DC, TranSubTypeEnum.DE, TranSubTypeEnum.DI, TranSubTypeEnum.DM]) {
                                                    if (transaction.tranCtrNbr != null) {
                                                        def container_ = Container.findContainer(transaction.tranCtrNbr)

                                                        if (container_)
                                                            container(eqid: transaction.tranCtrNbr, type: container_.eqEquipType.eqtypId, 'length-mm': container_.eqLengthMm, 'height-mm': container_.eqHeightMm, 'on-chassis-id': fixNull(transaction.tranChsNbr), slot: fixNull(transaction.tranCtrPosition))

                                                    }
                                                    if (transaction.tranChsNbr) {
                                                        def chassis_ = Chassis.findChassis(transaction.tranChsNbr)

                                                        if (chassis_) {
                                                            def chassisId = transaction.tranChsNbr.substring(0,transaction.tranChsNbr.length()-1); //A2
                                                            chassis(eqid: chassisId, type: chassis_.eqEquipType.eqtypId, 'length-mm': chassis_.eqLengthMm, 'height-mm': chassis_.eqHeightMm, 'chastype':getChassisType(transaction))
                                                            position(slot: fixNull(transaction.tranChsPosition))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                timestamp(java.text.MessageFormat.format("{0,date,yyyy-MM-dd'T'HH:mm:ss}", new Date()))
                            }
                }

        //com.navis.road.business.util.RoadBizUtil.getMessageCollector().appendMessage(com.navis.framework.util.BizFailure.create(writer.toString()))

        return writer.toString()
    }


//Test1. Triaxel and Mg Triaxel Setting
    public String getChassisType(Object transaction){

        def chasType = null;
        def acryId = transaction.tranChsAccNbr
        Chassis chassis = transaction.tranChassis

        if(chassis == null){ //A3
            return "STD";
        }
        def chasEqType = chassis.eqEquipType
        //println("transaction.tranChsNbr="+transaction.tranChsNbr)
        def isTriaxle = chassis.eqEquipType.getEqtypIsChassisTriaxle()
        //EquipType equipType = EquipType.findEquipType(transaction.tranChsNbr);
        //def isTriaxle =  equipType.getEqtypIsChassisTriaxle()

        if(acryId != null && isTriaxle){ chasType = "MGX"; }
        else if(isTriaxle){ chasType = "TX"; }
        else if(acryId != null ){ chasType = "MG"; }
        else{ chasType = "STD" }

        //println("chassis="+chassis+" acryId="+acryId+" isTriaxle="+isTriaxle+" chasType"+chasType+" chasEqType="+chasEqType)
        return chasType
    }

    public boolean isValidEit(inDao){

        def eitString = inDao.tv.tvdtlsBatNbr
        int eitId = eitString != null ? eitString.toInteger() : 0
        if (eitId < 101 || eitId > 200) {
            return false;
        }else{
            return true;
        }
    }

}//class Ends
