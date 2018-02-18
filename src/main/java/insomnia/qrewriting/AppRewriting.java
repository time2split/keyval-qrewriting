package insomnia.qrewriting;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.commons.cli.CommandLine;
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
	private CommandLine					coml;
	private HashMap<String, Duration>	times			= new HashMap<>();
	private RuleManager					rules			= new RuleManager();
	private Query						query			= null;
	private Encoding					encoding		= new Encoding();
	private ArrayList<Query>			queries;
	// final private JsonWriter writer = new JsonWriter();

	private Options						options;
	private App							app;
	private int							nbThreads		= 0;
	private AppDriverManager			driverManager	= new AppDriverManager();
	private Driver						driver;

	public AppRewriting(App app) throws ClassNotFoundException, Exception
	{
		this.app = app;
		coml = app.getCommandLine();
		times.put("generation", null);
		times.put("computation", null);

		Properties comprop = coml.getOptionProperties("O");
		Properties sysdef = new Properties();

		try
		{
			URL resource = AppRewriting.class.getResource("default.properties");
			sysdef.load(new InputStreamReader(resource.openStream(), "UTF8"));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		sysdef.putAll(comprop);
		options = new Options(sysdef);

		driver = driverManager.getDriver(app.getOptionDBDriver());
	}

	// ===============================================================
	// VELOCITY ACCESS
	// ===============================================================

	public Options getOptions()
	{
		return options;
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

		QueryManager queryManager = (QueryManager) driver.getQueryManagerClass()
				.getConstructor().newInstance();
		queryManager.setQueries(query);
		return queryManager.getStrFormat()[0];
	}

	public ArrayList<QueryBucket> getQueries() throws Exception
	{
		makeQueries();

		ArrayList<QueryBucket> ret = new ArrayList<>();
		long i = encoding.generateCodeInterval().geta();

		QueryManager queryManager = (QueryManager) driver.getQueryManagerClass()
				.getConstructor().newInstance();

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

		Driver driver = driverManager.getDriver("@internal");

		Class<?> queryBuilderClass = driver.getQueryBuilderClass();
		DriverQueryBuilder queryBuilder = (DriverQueryBuilder) queryBuilderClass
				.getDeclaredConstructor().newInstance();

		String fileQuery = coml.getOptionValue('q', app.defq);

		queryBuilder
				.setReader(IOUtils.toBufferedReader(new FileReader(fileQuery)));
		query = queryBuilder.newBuild();
	}

	private void makeQueries() throws Exception
	{
		makeContext();
		nbThreads = Integer.parseInt(options.getOption("sys.nbThreads", "1"));
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
			}
		}
		else
			throw new InvalidParameterException("Bad parameter sys.nbThreads="
					+ options.getOption("sys.nbThreads"));

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

		String fileRules = coml.getOptionValue('r', app.defr);
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
