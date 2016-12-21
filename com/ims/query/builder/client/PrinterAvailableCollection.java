/*
 * Created on 05-May-2005
 *
 */
package com.ims.query.builder.client;

import java.util.ArrayList;

/**
 * @author vpurdila 
 *
 */
@SuppressWarnings("rawtypes")
public class PrinterAvailableCollection
{
	private ArrayList col = new ArrayList();
	
	@SuppressWarnings("unchecked")
	public void add(PrinterAvailable value)
	{
		this.col.add(value);
	}
	public void clear()
	{
		this.col.clear();
	}
	public void remove(int index)
	{
		this.col.remove(index);
	}
	public int size()
	{
		return this.col.size();
	}
	public int indexOf(PrinterAvailable instance)
	{
		return col.indexOf(instance);
	}
	public PrinterAvailable get(int index)
	{
		return (PrinterAvailable)this.col.get(index);
	}
	@SuppressWarnings("unchecked")
	public void set(int index, PrinterAvailable value)
	{
		this.col.set(index, value);
	}
	public void remove(PrinterAvailable instance)
	{
		remove(indexOf(instance));
	}
	@SuppressWarnings("unchecked")
	public PrinterAvailable[] toArray()
	{
		PrinterAvailable[] arr = new PrinterAvailable[col.size()];
		col.toArray(arr);
		return arr;
	}
}
