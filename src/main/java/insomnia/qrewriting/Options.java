package insomnia.qrewriting;

import java.util.Properties;

public class Options
{
	Properties prop;

	public Options(Properties def)
	{
		prop = new Properties(def);
	}

	public void setIfUnset(String name, String val)
	{
		if (prop.containsKey(name))
			return;

		prop.setProperty(name, val);
	}

	public void setOption(String name, String val)
	{
		prop.setProperty(name, val);
	}

	public String getOption(String name)
	{
		return prop.getProperty(name);
	}

	public String getOption(String name, String def)
	{
		return prop.getProperty(name, def);
	}
}