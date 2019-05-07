package insomnia.qrewriting;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import insomnia.qrewriting.context.Context;
import insomnia.qrewriting.database.Driver;
import insomnia.qrewriting.database.driver.DriverManager;

public class AppDriverManager extends DriverManager
{
	private URLClassLoader loader;

	public AppDriverManager(URL... driversLocations)
	{
		loader = new URLClassLoader(driversLocations);
	}

	private Driver loadDriver(String className, ClassLoader loader)
			throws ClassNotFoundException, InstantiationException,
			IllegalAccessException
	{
		@SuppressWarnings("unchecked")
		final Class<Driver> driverClass = (Class<Driver>) loader
				.loadClass(className);

		return driverClass.newInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Driver getDriver(String driverName, Properties options, Context context)
			throws ClassNotFoundException, Exception
	{
		Driver driver = drivers.get(driverName);

		if (driver != null)
			return driver;

		/*
		 * Classe interne
		 */
		if (driverName.charAt(0) == '@')
		{
			final String className = "insomnia.qrewriting.database.driver."
					+ driverName.substring(1) + ".TheDriver";

			driver = loadDriver(className, loader.getParent());
		}
		/*
		 * Classe externes
		 */
		else
		{
			final String className = "insomnia.qrewriting.database.driver."
					+ driverName + ".TheDriver";

			driver = loadDriver(className, loader);
		}
		drivers.put(driverName, driver);
		driver.load(context, options);
		return driver;
	}

}
