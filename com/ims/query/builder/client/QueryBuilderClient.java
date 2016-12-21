/*
 * Created on Mar 3, 2005
 *
 */
package com.ims.query.builder.client;

import ims.configuration.gen.ConfigFlag;
import ims.framework.enumerations.SystemLogLevel;

import com.ims.query.server.LogReportHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.tree.DefaultElement;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import sun.misc.BASE64Encoder;

import com.ims.query.builder.QueryBuilderEngine;
import com.ims.query.builder.SeedHolder;
import com.ims.query.builder.client.exceptions.QueryBuilderClientException;
import com.ims.query.builder.exceptions.QueryBuilderException;
import com.ims.query.server.HibernateUtil;
import com.ims.query.server.ResultCollection;
import com.ims.query.server.ResultHolder;
import com.ims.report.client.ExportType;
import com.ims.report.client.HttpReportClient;
import com.ims.report.client.exceptions.HttpReportClientException;

/**
 * @author vpurdila
 *
 */
@SuppressWarnings("unchecked")
public class QueryBuilderClient
{
	static final Logger log = Logger.getLogger(QueryBuilderClient.class);
	
	static final String CRNL = "\n";	
	
	public static String PDF = "PDF";
	public static String HTML = "HTML";
	public static String RTF = "RTF";
	public static String FP3 = "FP3";
	public static String CSV = "CSV";
	public static String JPEG = "JPEG";
	public static String TXT = "TXT";
	public static String XLS = "XLS";
	
	private static final int TIMEOUT = 1000 * 60 * 15;
	private static final int MAX_BUFFER_LIMIT_NO_WARNING = 1024*1024;
	private HttpClient client;
	private PostMethod post;

	private String queryServer;
	private String sessionId;
	@SuppressWarnings("rawtypes")
	private ArrayList seeds = new ArrayList();

