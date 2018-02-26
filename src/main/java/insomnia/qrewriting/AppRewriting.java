package insomnia.qrewriting;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.IOUtils;

import insomnia.numeric.Interval;
import insomnia.qrewriting.code.Encoding;
import insomnia.qrewriting.database.Driver;
import insomnia.qrewriting.database.driver.DriverQueryBuilder;
import insomnia.qrewriting.generator.CodeGenerator;
import insomnia.qrewriting.generator.CodeGeneratorException;
import insomnia.qrewriting.generator.CodeGenerator_simple;
import insomnia.qrewriting.qpu.QPU;
import insomnia.qrewriting.qpu.QPUSimple;
import insomnia.qrewriting.query.Query;
import insomnia.qrewriting.query.QueryBuilderException;
import insomnia.qrewriting.query.QueryManager;
import insomnia.qrewriting.query_building.RuleManagerBuilder_textDemo;
import insomnia.qrewriting.rule.RuleManager;
import insomnia.qrewriting.thread.QThreadManager;
import insomnia.qrewriting.thread.QThreadResult;
import insomnia.reader.ReaderException;

/**
 * Classe accessible dans le template Velocity
 * 
 * @author zuri
 *
 */
public class AppRewriting
{
	private HashMap<String, Duration>	times			= new HashMap<>();
	private RuleManager					rules			= new RuleManager();
	private Query						query			= null;
	private Encoding					encoding		= new Encoding();
	private ArrayList<Query>			queries;

	// private Properties defaultOptions;
	private Properties					options;
	private App							app;
	private int							nbThreads		= 0;
	private AppDriverManager			driverManager	= new AppDriverManager();
	private Driver						driver;

	public AppRewriting(App app) throws ClassNotFoundException, Exception
	{
		this.app = app;
		times.put("generation", null);
		times.put("computation", null);

		Properties comprop = app.coml.getOptionProperties("O");
		Properties sysdef = new Properties();
		sysdef.load(new InputStreamReader(
			AppRewriting.class.getResourceAsStream("default.properties")));

		Properties defaultOptions = new Properties();
		defaultOptions.load(new InputStreamReader(
			AppRewriting.class.getResourceAsStream("options.properties")));

		options = new Properties(defaultOptions);
		options.putAll(comprop);
		driver = driverManager.getDriver(app.getOptionDBDriver(), options);
	}

	// ===============================================================
	// VELOCITY ACCESS
	// ===============================================================

	/**
	 * 
	 * @param name
	 * @return String|null null si option inexistante
	 */
	public String getOption(String name)
	{
		return getOption(name,"");
	}

	public String getOption(String name, String def)
	{
		return options.getProperty(name,def);
	}

	public int getNbThreads()
	{
		return nbThreads;
	}

	public Encoding getEncoding() throws Exception
	{
		makeContext();
		return encoding;
	}

	public Interval getInterval() throws Exception
	{
		makeContext();
		return encoding.generateCodeInterval();
	}

	public HashMap<String, Duration> getTimes()
	{
		return times;
	}

	public long getNbCodes() throws Exception
	{
		makeContext();
		return encoding.getTotalNbStates();
	}

	public void compute() throws Exception
	{
		makeQueries();
	}

	/*
	 * Récupère la requête de base
	 */
	public String getQuery() throws Exception
	{
		makeQuery();

		QueryManager queryManager = driver.getAQueryManager();
		queryManager.setQueries(query);
		return queryManager.getStrFormat()[0];
	}

	public ArrayList<QueryBucket> getQueries() throws Exception
	{
		makeQueries();

		ArrayList<QueryBucket> ret = new ArrayList<>();
		long i = encoding.generateCodeInterval().geta();

		QueryManager queryManager = driver.getAQueryManager();

		for (Query q : queries)
		{
			queryManager.setQueries(q);
			ret.add(new QueryBucket(queryManager.getStrFormat()[0].toString(),
				i, encoding.getCodeFrom((int) (i))));
			i++;
		}
		return ret;
	}

	// ===============================================================

	private void makeQuery() throws Exception
	{
		if (query != null)
			return;

		Driver driver = driverManager.getDriver("@internal", options);

		Class<?> queryBuilderClass = driver.getQueryBuilderClass();
		DriverQueryBuilder queryBuilder = (DriverQueryBuilder) queryBuilderClass
				.getDeclaredConstructor().newInstance();

		String fileQuery = app.getOptionQuery();

		queryBuilder
				.setReader(IOUtils.toBufferedReader(new FileReader(fileQuery)));
		query = queryBuilder.newBuild();
	}

	private void makeQueries() throws Exception
	{
		makeContext();
		nbThreads = Integer.parseInt(getOption("sys.nbThreads", "1"));
		Instant start;
		Instant end;

		if (nbThreads == 1)
		{
			start = Instant.now();
			QPU qpu = new QPUSimple(query, encoding);
			queries = qpu.process();
		}
		else if (nbThreads > 1)
		{
			queries = new ArrayList<>();
			QThreadManager threads = new QThreadManager(query, encoding);
			threads.setMode_nbThread(nbThreads);
			start = Instant.now();

			try
			{
				ArrayList<QThreadResult> res = threads.compute();

				for (QThreadResult r : res)
				{
					queries.add(r.query);
				}
			}
			catch (InterruptedException | ExecutionException e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
		else
		{
			throw new InvalidParameterException(
				"Bad parameter sys.nbThreads=" + getOption("sys.nbThreads"));
		}
		end = Instant.now();
		times.put("computation", Duration.between(start, end));
	}

	/**
	 * Calcule Query et Rules
	 * 
	 * @throws IOException
	 * @throws ReaderException
	 * @throws QueryBuilderException
	 * @throws CodeGeneratorException
	 */
	private void makeContext() throws Exception
	{
		if (times.get("generation") != null)
			return;

		String fileRules = app.getOptionRules();
		{
			new RuleManagerBuilder_textDemo(rules)
					.addLines(Files.readAllLines(Paths.get(fileRules))).build();
		}
		makeQuery();
		Instant start = Instant.now();
		CodeGenerator generator = new CodeGenerator_simple(query, rules);
		Instant end = Instant.now();
		times.put("generation", Duration.between(start, end));
		encoding = generator.getEncoding();
	}
}
