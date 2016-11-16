/*
* Srno doer date      Change
* A1   GR   02/11/2011  Added Map sorting Map By Value
* A2   GR   06/30/2011  Added Map sorting Map By Key
*/
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.collections.comparators.ComparatorChain;
import java.util.List
import java.util.LinkedList
import java.util.LinkedHashMap
import java.util.Map
import java.util.Iterator
import java.util.Collection
import java.util.Comparator
import java.lang.Comparable


public class ReportFieldSortUtil {

    public ArrayList processFieldSort(ArrayList unitList, String sortFields)
    {
        Object[] dataMapArr =unitList.toArray();
        ArrayList arrList = new ArrayList();
        try
        {
            ComparatorChain chain = new ComparatorChain();
            String[] sortUnitFlds = sortFields.split(',')
            for(aField in sortUnitFlds)
            {
                if(aField.equals('UnitNbr')){
                    chain.addComparator(new SortUnitNbr());
                }else if(aField.equals('GoodsConsigneeName')){
                    chain.addComparator(new SortGoodsConsigneeName());
                }else if(aField.equals('DeclaredIbCarrierId')){
                    chain.addComparator(new SortDeclaredIbCarrier());
                }else if(aField.equals('truckingCompanyId')){
                    chain.addComparator(new SortTruckCompanyId());
                }else if(aField.equals('vesselId')){
                    chain.addComparator(new SortVesselId());
                }else if(aField.equals('createdDate')){
                    chain.addComparator(new SortCreatedDate())
                }else if(aField.equals('EquipmentType')){
                    chain.addComparator(new SortEquipmentType())
                }
            }//For Ends

            Arrays.sort(dataMapArr, chain);
            printData(dataMapArr);

            for(aUnit in dataMapArr){
                arrList.add(aUnit)
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return arrList
    }

    public void printData(Object[] dataMapArr){
        try
        {
            for(aUnitData in dataMapArr)
            {
                HashMap map = (HashMap)aUnitData;
                //println("KEY :"+map.get("DeclaredIbCarrierId")+"  UnitNbr:"+map.get("UnitNbr"));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //A1 - Starts Sort Map Object By Value
    public Map sortMapByValue(Map map)
    {
        List list = new LinkedList(map.entrySet());
        Collections.sort(list, new SortMapObject());
        Map result = new LinkedHashMap();
        for (it in list)
        {
            Map.Entry entry = (Map.Entry)it;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }  //A1 - Starts


    //A2 - Starts Sort Map Object By Key
    public Map sortMapByKey(Map map)
    {
        List list = new LinkedList(map.entrySet());
        Collections.sort(list, new SortMapObjectByKey());
        Map result = new LinkedHashMap();
        for (it in list)
        {
            Map.Entry entry = (Map.Entry)it;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }  //A2 - Starts

} //ReportFieldSortUtil - Class Ends


public class SortMapObject implements java.util.Comparator{
    public int compare(Object o1, Object o2)
    {
        return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
    }
}


public class SortMapObjectByKey implements java.util.Comparator{
    public int compare(Object o1, Object o2)
    {
        return ((Comparable) ((Map.Entry) (o1)).getKey()).compareTo(((Map.Entry) (o2)).getKey());
    }
}

public class SortDeclaredIbCarrier implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        String declaredIb1 = new ReportSort(map1).getDeclaredIbCarrierId();
        String declaredIb2 = new ReportSort(map2).getDeclaredIbCarrierId();
        return declaredIb1.compareTo(declaredIb2 );
    }
}

public class SortUnitNbr implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        String unitNbr1 = new ReportSort(map1).getUnitNbr();
        String unitNbr2 = new ReportSort(map2).getUnitNbr();
        return unitNbr1.compareTo(unitNbr2);
    }
}

public class SortGoodsConsigneeName implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        String goodsConsigneeName1 = new ReportSort(map1).getGoodsConsigneeName();
        String goodsConsigneeName2 = new ReportSort(map2).getGoodsConsigneeName();
        goodsConsigneeName1 = goodsConsigneeName1 == null ? "Z" : goodsConsigneeName1
        goodsConsigneeName2 = goodsConsigneeName2 == null ? "Z" : goodsConsigneeName2
        return goodsConsigneeName1.compareTo(goodsConsigneeName2);
    }
}

public class SortTruckCompanyId implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        String truckCmpyId1 = new ReportSort(map1).getTruckCompanyId();
        String truckCmpyId2 = new ReportSort(map2).getTruckCompanyId();
        return truckCmpyId1.compareTo(truckCmpyId2);
    }
}

