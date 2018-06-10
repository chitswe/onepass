package com.fgtit.net;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class HttpConnSoap2
{
    public InputStream GetWebServre(String serverUrl,String methodName, ArrayList<String> Parameters, ArrayList<String> ParValues)
    {
    	String ServerUrl = serverUrl;
        String soapAction = "http://www.fgtit.com/" + methodName;
        String soap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                      + "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                      + "<soap:Body>";
        String tps, vps, ts;
        String mreakString = "";
        mreakString = "<" + methodName + " xmlns=\"http://www.fgtit.com/\">";
        
        for (int i = 0; i < Parameters.size(); i++)
        {
            tps = Parameters.get (i).toString();
            vps = ParValues.get (i).toString();	
            ts = "<" + tps + ">" + vps + "</" + tps + ">";
            mreakString = mreakString + ts;
        }
        mreakString = mreakString + "</" + methodName + ">";
        String soap2 = "</soap:Body>"
        			   +"</soap:Envelope>";
        String requestData = soap + mreakString + soap2;

        try
        {
        	//StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build());
        	
            URL url = new URL (ServerUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            byte[] bytes = requestData.getBytes ("utf-8");
            con.setDoInput (true);
            con.setDoOutput (true);
            con.setUseCaches (false);
            con.setConnectTimeout (6000);
            con.setRequestMethod ("POST");
            con.setRequestProperty ("Content-Type", "text/xml;charset=utf-8");
            con.setRequestProperty ("SOAPAction", soapAction);
            con.setRequestProperty ("Content-Length", "" + bytes.length);

            OutputStream outStream = con.getOutputStream();
            outStream.write (bytes);
            outStream.flush();
            outStream.close();

            InputStream inputStream = con.getInputStream();
            return inputStream;

        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
    
    /*
HttpConnSoap2 webservice = new HttpConnSoap2();
String methodName = "showReview";
ArrayList<String> paramList = new ArrayList<String>();
ArrayList<String> parValueList = new ArrayList<String>();
ArrayList<CommentInfor>() resultList = new ArrayList<CommentInfor>();

paramList.add ("ID");
parValueList.add ("001");

InputStream inputStream = webservice.GetWebServre (methodName, paramList, parValueList);
resultList = XMLParase.paraseCommentInfors (inputStream); 
     */

}