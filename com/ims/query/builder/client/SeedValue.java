/*
 * Created on Mar 3, 2005
 *
 */
package com.ims.query.builder.client;

/**
 * @author vpurdila
 *
 */
@SuppressWarnings("rawtypes")
public class SeedValue
{
	private String name;
	private Object value;
	private Class clazz;
	
	public SeedValue(String name, Object value, Class clazz)
	{
		super();
		this.name = name;
		this.value = value;
		this.clazz = clazz;
	}
	
	public String getName()
	{
		return name;
	}
	public void setName(String name)
	{
		this.name = name;
	}
	public Object getValue()
	{
		return value;
	}
	public void setValue(Object value)
	{
		this.value = value;
	}
	public Class getClazz()
	{
		return clazz;
	}
	public void setClazz(Class clazz)
	{
		this.clazz = clazz;
	}
}
