/*
 * Created on Mar 3, 2005
 *
 */
package com.ims.query.builder.client;

import java.util.StringTokenizer;

/**
 * @author vpurdila
 *
 */
public class StringUtils
{
	public static String encodeXML(String source)
	{
		if (source == null) return "";
		
		char[] ar = source.toCharArray();
		StringBuffer sb = new StringBuffer(source.length() * 2);
		for (int i = 0; i < ar.length; ++i)
		{
			switch (ar[i])
			{
				case '&':
					sb.append("&amp;");
					break;
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '\'':
					sb.append("&apos;");
					break;
				case '\"':
					sb.append("&quot;");
					break;
				case '\t':
					sb.append("&#x9;");
					break;
				case '\r':
					sb.append("&#xD;");
					break;
				case '\n':
					sb.append("&#xA;");
					break;
				default:
					int b = (int)ar[i];
					if (b > 31) // Ignore symbols, that won't be displayed anyway
						sb.append(ar[i]);
			}
		}
		return sb.toString();
	}
	
	public static String[] SplitString(String source, String token)
	{
		StringTokenizer st = new  StringTokenizer(source, token);
		
		int nCount = st.countTokens();
		
		if(nCount == 0)
			return null;
		
		String[] result = new String[nCount];
		
		for(int i = 0; i < nCount; i++)
			result[i] = st.nextToken();
		
		return result;
	}
	
}

