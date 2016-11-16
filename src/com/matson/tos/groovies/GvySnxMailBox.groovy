import com.navis.apex.business.model.GroovyInjectionBase;
import java.io.File;
import java.io.FileReader
import java.io.BufferedReader
import java.lang.StringBuilder
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Unmarshaller
import javax.xml.bind.Marshaller
import java.io.StringWriter
import com.navis.argo.util.XmlUtil
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;


public class GvySnxMailBox extends GroovyInjectionBase {

    String path = "/var/tmp/snxmailbox/errors";
    String archivePath = "/var/tmp/snxmailbox/archive";

    public String execute(Map inParameters) {
        println("Groovy test started");
        def snxObj = null;
        String fileStr = null;
        String fileName = null;
        def units = null;
        try{
            File dir = new File(path);
            File[] files = dir.listFiles();
            println("directory read test");
            for (File file : files){
                fileName = file.getName()
                println("PICKED ERROR FILE ---"+fileName);
                processFile(file)
                println("PROCESSED ERROR FILE ---"+fileName);
                copyfile(file, archivePath+"/"+fileName) //Copy File to ARchive Folder
                file.delete();
                println("DELETE ERROR FILE ---"+fileName);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void processFile(File file){
        try{
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = null;
            String errorMsg = null;
            String strUnit = null;
            String line = null;
            while ((line = br.readLine()) != null){

                if(sb == null){ sb = new StringBuilder() } //Create Buffer For First unit

                if(line == null || line.trim().length() == 0 ){
                    sendXmlOut(sb.toString())
                    sb = new StringBuilder();
                }else if(line.contains('ns2:snx')){
                    if(line.contains('</ns2:snx')){
                        sendXmlOut(sb.toString())  //Post last Unit
                    }
                }else if(line.contains('<!--Error')){
                    line = line.replace('<!--Error','');
                    line = line.replace('-->','');
                    errorMsg = "<argo:snx-error xmlns:argo='http://www.navis.com/argo' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='http://www.navis.com/argo snxError.xsd'><argo:reason>"+line+"</argo:reason><argo:payload>"
                    sb.append(errorMsg)
                }else if(line.contains('<unit')){
                    println("Error Unit:"+line)
                    line = line.replace('<','&lt;');
                    line = line.replace('>','&gt;');
                    strUnit = "&lt;?xml version='1.0' encoding='UTF-8' standalone='yes'?&gt;&lt;ns2:snx xmlns:ns2='http://www.navis.com/argo'&gt;"+line+"&lt;/ns2:snx&gt;</argo:payload></argo:snx-error>"
                    sb.append(strUnit);

                }
            }
            br.close();
        }catch(Exception e){
            e.printStackTrace();
        }
        //return null;
    }

    public void sendXmlOut(String xml){
        try{
            sendXml(xml);
            //println("sendXml  ----------"+xml);
        }catch(Exception e){
            e.printstackTrace();
            //Add Code to Put intp Integration Errors
        }
    }

    private static void copyfile(File f1, String dtFile){
        try
        {
            File f2 = new File(dtFile);
            InputStream input = new FileInputStream(f1);

            //For Overwrite the file.
            OutputStream output = new FileOutputStream(f2);

            byte[] buf = new byte[1024];
            int len;
            while ((len = input.read(buf)) > 0){
                output.write(buf, 0, len);
            }
            input.close();
            output.close();
            println("--- Error File copied ---");
        }
        catch(Exception ex){
            ex.printStackTrace()
            throw ex;
        }
    }

}