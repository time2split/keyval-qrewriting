package insomnia.qrewriting;

import org.apache.commons.lang3.NotImplementedException;

import insomnia.qrewriting.database.Driver;
import insomnia.qrewriting.database.driver.DriverManager;

public class AppDriverManager extends DriverManager
{

	/**
	 * Tente de charger le driver $driverName, le nom doit être entier (package,
	 * classe)
	 * 
	 * @param className
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	private Driver loadDriver(String className) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException
	{
		ClassLoader loader = this.getClass().getClassLoader();

		@SuppressWarnings("unchecked")
		final Class<Driver> driverClass = (Class<Driver>) loader
				.loadClass(className);

		return driverClass.newInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Driver getDriver(String driverName)
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

			driver = loadDriver(className);
		}
		/*
		 * TODO: Classe externes
		 */
		else
		{
			throw new NotImplementedException("TODO file access");
		}
		drivers.put(driverName, driver);
		driver.load();
		return driver;
	}

}
