/*
 * Created on 2 Nov 2012
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package com.ims.query.builder.client;

import com.ims.query.builder.ReportTemplateManifest1;

/**
 * This class is just a proxy to a class defined in QueryBuilder3.jar
 * It is a helper class to return manifest information from a QueryBuilder project or template
 * 
 * @author Vasile Purdila
 * @version 1.0 
 */
public class ReportTemplateManifest
{
	/**
	 * Returns the template type from a FastReport template
	 * <p>
	 * @param  templateXml  a string that contains the FastReport template
	 * @return      FastReport or MSWord as strings
	 */    
	public String getTemplateType(String templateXml)
	{
		ReportTemplateManifest1 obj = new ReportTemplateManifest1();
		
		return obj.getTemplateType(templateXml);
	}
	
	/**
	 * Extracts the manifest info from a FastReport template
	 * <p>
	 * @param  templateXml  a string that contains the FastReport template
	 * @return      template manifest as a string
	 */    
	public String extractTemplateInfoFromXml(String templateXml) 
	{
		ReportTemplateManifest1 obj = new ReportTemplateManifest1();
		
		return obj.extractTemplateInfoFromXml(templateXml);
	}
	
	/**
	 * Extracts the manifest info from a QueryBuilder project
	 * <p>
	 * @param  reportXml  a string that contains the QueryBuilder project
	 * @return      QueryBuilder project manifest as a string into "sb" StringBuilder variable
	 */    
	public void extractReportInfoFromXml(String reportXml, StringBuilder sb)
	{
		ReportTemplateManifest1 obj = new ReportTemplateManifest1();
		
		obj.extractReportInfoFromXml(reportXml, sb);
	}
}
