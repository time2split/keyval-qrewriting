package insomnia.qrewriting;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

import insomnia.builder.BuilderException;
import insomnia.json.Json;
import insomnia.json.JsonReader;
import insomnia.json.JsonWriter;
import insomnia.numeric.Interval;
import insomnia.qrewritingnorl1.query_building.RuleManagerBuilder_text;
import insomnia.qrewritingnorl1.query_building.mongo.JsonBuilder_query;
import insomnia.qrewritingnorl1.query_building.mongo.QueryBuilder_json;
import insomnia.qrewritingnorl1.query_rewriting.code.Encoding;
import insomnia.qrewritingnorl1.query_rewriting.generator.CodeGenerator;
import insomnia.qrewritingnorl1.query_rewriting.generator.CodeGeneratorException;
import insomnia.qrewritingnorl1.query_rewriting.generator.CodeGenerator_simple;
import insomnia.qrewritingnorl1.query_rewriting.qpu.QPU;
import insomnia.qrewritingnorl1.query_rewriting.qpu.QPUSimple;
import insomnia.qrewritingnorl1.query_rewriting.query.Query;
import insomnia.qrewritingnorl1.query_rewriting.query.QueryBuilderException;
import insomnia.qrewritingnorl1.query_rewriting.rule.RuleManager;
import insomnia.qrewritingnorl1.query_rewriting.thread.QThreadManager;
import insomnia.qrewritingnorl1.query_rewriting.thread.QThreadResult;
import insomnia.reader.ReaderException;
import insomnia.writer.WriterException;

/**
 * Classe accessible dans le template Velocity
 * 
 * @author zuri
 *
 */
public class AppRewriting
{
	private CommandLine					coml;
	private HashMap<String, Duration>	times		= new HashMap<>();
	private RuleManager					rules		= new RuleManager();
	private Query						query		= new Query();
	private Encoding					encoding	= new Encoding();
	private ArrayList<Query>			queries;
	final private JsonWriter			writer		= new JsonWriter();

	private Options						options;
	private App							app;

	public AppRewriting(App app)
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
	}

	/**
	 * A appeler avant toute écriture avec le writer
	 */
	private void initJSWriter()
	{
		writer.getOptions().setCompact(
			options.getOption("json.prettyPrint").equals("false") ? true
					: false);
	}

	// ===============================================================
	// VELOCITY ACCESS
	// ===============================================================

	public Options getOptions()
	{
		return options;
	}

	public String getNbThreads()
	{
		return options.getOption("sys.nbThreads");
	}

	public Encoding getEncoding() throws ReaderException, IOException,
			BuilderException, CodeGeneratorException
	{
		makeContext();
		return encoding;
	}

	public Interval getInterval() throws ReaderException, IOException,
			BuilderException, CodeGeneratorException
	{
		makeContext();
		return encoding.generateCodeInterval();
	}

	public HashMap<String, Duration> getTimes()
	{
		return times;
	}

	public long getNbCodes() throws ReaderException, IOException,
			BuilderException, CodeGeneratorException
	{
		makeContext();
		return encoding.getTotalNbStates();
	}

	public void compute() throws ReaderException, IOException, BuilderException,
			CodeGeneratorException
	{
		makeQueries();
	}

	public ArrayList<QueryBucket> getQueries()
			throws ReaderException, IOException, BuilderException,
			CodeGeneratorException, WriterException
	{
		ArrayList<QueryBucket> ret = new ArrayList<>();
		makeQueries();
		long i = encoding.generateCodeInterval().geta();
		initJSWriter();

		for (Query q : queries)
		{
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			Json json = new JsonBuilder_query(q).newBuild();
			writer.setDestination(buffer);

			writer.write(json);
			ret.add(new QueryBucket(buffer.toString(), i,
				encoding.getCodeFrom((int) (i))));
			i++;
		}
		return ret;
	}

	// ===============================================================

	private void makeQueries() throws ReaderException, IOException,
			BuilderException, CodeGeneratorException
	{
		makeContext();
		int nbThreads = Integer
				.parseInt(options.getOption("sys.nbThreads", "1"));
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
	private void makeContext() throws ReaderException, IOException,
			BuilderException, CodeGeneratorException
	{
		if (times.get("generation") != null)
			return;

		String fileQuery = coml.getOptionValue('q', app.defq);
		String fileRules = coml.getOptionValue('r', app.defr);
		{
			new RuleManagerBuilder_text(rules).addLines(Files.readAllLines(Paths.get(fileRules))).build();
		}
		{
			JsonReader jsreader = new JsonReader(new File(fileQuery));
			jsreader.getOptions().setStrict(false);
			Json json = jsreader.read();
			jsreader.close();
			new QueryBuilder_json(query, json).build();
		}
		Instant start = Instant.now();
		CodeGenerator generator = new CodeGenerator_simple(query, rules);
		Instant end = Instant.now();
		times.put("generation", Duration.between(start, end));
		encoding = generator.getEncoding();
	}
}