public class SortEquipmentType implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        String equipmentType1 = new ReportSort(map1).getEquipmentType();
        String equipmentType2 = new ReportSort(map2).getEquipmentType();
        return equipmentType1.compareTo(equipmentType2);
    }
}

public class SortVesselId implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        String vesselId1 = new ReportSort(map1).getVesselId();
        String vesselId2 = new ReportSort(map2).getVesselId();
        return vesselId1.compareTo(vesselId2);
    }
}

public class SortCreatedDate implements java.util.Comparator {
    public int compare(Object map1, Object map2) {
        Date createdDate1 = new ReportSort(map1).getCreatedDate();
        Date createdDate2 = new ReportSort(map2).getCreatedDate();
        return createdDate1.compareTo(createdDate2);
    }
}

public class ReportSort
{
    private String _declaredIbCarrId;
    private String _unitNbr;
    private String _truckCompanyId;
    private String _vesselId;
    private Date _createdDate;
    private String _goodsConsigneeName;
    private String _equipmentType;

    public ReportSort(Object map)
    {
        HashMap map1 = (HashMap) map;
        _declaredIbCarrId = (String)map1.get("DeclaredIbCarrierId");
        _unitNbr = (String)map1.get("UnitNbr");
        _truckCompanyId = (String)map1.get("truckingCompanyId");
        _vesselId = (String)map1.get("vesselId");
        _createdDate = (Date)map1.get("createdDate");
        _goodsConsigneeName = (String)map1.get("GoodsConsigneeName");
        _equipmentType = (String)map1.get("EquipmentType");
    }


    public void setDeclaredIbCarrierId(String declaredIbCarrierId){
        this._declaredIbCarrId=declaredIbCarrierId;
    }

    public String getDeclaredIbCarrierId(){
        return this._declaredIbCarrId;
    }

    public void setUnitNbr(String unitNbr){
        this._unitNbr=unitNbr;
    }

    public String getUnitNbr(){
        return this._unitNbr;
    }

    public void setGoodsConsigneeName(String goodsConsigneeName){
        this._goodsConsigneeName=goodsConsigneeName;
    }

    public String getGoodsConsigneeName(){
        return this._goodsConsigneeName;
    }

    public void setTruckCompanyId(String truckCompanyId){
        this._truckCompanyId=truckCompanyId;
    }

    public String getTruckCompanyId(){
        return this._truckCompanyId;
    }


    public void setEquipmentType(String equipmentType){
        this._equipmentType=equipmentType;
    }

    public String getEquipmentType(){
        return this._equipmentType;
    }

    public void setVesselId(String vesselId){
        this._vesselId=vesselId;
    }

    public String getVesselId(){
        return this._vesselId;
    }

    public void setCreatedDate(Date createdDate){
        this._createdDate=createdDate;
    }

    public Date getCreatedDate(){
        return this._createdDate;
    }



}//Class Ends

/*
  HashMap map1 = new HashMap();
  map1.put("DeclaredIbCarrierId","MAU726" );
  map1.put("UnitNbr","MATU111111");

  HashMap map5 = new HashMap();
  map5.put("DeclaredIbCarrierId","ALE901" );
  map5.put("UnitNbr","MATU555555");

  HashMap map2 = new HashMap();
  map2.put("DeclaredIbCarrierId","ALE901" );
  map2.put("UnitNbr","MATU222222");

  HashMap map3 = new HashMap();
  map3.put("DeclaredIbCarrierId","HAL904" );
  map3.put("UnitNbr","MATU333333");

  HashMap map4 = new HashMap();
  map4.put("DeclaredIbCarrierId","ALE901" );
  map4.put("UnitNbr","MATU111111");

  ArrayList list = new ArrayList();
  list.add(map1);
  list.add(map5);
  list.add(map2);
  list.add(map3);
  list.add(map4);

*/