	private DateFormat qbDateFormat = new SimpleDateFormat("dd/MM/yyyy");
	private DateFormat qbTimeFormat = new SimpleDateFormat("HH:mm");
	private DateFormat qbDateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	/**
	 * Constructor 
	 *
	 * @param queryServer		the QueryServer Url, in most situations it is the same as the app url
	 * @param sessionId			the http session id, used by load balancer for session affinity
	 */		
	public QueryBuilderClient(String queryServer, String sessionId)
	{
		super();
		this.queryServer = queryServer;
		this.sessionId = sessionId;
		
		if(log.isDebugEnabled())
		{
			try
			{
				log.debug("QueryBuilderClient() called on server: " + InetAddress.getLocalHost().getHostName() + " at " + System.getProperty("catalina.home"));
			}
			catch (UnknownHostException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public String getQueryServer()
	{
		return queryServer;
	}
	public void setQueryServer(String queryServer)
	{
		this.queryServer = queryServer;
	}
	@SuppressWarnings("rawtypes")
	public ArrayList getSeeds()
	{
		return seeds;
	}
	@SuppressWarnings("rawtypes")
	public void setSeeds(ArrayList seeds)
	{
		this.seeds = seeds;
	}
	public void addSeed(SeedValue seed)
	{
		seeds.add(seed);
	}

	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies and gets the newly created report as a byte array
	 *
	 * @param reportProject		example:	a String containing the report project
	 * @param reportTemplate	example:	a String containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @return byte[]
	 */	
	public byte[] buildReport(String reportProject, String reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		return buildReport(reportProject.getBytes(), reportTemplate.getBytes(), reportServerUrl, format, printTo, nCopies);
	}
	
	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies 
	 * After the report is created it will be copied to a location given by PDF_STORE_PATH config flag
	 * Returns the name of report including YYYY/MM/DD path 
	 *
	 * @param reportProject		example:	a String containing the report project
	 * @param reportTemplate	example:	a String containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @return String
	 */	
	public String buildReportAndUpload(String reportProject, String reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		return buildReportAndUpload(ConfigFlag.GEN.PDF_STORE_PATH.getValue(), reportProject, reportTemplate, reportServerUrl, format, printTo, nCopies);
	}
	
	public String buildReportAndUpload(String storePath, String reportProject, String reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		return buildReportAndUpload(storePath, reportProject, reportTemplate, reportServerUrl, format, printTo, nCopies, true);//	WDEV-13366
	}
	
	public String buildReportAndUpload(String storePath, String reportProject, String reportTemplate, String reportServerUrl, String format, String printTo, int nCopies, boolean useYMDPath) throws QueryBuilderClientException
	{
		if(storePath == null)
			throw new QueryBuilderClientException("The report project cannot be null !");
		
		String fileName = "";
		String filePath = "";
		String generatedName = "";
		
		if(!(storePath.endsWith("/") || storePath.endsWith("\\")))
			storePath = storePath + "/";
		
		byte[] result = buildReport(reportProject, reportTemplate, reportServerUrl, format, printTo, nCopies);
		
		filePath = storePath;////	WDEV-13366
		
		String ymd = null;
//		WDEV-13366
		if(useYMDPath)
		{
			java.util.Date date = new java.util.Date();
			SimpleDateFormat df = new SimpleDateFormat("yyyy/M/d");
			ymd = df.format(date);
			
			filePath += ymd + "/";
		}
		
		System.out.println("Store path : " + filePath);
		File newDir = new File(filePath);

		if (!newDir.exists()) 
		{
			log.info("Trying to make make new directory: " + newDir.getPath());
			try
			{
				newDir.mkdirs();
				System.out.println("Succesfully created new directory: " + newDir.getPath());
			}
			catch(Exception e)
			{
				throw new QueryBuilderClientException(e);
			}
		}

		generatedName = generateName();
		fileName = filePath + generatedName + "." + format.toLowerCase();
		
		System.out.println("Trying to upload report to: " + fileName);
		
        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream(fileName);
            fos.write(result);
            fos.flush();
            fos.close();
            System.out.println("Succesfully uploaded report to: " + fileName);
        }
        catch(Exception e)
        {
        	System.out.println(e.toString());
            e.printStackTrace();
            throw new QueryBuilderClientException(e);
        }
		
		
		return (useYMDPath ? (ymd + "/") : "") + generatedName + "." + format.toLowerCase();//	WDEV-13366
	}
	
	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies and gets the newly created report as a byte array
	 *
	 * @param reportProject		example:	a byte[] containing the report project
	 * @param reportTemplate	example:	a byte[] containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @deprecated  			use:	public byte[] buildReport(String reportProject, String reportTemplate, String reportServerUrl, ExportType format, String printTo, int nCopies) instead
	 * @return byte[]
	 */	
	public byte[] buildReport(byte[] reportProject, byte[] reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		byte[] result = null;
		
		if(reportProject == null)
			throw new QueryBuilderClientException("The report project cannot be null !");

		if(reportProject.length == 0)
			throw new QueryBuilderClientException("The report project cannot be empty !");

		if(reportTemplate == null)
			throw new QueryBuilderClientException("The report template cannot be null !");

		if(reportTemplate.length == 0)
			throw new QueryBuilderClientException("The report template cannot be empty !");

		if(reportServerUrl == null)
			throw new QueryBuilderClientException("The report server url cannot be null !");

		if(reportServerUrl.length() == 0)
			throw new QueryBuilderClientException("The report server url cannot be empty !");

		if(format == null)
			throw new QueryBuilderClientException("The report format cannot be null !");

		if(format.length() == 0)
			throw new QueryBuilderClientException("The report format cannot be empty !");
		
		if(!(format.equalsIgnoreCase("PDF") || format.equalsIgnoreCase("FP3") || format.equalsIgnoreCase("HTML") || format.equalsIgnoreCase("RTF") || format.equalsIgnoreCase("CSV") || format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("TXT")))
		{
			StringBuffer sb = new StringBuffer(100);
			
			sb.append("Invalid report format '" + format + "' !\r\n");
			sb.append("The allowed report formats are: PDF, RTF, HTML, FP3, CSV, JPEG, TXT");
			
			throw new QueryBuilderClientException(sb.toString());
		}
		
		String xmlSeeds = getXmlSeeds();
		
		client = new HttpClient(new MultiThreadedHttpConnectionManager());
        client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT);		
        client.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
        client.getParams().setIntParameter(HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, MAX_BUFFER_LIMIT_NO_WARNING);
        if(this.sessionId != null)
        {
			try
			{
				setCookies(client);
			}
			catch (MalformedURLException e)
			{
				throw new QueryBuilderClientException(e.toString());
			}
        }

        PostMethod post = new PostMethod(getQueryServerUrl());
		
        NameValuePair[] data = 
        {
          new NameValuePair("project", new String(reportProject)), 
          new NameValuePair("seeds", xmlSeeds),
          new NameValuePair("template", new String(reportTemplate)),
          new NameValuePair("urlServer", reportServerUrl),
          new NameValuePair("format", format),
          new NameValuePair("printTo", printTo),
          new NameValuePair("copies", String.valueOf(nCopies))
        };
        
        post.setRequestBody(data);
		
        int iGetResultCode;
		try
		{
			iGetResultCode = client.executeMethod(post);
			
			if(iGetResultCode == HttpStatus.SC_OK)
	        {
				result = getResponseAsByteArray(post);
	        }
			else
			{
				StringBuffer sb = new StringBuffer(500);
				
				sb.append("buildReport() function returned the error code ");
				sb.append(iGetResultCode);
				sb.append(".\r\n");
				sb.append(new String(getResponseAsByteArray(post)));
				
				throw new QueryBuilderClientException(sb.toString());
			}
		} 
		catch (HttpException e)
		{
			throw new QueryBuilderClientException(e.toString());
		} 
		catch (IOException e)
		{
			throw new QueryBuilderClientException(e.toString());
		}
		finally
		{
			post.releaseConnection();
		}
		
		return result;
	}

	
	private byte[] getResponseAsByteArray(PostMethod post) throws IOException
	{
		InputStream instream = post.getResponseBodyAsStream();
		
		if (instream != null) 
		{
			long contentLength = post.getResponseContentLength();
			
			// guard below cast from overflow
			if (contentLength > Integer.MAX_VALUE) 
			{ 
				throw new IOException("Content too large to be buffered: "+ contentLength +" bytes");
			}
			
			ByteArrayOutputStream outstream = new ByteArrayOutputStream(contentLength > 0 ? (int) contentLength : 4*1024);
			byte[] buffer = new byte[4096];
			int len;
			while ((len = instream.read(buffer)) > 0) 
			{
			    outstream.write(buffer, 0, len);
			}
			outstream.close();

			return outstream.toByteArray();
        }	
		else
			return null;
	}

	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies and gets the newly created report as an Url
	 *
	 * @param reportProject		example:	a String containing the report project
	 * @param reportTemplate	example:	a String containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @return String
	 */	
	public String buildReportAsUrl(String reportProject, String reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		return buildReportAsUrl(reportProject.getBytes(), reportTemplate.getBytes(), reportServerUrl, format, printTo, nCopies);
	}
	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies and gets the newly created report as an url
	 *
	 * @param reportProject		example:	a byte[] containing the report project
	 * @param reportTemplate	example:	a byte[] containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @deprecated  			use:		buildReportAsUrl(String reportProject, String reportTemplate, String reportServerUrl, ExportType format, String printTo, int nCopies) instead
	 * @return String
	 */	
	@SuppressWarnings("rawtypes")
	public String buildReportAsUrl(byte[] reportProject, byte[] reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		byte[] result = null;
		
		long time1 = System.currentTimeMillis();
		LogReportHelper logHelper = new LogReportHelper();
		boolean bLogReport = ConfigFlag.GEN.LOG_REPORT_EXECUTION.getValue();
		boolean bCompress = ConfigFlag.GEN.COMPRESS_REPORT_DATA.getValue();
		StringBuffer systemLog = new StringBuffer();
		DateFormat dtf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		
		if(reportProject == null)
			throw new QueryBuilderClientException("The report project cannot be null !");

		if(reportProject.length == 0)
			throw new QueryBuilderClientException("The report project cannot be empty !");

		if(reportTemplate == null)
			throw new QueryBuilderClientException("The report template cannot be null !");

		if(reportTemplate.length == 0)
			throw new QueryBuilderClientException("The report template cannot be empty !");

		if(reportServerUrl == null)
			throw new QueryBuilderClientException("The report server url cannot be null !");

		if(reportServerUrl.length() == 0)
			throw new QueryBuilderClientException("The report server url cannot be empty !");

		if(format == null)
			throw new QueryBuilderClientException("The report format cannot be null !");

		if(format.length() == 0)
			throw new QueryBuilderClientException("The report format cannot be empty !");
		
		if(!(format.equalsIgnoreCase("PDF") || format.equalsIgnoreCase("FP3") || format.equalsIgnoreCase("HTML") || format.equalsIgnoreCase("RTF") || format.equalsIgnoreCase("CSV") || format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("TXT")))
		{
			StringBuffer sb = new StringBuffer(100);
			
			sb.append("Invalid report format '" + format + "' !\r\n");
			sb.append("The allowed report formats are: PDF, RTF, HTML, FP3, CSV, JPEG, TXT");
			
			throw new QueryBuilderClientException(sb.toString());
		}
		
		String xmlSeeds = getXmlSeeds();

		////////////////////
		Map seeds = new HashMap();
		Boolean multiRowSeed = false;
		ArrayList seedsArray = new ArrayList();

		if(bLogReport)
		{
			systemLog.append("===  Report log execution ===");
			systemLog.append(CRNL);
			systemLog.append("Start time: " + dtf.format(new Date()));
			systemLog.append(CRNL);
			
			systemLog.append(logHelper.getReportInfo(new String(reportTemplate)));			
		}
		
		try
		{
			multiRowSeed = ParseSeedsXml(xmlSeeds, seeds, seedsArray);
		}
		catch (DocumentException e4)
		{
			systemLog.append("Error thrown: " + e4.toString());
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);
			
			throw new QueryBuilderClientException(e4.toString());
		}
		catch (ParseException e4)
		{
			systemLog.append("Error thrown: " + e4.toString());
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);

			throw new QueryBuilderClientException(e4.toString());
		}
		
