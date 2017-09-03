package insomnia.qrewriting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

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
import insomnia.reader.ReaderException;
import insomnia.reader.TextReader;
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

	public AppRewriting(CommandLine coml)
	{
		this.coml = coml;
		times.put("generation", null);
		times.put("computation", null);
	}

	/**
	 * Change la représentation du json du writer
	 * 
	 * @param v
	 */
	public void setPrettyPrint(boolean v)
	{
		writer.getOptions().setCompact(!v);
	}

	public Encoding getEncoding()
	{
		return encoding;
	}
	
	public Interval getInterval()
	{
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

	public ArrayList<QueryBucket> getQueries() throws ReaderException,
			IOException, BuilderException, CodeGeneratorException
	{
		ArrayList<QueryBucket> ret = new ArrayList<>();
		makeQueries();
		long i = encoding.generateCodeInterval().geta();

		for (Query q : queries)
		{
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			Json json = new JsonBuilder_query(q).newBuild();
			writer.setDestination(buffer);

			try
			{
				writer.write(json);
			}
			catch (WriterException e)
			{
				e.printStackTrace();
			}
			ret.add(
				new QueryBucket(buffer.toString(), i, encoding.getCodeFrom((int) (i))));
			i++;
		}
		return ret;
	}

	public void makeQueries() throws ReaderException, IOException,
			BuilderException, CodeGeneratorException
	{
		makeContext();
		Instant start = Instant.now();
		QPU qpu = new QPUSimple(query, encoding.generateAllCodes(), encoding);
		queries = qpu.process();
		Instant end = Instant.now();
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

		String fileQuery = coml.getOptionValue('q', "query");
		String fileRules = coml.getOptionValue('r', "rules");
		{
			TextReader reader = new TextReader();
			reader.setModeAll();
			reader.setSource(new File(fileRules));
			new RuleManagerBuilder_text(rules, reader).build();
			reader.close();
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
