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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.IOUtils;

import insomnia.numeric.Interval;
import insomnia.qrewriting.code.Code;
import insomnia.qrewriting.code.Encoding;
import insomnia.qrewriting.context.AppContext;
import insomnia.qrewriting.context.Context;
import insomnia.qrewriting.database.Driver;
import insomnia.qrewriting.database.DriverException;
import insomnia.qrewriting.database.driver.CursorAggregation;
import insomnia.qrewriting.database.driver.DriverQueryBuilder;
import insomnia.qrewriting.database.driver.DriverQueryEvaluator;
import insomnia.qrewriting.database.driver.DriverQueryEvaluator.Cursor;
import insomnia.qrewriting.database.driver.DriverQueryManager;
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
import insomnia.qrewriting.thread.QThreadQueriesManager;
import insomnia.qrewriting.thread.QThreadResult;
import insomnia.qrewriting.thread.QThreadRewritingManager;
import insomnia.reader.ReaderException;

/**
 * Classe accessible dans le template Velocity
 * 
 * @author zuri
 */
public class AppRewriting
{
	private Context context = new AppContext();

	private HashMap<String, Duration> times    = new HashMap<>();
	private RuleManager               rules    = new RuleManager();
	private Query                     query    = null;
	private Encoding                  encoding = new Encoding();
	private Collection<Query>         queries;

	private Properties options;
	private App        app;
	private int        nbThreads = 0;
	private Driver     driver;

	private CursorAggregation answer = new CursorAggregation();

	private boolean queriesAreMerged = false;

	private List<Duration> threadEvaluationTimes;

	public AppRewriting(App app) throws ClassNotFoundException, Exception
	{
		threadEvaluationTimes = Collections.synchronizedList(new ArrayList<>());
		this.app              = app;
		times.put("generation", null);
		times.put("computation", null);
		times.put("evaluation", null);

		Properties comprop = app.coml.getOptionProperties("O");
		Properties sysdef  = new Properties();
		sysdef.load(new InputStreamReader(AppRewriting.class.getResourceAsStream("default.properties")));

		Properties defaultOptions = new Properties();
		defaultOptions.load(new InputStreamReader(AppRewriting.class.getResourceAsStream("options.properties")));

		options = new Properties(defaultOptions);
		options.putAll(comprop);
		driver = app.driverManager.getDriver(app.getOption("driver"), options, context);
	}

	// ===============================================================
	// VELOCITY ACCESS
	// ===============================================================

	/**
	 * @param name
	 * @return String|null null si option inexistante
	 */
	public String getOption(String name)
	{
		return getOption(name, "");
	}

	public String getOption(String name, String def)
	{
		return options.getProperty(name, def);
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

	public Cursor getAnswers()
	{
		return answer;
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

		Collection<Query>      queries = mergeQueries(this.queries);
		ArrayList<QueryBucket> ret     = new ArrayList<>();
		long                   i       = encoding.generateCodeInterval().geta();

		QueryManager queryManager = driver.getAQueryManager();

		if (queriesAreMerged)
		{
			Code _0code = encoding.getCodeFrom(0);

			for (Query q : queries)
				ret.add(new QueryBucket(q, i, _0code, queryManager));
		}
		else
		{
			for (Query q : queries)
			{
				ret.add(new QueryBucket(q, i, encoding.getCodeFrom((int) (i)), queryManager));
				i++;
			}
		}
		return ret;
	}

	// ===============================================================

	private void evaluate_QThread(Collection<QThreadResult> results)
	{
		try
		{
			Collection<Query> queries = new ArrayList<>();

			for (QThreadResult result : results)
				queries.add(result.query);

			evaluate(queries);
		}
		catch (DriverException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void evaluate(Collection<Query> queries) throws DriverException
	{
		Instant              start;
		Instant              end;
		DriverQueryEvaluator evaluator = driver.getAQueryEvaluator();
		Query[]              aqueries  = queries.toArray(new Query[0]);

		start = Instant.now();
		answer.add(evaluator.evaluate(aqueries));
		end = Instant.now();
		threadEvaluationTimes.add(Duration.between(start, end));
	}

	private void computeEvaluationTime()
	{
		Duration ret = Duration.ZERO;

		for (Duration d : threadEvaluationTimes)
			ret = ret.plus(d);

		times.put("evaluation", ret);
	}

	private void makeQuery() throws Exception
	{
		if (query != null)
			return;

		Driver driver = app.driverManager.getDriver("@internal", options, context);

		DriverQueryBuilder queryBuilder = driver.getAQueryBuilder();

		String fileQuery = app.getOption("file.query");

		queryBuilder.setReader(IOUtils.toBufferedReader(new FileReader(fileQuery)));
		query = queryBuilder.newBuild();
	}

	private void makeQueries() throws Exception
	{
		if (queries != null)
			return;

		makeContext();
		nbThreads = Integer.parseInt(getOption("sys.nbThreads", "1"));
		boolean doEvaluation = Integer.parseInt(getOption("sys.evaluation.block", "0")) > 0;
		Instant start;
		Instant end;

		if (nbThreads == 1)
		{
			start = Instant.now();
			QPU qpu = new QPUSimple(context, query, encoding);
			queries = qpu.process();

			if (doEvaluation)
				evaluate(queries);
		}
		else if (nbThreads > 1)
		{
			String mode = getOption("sys.mode", "normal").toLowerCase();
			queries = new ArrayList<>();
			QThreadManager threads;

			switch (mode)
			{
			case "normal":
				threads = new QThreadRewritingManager(context, query, encoding);
				break;

			case "naive":
				threads = new QThreadQueriesManager(context, query, encoding);
				break;

			default:
				throw new InvalidParameterException("Unknow option sys.mode=" + mode);
			}
			threads.setMode_nbThreads(nbThreads);

			if (doEvaluation)
				threads.setThreadCallback(this::evaluate_QThread);

			start = Instant.now();

			try
			{
				ArrayList<QThreadResult> res = threads.compute();

				for (QThreadResult r : res)
					queries.add(r.query);
			}
			catch (InterruptedException | ExecutionException e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
		else
			throw new InvalidParameterException("Bad parameter sys.nbThreads=" + getOption("sys.nbThreads"));

		end = Instant.now();
		times.put("computation", Duration.between(start, end));
		computeEvaluationTime();
	}

	private Collection<Query> mergeQueries(Collection<Query> queries) throws DriverException
	{
		String optVal = options.getProperty("queries.merge.sizeOfQuery");

		if (optVal == null)
			return queries;

		final int size = Integer.parseInt(optVal);

		if (size == 1)
			return queries;

		if (size <= 0)
			return Collections.emptyList();

		queriesAreMerged = true;

		DriverQueryManager manager = driver.getAQueryManager();

		if (!manager.canMerge())
			throw new DriverException("The query merge operation is not implemented by " + manager.getClass().getCanonicalName());

		// TODO: change this in sort of disjonctivee query
		Query[] tmp = manager.mergeBySizeOfQueries(size, queries);
		return new ArrayList<>(Arrays.asList(tmp));
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

		String fileRules = app.getOption("file.rules");
		{
			new RuleManagerBuilder_textDemo(context, rules).addLines(Files.readAllLines(Paths.get(fileRules))).build();
		}
		makeQuery();
		Instant       start     = Instant.now();
		CodeGenerator generator = new CodeGenerator_simple(query, rules);
		Instant       end       = Instant.now();
		times.put("generation", Duration.between(start, end));
		encoding = generator.getEncoding();
	}
}