		Session session = null;
		try
		{
			session = HibernateUtil.currentSession();
		}
		catch (HibernateException e3)
		{
			systemLog.append("Error thrown: " + e3.toString());
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);
			
			throw new QueryBuilderClientException(e3.toString());
		}
		
		
		QueryBuilderEngine engine = null;
		try
		{
			engine = new QueryBuilderEngine();
		}
		catch (QueryBuilderException e1)
		{
			systemLog.append("Error thrown: " + e1.toString());
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);
			
			throw new QueryBuilderClientException(e1.toString());
		}
		
		Object dsXml = "";
		String key;
		
		try
		{
			engine.setSession(session);
			
			//WDEV-10113
			engine.setSystemLog(systemLog);
			
			//WDEV-15182
			engine.setCompressData(bCompress);

			Iterator keys = seeds.keySet().iterator();
			while (keys.hasNext())
			{
				key = (String) keys.next();
				engine.setSeed(key, seeds.get(key));
			}

			if (multiRowSeed != null && multiRowSeed.booleanValue() == true)
				engine.setSeedsArray(seedsArray);
			
			if(bLogReport)
				logHelper.printSeeds(seeds, seedsArray, systemLog, Boolean.TRUE.equals(multiRowSeed));

			dsXml = engine.run(new String(reportProject));
		}
		catch (QueryBuilderException e)
		{
			systemLog.append("Error thrown: " + e.toString());
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);
			
			throw new QueryBuilderClientException(e.toString());
		}
		finally
		{
			try
			{
				HibernateUtil.closeSession();
			}
			catch (HibernateException e2)
			{
				systemLog.append("Error thrown: " + e2.toString());
				logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);
				
				throw new QueryBuilderClientException(e2.toString());
			}
		}
		
		ExportType et = null;
		String mimeType = "text/html";
		if(format.equalsIgnoreCase("FP3"))
		{
			et = ExportType.FP3;
			mimeType = "text/xml";
		}
		else if(format.equalsIgnoreCase("PDF"))
		{
			et = ExportType.PDF;
			mimeType = "application/pdf";
		}
		else if(format.equalsIgnoreCase("HTML"))
		{
			et = ExportType.HTML;
			mimeType = "text/html";
		}
		else if(format.equalsIgnoreCase("RTF"))
		{
			et = ExportType.RTF;
			mimeType = "application/msword";
		}
		else if(format.equalsIgnoreCase("CSV"))
		{
			et = ExportType.CSV;
			mimeType = "text/csv";
		}
		else if(format.equalsIgnoreCase("DS"))
		{
			et = ExportType.DS;
			mimeType = "text/html";
		}
		else if(format.equalsIgnoreCase("JPEG"))
		{
			et = ExportType.JPEG;
			mimeType = "image/jpeg";
		}
		else if(format.equalsIgnoreCase("TXT"))
		{
			et = ExportType.TXT;
			mimeType = "text/plain";
		}
		else if(format.equalsIgnoreCase("XLS"))
		{
			et = ExportType.XLS;
			mimeType = "application/excel";
		}
