/*
*  Sr doer date      change
*  A1 GR   07/13/11  Change to handel lease cntr
*  A2 GR   07/14/11  Added 12 new Codes
*  A3 GR   07/14/11  Added Method setEquipOwnerInBizTask
*/
public class GvyXpsEquipmentOwner
{
    public void setEquipmentOwner(Object event)
    {
        try
        {
            def unit = event.getEntity();
            def unitId = unit.unitId
            def ownerCode = event.getProperty("EquipmentOwner");
            ownerCode = ownerCode != null ? ownerCode : ''
            /*if(!unitId.startsWith('MATU') && ownerCode.equals('MATU')){
                  unit.setUnitFlexString13('LEAS');
            }*/
            if(ownerCode.equals('MATU') || ownerCode.equals('ANLC') || ownerCode.equals('ANZU') || ownerCode.equals('APLU') ||
                    ownerCode.equals('CCLU') || ownerCode.equals('CSXU') || ownerCode.equals('DOLU') || ownerCode.equals('FSCU') ||
                    ownerCode.equals('HLCU') || ownerCode.equals('MAEU') || ownerCode.equals('MSGU') || ownerCode.equals('MSLU') ||
                    ownerCode.equals('NYKU') || ownerCode.equals('POLU') ||  ownerCode.equals('PONU') || ownerCode.equals('SHOW') ||
                    ownerCode.equals('ZCSU') || ownerCode.equals('HSDU') || ownerCode.equals('FHSU') || ownerCode.equals('CPSU')
                    || ownerCode.equals('MEDX') || ownerCode.equals('WATU') || ownerCode.equals('AMPC') || ownerCode.equals('CHVU')
                    || ownerCode.equals('COOP') || ownerCode.equals('CPRU') || ownerCode.equals('EADE') || ownerCode.equals('DCIU')
                    || ownerCode.equals('EADU') || ownerCode.equals('HEAD') || ownerCode.equals('MDAN') || ownerCode.equals('MSCU')
                    || ownerCode.equals('PLEU'))
            {
                unit.setUnitFlexString13(ownerCode);
            }
            /* else{
              unit.setUnitFlexString13('LEAS');
             }*/
        }catch(Exception e){
            e.printStackTrace()
        }
    }


    /*public void setEquipOwnerInBizTask(dao){
        try{
         def truckTran = dao.tran
         def unit = truckTran.tranUnit
         def unitId = unit.unitId

         def equiOwner =unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId")
         if(!unitId.startsWith('MATU') && equiOwner.equals('MATU')){
             unit.setUnitFlexString13('LEAS');
         }
        }catch(Exception  e){
            e.printStackTrace()
        }
    }*/
}