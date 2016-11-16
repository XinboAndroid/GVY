import com.navis.apex.business.model.GroovyInjectionBase;
import java.sql.ResultSet;
import java.sql.Connection;

class MatCheckNvProcess {
    public String getNv(String vesVoy) {
        //public void execute(Map params) {
        try{
            String result = "Y";
            //String vesVoy = "MAU728";
            String cnt = "0";
            int vesCnt = 0;
            def inj = new GroovyInjectionBase();
            HashMap reportMap = null;
            def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");


            println("MatCheckNvProcess - Start")
            cnt = GvyRefDataLookup.lookupNv(vesVoy);
            vesCnt = cnt.toInteger()
            println("vesCnt ::::::::::::"+vesCnt);

            if (vesCnt == 0 || vesCnt >= 1){
                result = "Y";
            } else {
                result = "N";
            }
            println("No of records in process logger table for :: "+vesVoy +" :: Is :"+vesCnt +" :: Result ::"+result);

            return result;
        } catch (Exception e){
            println ("Error in MatCheckNvProcess ::"+e);
            return result;

        }

    }
}