/*		response.setContentType(mimeType);
		
		if(mimeType.equalsIgnoreCase("text/xml"))
			response.setHeader("Content-Disposition","inline; filename=report.xml");
		else if(mimeType.equalsIgnoreCase("application/pdf"))
			response.setHeader("Content-Disposition","inline; filename=report.pdf");
		else if(mimeType.equalsIgnoreCase("application/msword"))
			response.setHeader("Content-Disposition","inline; filename=report.rtf");
		else if(mimeType.equalsIgnoreCase("text/csv"))
			response.setHeader("Content-Disposition","inline; filename=report.csv");
		else if(mimeType.equalsIgnoreCase("text/html"))
			response.setHeader("Content-Disposition","inline; filename=report.txt");
*/
		HttpReportClient client = new HttpReportClient();		
		try
		{
			if(dsXml instanceof String)
			{
				String ds = (String) dsXml;
				result = client.buildReport(reportServerUrl, reportTemplate, ds.getBytes(), et, printTo, nCopies);
			}
			else
			{
				byte[] ds = (byte[]) dsXml;
				result = client.buildReport(reportServerUrl, reportTemplate, ds, et, printTo, nCopies);
			}
		} 
		catch (HttpReportClientException e2)
		{
			systemLog.append("Error thrown: " + e2.toString());
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport, SystemLogLevel.FATALERROR);
			
			throw new QueryBuilderClientException(e2.toString());
		}

		CRC32 crc = new CRC32();
		crc.reset();
		if(result != null)
			crc.update(result);

		key = String.valueOf(System.currentTimeMillis());
		key += sessionId != null ? sessionId : "";
		key += String.valueOf(crc.getValue());
		
		ResultCollection.putResult(key, new ResultHolder(result, mimeType));
		
		String retVal;
		try
		{
			retVal = "/ReturnAsUrlServlet?action=getResult&id=" + key + "&appservername=" + InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e)
		{
			throw new QueryBuilderClientException(e.toString());
		}
		
		if(bLogReport)
		{
			long time2 = System.currentTimeMillis();

			systemLog.append("End time: " + dtf.format(new Date()));
			systemLog.append(CRNL);
			systemLog.append("Report execution time: " + (time2 - time1) + " ms");
			//systemLog.append(CRNL);
			//systemLog.append("Remote host: " + request.getRemoteAddr());
			
			logHelper.createSystemLogEntry(systemLog.toString(), bLogReport);
		}
		
		return getQueryServerRoot() + retVal;
		///////////////////
	}

	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies and gets the newly created report as an Url inline frame
	 *
	 * @param reportProject		example:	a String containing the report project
	 * @param reportTemplate	example:	a String containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @return String
	 */	
	public String buildReportAsUrlInlineFrame(String reportProject, String reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		return buildReportAsUrlInlineFrame(reportProject.getBytes(), reportTemplate.getBytes(), reportServerUrl, format, printTo, nCopies);
	}
	
	/**
	 * Connects to the query server, passes down the report project, report template, report server url, 
	 * report type, printer name and number of copies and gets the newly created report as an url inline frame
	 *
	 * @param reportProject		example:	a String containing the report project
	 * @param reportTemplate	example:	a String containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "FP3" or "CSV"		
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 *  
	 * @deprecated  			use:		buildReportAsUrlInlineFrame(String reportProject, String reportTemplate, String reportServerUrl, ExportType format, String printTo, int nCopies) instead
	 * @return String
	 */	
	public String buildReportAsUrlInlineFrame(byte[] reportProject, byte[] reportTemplate, String reportServerUrl, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		String url = buildReportAsUrl(reportProject, reportTemplate, reportServerUrl, format, printTo, nCopies);
		
		url = "<IFRAME id=\"PostFrame\" name=\"PostFrame\" width=\"100%\" height=\"100%\" frameborder=0 src='" + url + "'></IFRAME>";
		
		return url;
	}

	/**
	 * Passes down a report in PDF, HTML or RTF format and return an url for wiewing inside of an IFRAME 
	 * @param report			example:	a byte[] containing the report
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "CSV"
	 * @return String
	 * @throws QueryBuilderClientException
	 */
	public String prepareReportForViewingInlineFrame(byte[] report, String format) throws QueryBuilderClientException
	{
		String url = prepareReportForViewing(report, format);
		
		url = "<IFRAME id=\"PostFrame\" name=\"PostFrame\" width=\"100%\" height=\"100%\" frameborder=0 src='" + url + "'></IFRAME>";
		
		return url;
	}

	/**
	 * Passes down a report in PDF, HTML or RTF format and return an url for wiewing it in the browser 
	 * @param report			example:	a byte[] containing the report
	 * @param format			example:	"PDF" or "HTML" or "RTF" or "CSV"
	 * @return String
	 * @throws QueryBuilderClientException
	 */
	public String prepareReportForViewing(byte[] report, String format) throws QueryBuilderClientException
	{
		byte[] result = null;
		
		if(report == null)
			throw new QueryBuilderClientException("The report cannot be null !");

		if(report.length == 0)
			throw new QueryBuilderClientException("The report cannot be empty !");

		if(format == null)
			throw new QueryBuilderClientException("The report format cannot be null !");

		if(format.length() == 0)
			throw new QueryBuilderClientException("The report format cannot be empty !");
		
		if(!(format.equalsIgnoreCase("PDF") || format.equalsIgnoreCase("FP3") || format.equalsIgnoreCase("HTML") || format.equalsIgnoreCase("RTF") || format.equalsIgnoreCase("CSV") || format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("TXT")))
		{
			StringBuffer sb = new StringBuffer(100);
			
			sb.append("Invalid report format '" + format + "' !\r\n");
			sb.append("The allowed report formats are: PDF, RTF, HTML, FP3, CSV, JPEG, TXT");
			
			throw new QueryBuilderClientException(sb.toString());
		}
		
		client = new HttpClient(new MultiThreadedHttpConnectionManager());
        client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT);		
        client.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
        client.getParams().setIntParameter(HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, MAX_BUFFER_LIMIT_NO_WARNING);
        if(this.sessionId != null)
        {
			try
			{
				setCookies(client);
			}
			catch (MalformedURLException e)
			{
				throw new QueryBuilderClientException(e.toString());
			}
        }

		PostMethod post = new PostMethod(getQueryServerRoot() + "/ReturnAsUrlServlet");
		
		BASE64Encoder enc = new BASE64Encoder();
		
        NameValuePair[] data = 
        {
          new NameValuePair("action", "putResult"), 
          new NameValuePair("result", new String(enc.encode(report))),
          new NameValuePair("type", format)
        };
        
        post.setRequestBody(data);
		
        int iGetResultCode;
		try
		{
			iGetResultCode = client.executeMethod(post);
			
			if(iGetResultCode == HttpStatus.SC_OK)
	        {
				result = getResponseAsByteArray(post);
	        }
			else
			{
				StringBuffer sb = new StringBuffer(500);
				
				sb.append("prepareReportForViewing() function returned the error code ");
				sb.append(iGetResultCode);
				sb.append(".\r\n");
				sb.append(new String(getResponseAsByteArray(post)));
				
				throw new QueryBuilderClientException(sb.toString());
			}
		} 
		catch (HttpException e)
		{
			throw new QueryBuilderClientException(e.toString());
		} 
		catch (IOException e)
		{
			throw new QueryBuilderClientException(e.toString());
		}
		finally
		{
			post.releaseConnection();
		}
		
		String url = getQueryServerRoot() + new String(result);
	
		return url;
	}

	public String getWaitUrl() 
	{
		return "<IFRAME id=\"PostFrame\" name=\"PostFrame\" width=\"100%\" height=\"100%\" frameborder=0 src='" + this.getQueryServerRoot() + "/wait.html' onLoad=\"return HTMLViewerClick(1);\"></IFRAME>";
	}
	
	/**
	 * Returns a list of available printers on 'reportServerUrl' server
	 * 
	 * @param reportServerUrl
	 * @return
	 * @throws QueryBuilderClientException
	 */
	@SuppressWarnings("rawtypes")
	public PrinterAvailableCollection listPrinters(String reportServerUrl) throws QueryBuilderClientException
	{
		PrinterAvailableCollection coll = new PrinterAvailableCollection();

		client = new HttpClient(new MultiThreadedHttpConnectionManager());
        client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT);		
        client.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
        client.getParams().setIntParameter(HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, MAX_BUFFER_LIMIT_NO_WARNING);

		String servlet = "/listPrinters";
		PostMethod post = null;
		byte[] result;
		
        if(reportServerUrl.endsWith("/"))
        	servlet = "listPrinters";
        
        post = new PostMethod(reportServerUrl + servlet);
		
        int iGetResultCode;
		try
		{
			iGetResultCode = client.executeMethod(post);
			
			if(iGetResultCode == HttpStatus.SC_OK)
	        {
				result = getResponseAsByteArray(post);
	        }
			else
			{
				StringBuffer sb = new StringBuffer(500);
				
				sb.append("listPrinters() function returned the error code ");
				sb.append(iGetResultCode);
				sb.append(".\r\n");
				sb.append(new String(getResponseAsByteArray(post)));
				
				throw new QueryBuilderClientException(sb.toString());
			}
		} 
		catch (HttpException e)
		{
			throw new QueryBuilderClientException(e.toString());
		} 
		catch (IOException e)
		{
			throw new QueryBuilderClientException(e.toString());
		}
		finally
		{
			post.releaseConnection();
		}
		
		Document maindoc = null;
		try
		{
			maindoc = getXmlDocument(new String(result));
		} 
		catch (DocumentException e1)
		{
			throw new QueryBuilderClientException(e1);			
		}
		
		List list = maindoc.selectNodes("/printers/listOfPrinters/printer");
		
		for (Iterator iter = list.iterator(); iter.hasNext();)
		{
			DefaultElement printer = (DefaultElement) iter.next();

			coll.add(new PrinterAvailable(printer.valueOf("PrinterName"), printer.valueOf("ShareName"), printer.valueOf("Location"), printer.valueOf("PortName")));
		}
		
		return coll;
	}
	
	/**
	 * Prints a report
	 * @param reportProject		example:	a byte[] containing the report project
	 * @param reportTemplate	example:	a byte[] containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 * @return
	 * @throws QueryBuilderClientException
	 */
	public boolean printReport(String reportProject, String reportTemplate, String reportServerUrl, String printTo, int nCopies) throws QueryBuilderClientException
	{
		return printReport(reportProject.getBytes(), reportTemplate.getBytes(), reportServerUrl, printTo, nCopies);
	}
	/**
	 * Prints a report
	 * @param reportProject		example:	a byte[] containing the report project
	 * @param reportTemplate	example:	a byte[] containing the template
	 * @param reportServerUrl	example:	http://webapps/ImsReportServerCgi.exe
	 * @param printTo			example:	HPLASERJET4	
	 * @param nCopies			example:	2 //prints 2 copies		
	 * @return
	 * @throws QueryBuilderClientException
	 * @deprecated				use:		printReport(String reportProject, String reportTemplate, String reportServerUrl, String printTo, int nCopies) instead
	 */
	public boolean printReport(byte[] reportProject, byte[] reportTemplate, String reportServerUrl, String printTo, int nCopies) throws QueryBuilderClientException
	{
		byte[] result = null;
		
		if(reportProject == null)
			throw new QueryBuilderClientException("The report project cannot be null !");

		if(reportProject.length == 0)
			throw new QueryBuilderClientException("The report project cannot be empty !");

		if(reportTemplate == null)
			throw new QueryBuilderClientException("The report template cannot be null !");

		if(reportTemplate.length == 0)
			throw new QueryBuilderClientException("The report template cannot be empty !");

		if(reportServerUrl == null)
			throw new QueryBuilderClientException("The report server url cannot be null !");

		if(reportServerUrl.length() == 0)
			throw new QueryBuilderClientException("The report server url cannot be empty !");

		if(printTo == null)
			throw new QueryBuilderClientException("The printer name cannot be null !");

		if(printTo.length() == 0)
			throw new QueryBuilderClientException("The printer name cannot be empty !");
		
		String xmlSeeds = getXmlSeeds();
		
		client = new HttpClient(new MultiThreadedHttpConnectionManager());
        client.getHttpConnectionManager().getParams().setConnectionTimeout(TIMEOUT);		
        client.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
        client.getParams().setIntParameter(HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, MAX_BUFFER_LIMIT_NO_WARNING);
        if(this.sessionId != null)
        {
			try
			{
				setCookies(client);
			}
			catch (MalformedURLException e)
			{
				throw new QueryBuilderClientException(e.toString());
			}
        }
        
        PostMethod post = new PostMethod(getQueryServerUrlForPrinting());
		
        NameValuePair[] data = 
        {
          new NameValuePair("project", new String(reportProject)), 
          new NameValuePair("seeds", xmlSeeds),
          new NameValuePair("template", new String(reportTemplate)),
          new NameValuePair("urlServer", reportServerUrl),
          new NameValuePair("printTo", printTo),
          new NameValuePair("copies", String.valueOf(nCopies))
        };
        
        post.setRequestBody(data);
		
        int iGetResultCode;
		try
		{
			iGetResultCode = client.executeMethod(post);
			
			if(iGetResultCode == HttpStatus.SC_OK)
	        {
				result = getResponseAsByteArray(post);
	        }
			else
			{
				StringBuffer sb = new StringBuffer(500);
				
				sb.append("printReport() function returned the error code ");
				sb.append(iGetResultCode);
				sb.append(".\r\n");
				sb.append(new String(getResponseAsByteArray(post)));
				
				throw new QueryBuilderClientException(sb.toString());
			}
		} 
		catch (HttpException e)
		{
			throw new QueryBuilderClientException(e.toString());
		} 
		catch (IOException e)
		{
			throw new QueryBuilderClientException(e.toString());
		}
		finally
		{
			post.releaseConnection();
		}
		
		return (new String(result)).equalsIgnoreCase("true");
	}

	/**
	 * Prints directly a prepared to the report server, bypasses QueryServer
	 * 
	 * @param preparedReport
	 * @param reportServerUrl
	 * @param printTo
	 * @param nCopies
	 * @return
	 * @throws QueryBuilderClientException
	 */
	public boolean printReport(byte[] preparedReport, String reportServerUrl, String printTo, int nCopies) throws QueryBuilderClientException
	{
		//prints directly to the report server, bypasses QueryServer
		byte[] result = null;
		
		if(preparedReport == null)
			throw new QueryBuilderClientException("The prepared report cannot be null !");

		if(reportServerUrl == null)
			throw new QueryBuilderClientException("The report server url cannot be null !");

		if(printTo == null)
			throw new QueryBuilderClientException("The printer name cannot be null !");

		if(printTo.length() == 0)
			throw new QueryBuilderClientException("The printer name cannot be empty !");
		
		HttpReportClient httpReportClient = new HttpReportClient();

		try
		{
			result = httpReportClient.printReport(getReportServerUrlForPrintingPreparedReport(reportServerUrl), preparedReport, printTo, nCopies);
		} 
		catch (HttpReportClientException e)
		{
			throw new QueryBuilderClientException(e);
		}
		finally
		{
		}
		
		return (new String(result)).equalsIgnoreCase("true");
	}
	
	private String getXmlSeeds() throws QueryBuilderClientException
	{
		int nSeeds = (seeds == null ? 0 : seeds.size());
		
		if(nSeeds == 0)
			nSeeds = 1;
		
		StringBuffer sb = new StringBuffer(100 * nSeeds);
		SeedValue seed;
		String strVal;
		
		sb.append("<seeds>");
			for(int i = 0; i < seeds.size(); i++)
			{
				seed = (SeedValue)seeds.get(i);
				
				if(seed == null)
					throw new QueryBuilderClientException("The seed cannot be null !");

				if(seed.getClazz().getName().equalsIgnoreCase("java.sql.Date"))
					strVal = seed.getValue() == null ? "" : qbDateFormat.format(seed.getValue());
				else if(seed.getClazz().getName().equalsIgnoreCase("java.sql.Time"))
					strVal = seed.getValue() == null ? "" : qbTimeFormat.format(seed.getValue());
				else if(seed.getClazz().getName().equalsIgnoreCase("java.util.Date"))
					strVal = seed.getValue() == null ? "" : qbDateTimeFormat.format(seed.getValue());
				else
					strVal = seed.getValue() == null ? "" : seed.getValue().toString();
				
				sb.append("<seed>");
					sb.append("<name>");
						sb.append(StringUtils.encodeXML(seed.getName()));
					sb.append("</name>");
					sb.append("<value>");
						sb.append(seed.getValue() == null ? "" : StringUtils.encodeXML(strVal));
					sb.append("</value>");
					sb.append("<type>");
						sb.append(seed.getClazz().getName());
					sb.append("</type>");
				sb.append("</seed>");
			}
		sb.append("</seeds>");
		
		return sb.toString();
	}
	
	private String getQueryServerRoot()
	{
		String root = queryServer.trim();
		
		if(root.endsWith("/"))
			root = root.substring(0,root.length() - 1);
		
		if(root.endsWith("/ReportBuilder"))
		{
			int index = root.lastIndexOf("/");
			
			if(index > -1)
			{
				return root.substring(0, index);
			}
		}
		
		return root;
	}
	
	private String getQueryServerUrl()
	{
		String root = queryServer.trim();

		if(root.endsWith("/"))
			root = root.substring(0,root.length() - 1);

		if(root.endsWith("/ReportBuilder"))
			return root;
		
		root += "/ReportBuilder";
		
		return root;
	}

	private String getQueryServerUrlForPrinting()
	{
		String root = queryServer.trim();

		if(root.endsWith("/"))
			root = root.substring(0,root.length() - 1);

		if(root.endsWith("/PrintReport"))
			return root;
		
		root += "/PrintReport";
		
		return root;
	}

	private String getReportServerUrlForPrintingPreparedReport(String server)
	{
		String root = server.trim();

		if(root.endsWith("/"))
			root = root.substring(0,root.length() - 1);

		if(root.endsWith("/PrintPreparedReport"))
			return root;
		
		root += "/PrintPreparedReport";
		
		return root;
	}

	private String getReportServerUrlForConvertingPreparedReport(String server)
	{
		String root = server.trim();

		if(root.endsWith("/"))
			root = root.substring(0,root.length() - 1);

		if(root.endsWith("/ConvertReport"))
			return root;
		
		root += "/ConvertReport";
		
		return root;
	}

	private String getReportServerUrlForConvertingOfficeDocument(String server)
	{
		String root = server.trim();

		if(root.endsWith("/"))
			root = root.substring(0,root.length() - 1);

		if(root.endsWith("/ConvertOfficeDocument"))
			return root;
		
		root += "/ConvertOfficeDocument";
		
		return root;
	}
	
	private Document getXmlDocument(String xmlBuffer) throws DocumentException
	{
		return DocumentHelper.parseText(xmlBuffer);
	}

	public byte[] convertReport(String reportServerUrl, byte[] preparedReport, String format, String printTo, int nCopies) throws QueryBuilderClientException
	{
		byte[] result = null;
		
		if(preparedReport == null)
			throw new QueryBuilderClientException("The prepared report cannot be null !");

		if(preparedReport.length == 0)
			throw new QueryBuilderClientException("The prepared report cannot be empty !");

		if(reportServerUrl == null)
			throw new QueryBuilderClientException("The report server url cannot be null !");

		if(reportServerUrl.length() == 0)
			throw new QueryBuilderClientException("The report server url cannot be empty !");

		if(format == null)
			throw new QueryBuilderClientException("The report format cannot be null !");

		if(format.length() == 0)
			throw new QueryBuilderClientException("The report format cannot be empty !");
		
		if(!(format.equalsIgnoreCase("PDF") || format.equalsIgnoreCase("FP3") || format.equalsIgnoreCase("HTML") || format.equalsIgnoreCase("RTF") || format.equalsIgnoreCase("CSV") || format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("TXT")))
		{
			StringBuffer sb = new StringBuffer(100);
			
			sb.append("Invalid report format '" + format + "' !\r\n");
			sb.append("The allowed report formats are: PDF, RTF, HTML, FP3, CSV, JPEG, TXT");
			
			throw new QueryBuilderClientException(sb.toString());
		}
		
		ExportType et = ExportType.PDF;
		
		if(format.equalsIgnoreCase("PDF"))
			et = ExportType.PDF;
		else if(format.equalsIgnoreCase("RTF"))
			et = ExportType.RTF;
		if(format.equalsIgnoreCase("HTML"))
			et = ExportType.HTML;
		if(format.equalsIgnoreCase("FP3"))
			et = ExportType.FP3;
		if(format.equalsIgnoreCase("CSV"))
			et = ExportType.CSV;
		if(format.equalsIgnoreCase("JPEG"))
			et = ExportType.JPEG;
		if(format.equalsIgnoreCase("TXT"))
			et = ExportType.TXT;
		if(format.equalsIgnoreCase("XLS"))
			et = ExportType.XLS;
		
		HttpReportClient httpReportClient = new HttpReportClient();

		try
		{
			result = httpReportClient.convertReport(getReportServerUrlForConvertingPreparedReport(reportServerUrl), preparedReport, et, printTo, nCopies);
		} 
		catch (HttpReportClientException e)
		{
			throw new QueryBuilderClientException(e);
		}
		finally
		{
		}
		
		return result;
	}

	public byte[] convertOfficeDocument(String reportServerUrl, byte[] officeDocument, String format) throws QueryBuilderClientException
	{
		byte[] result = null;
		
		if(officeDocument == null)
			throw new QueryBuilderClientException("The office document cannot be null !");

		if(officeDocument.length == 0)
			throw new QueryBuilderClientException("The office document cannot be empty !");

		if(reportServerUrl == null)
			throw new QueryBuilderClientException("The report server url cannot be null !");

		if(reportServerUrl.length() == 0)
			throw new QueryBuilderClientException("The report server url cannot be empty !");

		if(format == null)
			throw new QueryBuilderClientException("The report format cannot be null !");

		if(format.length() == 0)
			throw new QueryBuilderClientException("The report format cannot be empty !");
		
		if(!(format.equalsIgnoreCase("PDF") || format.equalsIgnoreCase("RTF") || format.equalsIgnoreCase("DOC") || format.equalsIgnoreCase("DOCX") || format.equalsIgnoreCase("MHT")))
		{
			StringBuffer sb = new StringBuffer(100);
			
			sb.append("Invalid report format '" + format + "' !\r\n");
			sb.append("The allowed report formats are: PDF, RTF, DOC, DOCX, MHT");
			
			throw new QueryBuilderClientException(sb.toString());
		}
		
		ExportType et = ExportType.PDF;
		
		if(format.equalsIgnoreCase("PDF"))
			et = ExportType.PDF;
		else if(format.equalsIgnoreCase("RTF"))
			et = ExportType.RTF;
		else if(format.equalsIgnoreCase("DOC"))
			et = ExportType.DOC;
		else if(format.equalsIgnoreCase("DOCX"))
			et = ExportType.DOCX;
		else if(format.equalsIgnoreCase("MHT"))
			et = ExportType.MHT;
		
		HttpReportClient httpReportClient = new HttpReportClient();

		try
		{
			result = httpReportClient.convertOfficeDocument(getReportServerUrlForConvertingOfficeDocument(reportServerUrl), officeDocument, et);
		} 
		catch (HttpReportClientException e)
		{
			throw new QueryBuilderClientException(e);
		}
		finally
		{
		}
		
		return result;
	}
	
	private void setCookies(HttpClient client) throws MalformedURLException
	{
		HttpState initialState = new HttpState();
		
        Cookie mycookie = new Cookie(getHostNameFromUrl(this.queryServer), "JSESSIONID", this.sessionId, "/", null, false);
        
        initialState.addCookie(mycookie);
        client.setState(initialState);
        client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
	}
	
	public synchronized static String getHostNameFromUrl(String url) throws MalformedURLException
	{
		URL url1 = null;
		
		url1 = new URL(url);
		
		return url1.getHost();
	}
	
	@SuppressWarnings("rawtypes")
	private boolean ParseSeedsXml(String xmlSeeds, Map seeds, ArrayList seedsArray) throws DocumentException, ParseException
	{
		String name;
		String value;
		String type;
		
		boolean multiRowSeed = false;
		
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy");
		DateFormat tf = new SimpleDateFormat("HH:mm");
		DateFormat dtf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		
		if(xmlSeeds == null)
			return false;
		
		Document maindoc = getXmlDocument(xmlSeeds);

		List list = maindoc.selectNodes("/seeds/seed");
		
		Object obj;
		for (Iterator iter = list.iterator(); iter.hasNext();)
		{
			DefaultElement seed = (DefaultElement) iter.next();

			name = seed.valueOf("name");
			value = seed.valueOf("value");
			type = seed.valueOf("type");
			
			obj = null;
			if(type.equalsIgnoreCase("java.lang.Integer"))
			{
				if(value.length() > 0)
					obj = Integer.valueOf(value);
			}
			else if(type.equalsIgnoreCase("java.math.BigInteger"))
			{
				if(value.length() > 0)
					obj = new BigInteger(value);
			}
			else if(type.equalsIgnoreCase("java.lang.Long"))
			{
				if(value.length() > 0)
					obj = new Long(value);
			}
			else if(type.equalsIgnoreCase("java.lang.Short"))
			{
				if(value.length() > 0)
					obj = new Short(value);
			}
			else if(type.equalsIgnoreCase("java.lang.Boolean"))
			{
				if(value.length() > 0)
					obj = new Boolean(value);
			}
			else if(type.equalsIgnoreCase("java.lang.String"))
			{
				if(value.length() > 0)
					obj = new String(value);
			}
			else if(type.equalsIgnoreCase("java.math.BigDecimal"))
			{
				if(value.length() > 0)
					obj = new BigDecimal(value);
			}
			else if(type.equalsIgnoreCase("java.lang.Float"))
			{
				if(value.length() > 0)
					obj = new Float(value);
			}
			else if(type.equalsIgnoreCase("java.lang.Double"))
			{
				if(value.length() > 0)
					obj = new Double(value);
			}
			else if(type.equalsIgnoreCase("java.sql.Date"))
			{
				if(value.length() > 0)
					obj = new java.sql.Date(df.parse(value).getTime());
			}
			else if(type.equalsIgnoreCase("java.sql.Time"))
			{
				if(value.length() > 0)
					obj = tf.parse(value);
			}
			else if(type.equalsIgnoreCase("java.util.Date"))
			{
				if(value.length() > 0)
					obj = dtf.parse(value);
			}
			else
			{
				if(value.length() > 0)
					obj = Integer.valueOf(value);
			}

			if(seeds.get(name) != null)
				multiRowSeed = true;
			
			seeds.put(name, new SeedHolder(name, type, obj));
			seedsArray.add(new SeedHolder(name, type, obj));
		}
		
		return multiRowSeed;
	}
	
	private String generateName() 
	{
		String str = "";

		try
		{
			//Get Random Segment
			SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
			str += Integer.toHexString(prng.nextInt());
			while (str.length() < 8)
			{
				str = '0' + str;
			}

			//Get CurrentTimeMillis() segment
			str += Long.toHexString(System.currentTimeMillis());
			while (str.length() < 12)
			{
				str = '0' + str;
			}

			//Get Random Segment
			SecureRandom secondPrng = SecureRandom.getInstance("SHA1PRNG");
			str += Integer.toHexString(secondPrng.nextInt());
			while (str.length() < 8)
			{
				str = '0' + str;
			}

			//Get IdentityHash() segment
			str += Long.toHexString(System.identityHashCode((Object) this));
			while (str.length() < 8)
			{
				str = '0' + str;
			}
			//Get Third Random Segment
			byte bytes[] = new byte[16];
			SecureRandom thirdPrng = SecureRandom.getInstance("SHA1PRNG");
			thirdPrng.nextBytes(bytes);
			str += Integer.toHexString(thirdPrng.nextInt());
			while (str.length() < 8)
			{
				str = '0' + str;
			}
		}
		catch (java.security.NoSuchAlgorithmException ex)
		{
			ex.getMessage();
		}

		return str;
	}
	
}

