/*
 * Created on 05-May-2005
 *
 */
package com.ims.query.builder.client;

/**
 * @author vpurdila 
 *
 */
public class PrinterAvailable
{
	private String name;
	private String share;
	private String location;
	private String port;
	
	public PrinterAvailable(String name, String share, String location,	String port)
	{
		this.name = name;
		this.share = share;
		this.location = location;
		this.port = port;
	}
	
	public String getLocation()
	{
		return location;
	}
	public void setLocation(String location)
	{
		this.location = location;
	}
	public String getName()
	{
		return name;
	}
	public void setName(String name)
	{
		this.name = name;
	}
	public String getPort()
	{
		return port;
	}
	public void setPort(String port)
	{
		this.port = port;
	}
	public String getShare()
	{
		return share;
	}
	public void setShare(String share)
	{
		this.share = share;
	}
}
