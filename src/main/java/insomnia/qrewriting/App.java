package insomnia.qrewriting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import insomnia.resource.ResourceUtils;

public class App
{
	protected final Properties	properties;

	private String				fileTemplate;

	protected Options			options;
	protected CommandLine		coml;
	protected VelocityContext	vcontext;

	public App() throws IOException
	{
		properties = new Properties();
		properties.load(IOUtils.buffer(new InputStreamReader(App.class.getResourceAsStream("default.properties"))));
	}

	final public Options getOptions()
	{
		return options;
	}

	final public CommandLine getCommandLine()
	{
		return coml;
	}

	public String getOptionDBDriver()
	{
		return coml.getOptionValue('d', properties.getProperty("database.driver"));
	}

	/**
	 * 
	 * Récupère les noms des fichiers internes de template
	 * 
	 * @return
	 */
	protected String[] getTemplateFiles()
	{
		try
		{
			return ResourceUtils.getResourcesOf(App.class, properties.getProperty("path.templates"));
		}
		catch (URISyntaxException | IOException e)
		{
			e.printStackTrace();
		}
		return new String[0];
	}

	protected Options makeOptions()
	{
		final Options ret = new Options();
		final OptionGroup template = new OptionGroup();
		String templates = "";

		// Liste des templates disponibles
		for (String s : getTemplateFiles())
		{
			templates += "@" + s + "\n";
		}
		template.addOption(Option.builder("t").longOpt("file-template")
				.desc("Template of the output \n" + templates + "\n").hasArg()
				.build());
		// template.addOption(Option.builder("T").longOpt("template")
		// .desc("Direct input of the template").hasArg().build());
		ret.addOption(Option.builder("q").longOpt("file-query")
				.desc("Query file").hasArg().build());
		ret.addOption(Option.builder("r").longOpt("file-rules")
				.desc("Rules file").hasArg().build());
		ret.addOption(Option.builder("d").longOpt("db-driver")
				.desc("Database driver").hasArg().build());
		ret.addOption(Option.builder("h").longOpt("help").desc("Help").build());
		ret.addOption(Option.builder().longOpt("display-template")
				.desc("Display the template and exit").build());
		ret.addOption(Option.builder("O").hasArgs().valueSeparator()
				.desc("Set an option of the program").build());
		ret.addOptionGroup(template);
		return ret;
	}

	protected void createVelocityContext()
			throws ClassNotFoundException, Exception
	{
		vcontext = new VelocityContext();
		AppRewriting apprewriting = new AppRewriting(this);
		vcontext.put("r", apprewriting);
	}

	protected void program()
	{

	}

	protected void velocityExecute()
	{
		Template template = Velocity.getTemplate(fileTemplate);
		StringWriter sw = new StringWriter();

		template.merge(vcontext, sw);

		System.out.println(sw);
	}

	protected void printHelp()
	{
		HelpFormatter format = new HelpFormatter();
		format.printHelp("Query rewriting " + properties.getProperty("version") + "\n\n",
			"Use query rewriting with norl(1) rules\n", options,
			"\nContact : webzuri@gmail.com\n");
	}

	protected void execute(String[] args)
	{
		// Init de Velocity
		{
			Properties prop = new Properties();

			try
			{
				URL resource = App.class.getResource("velocity.properties");
				prop.load(new InputStreamReader(resource.openStream(), "UTF8"));
			}
			catch (IOException e)
			{
				System.err
						.println("impossible de charger velocity.properties : "
								+ e.getMessage());
			}
			Velocity.init(prop);
		}
		options = makeOptions();

		try
		{
			DefaultParser parser = new DefaultParser();
			coml = parser.parse(options, args);
			boolean internalTemplate = false;

			// Help
			if (coml.hasOption("h"))
			{
				printHelp();
				return;
			}
			fileTemplate = coml.getOptionValue('t', properties.getProperty("file.template"));

			if (fileTemplate.charAt(0) == '@')
			{
				String name = fileTemplate.substring(1);

				if (!name.endsWith(".vm"))
					name += ".vm";

				fileTemplate = properties.getProperty("path.templates") + name;
				internalTemplate = true;
			}

			if (coml.hasOption("display-template"))
			{
				String template;

				if (internalTemplate)
				{
					Reader reader = new BufferedReader(new InputStreamReader(
						App.class.getResourceAsStream(fileTemplate)));
					template = IOUtils.toString(reader);
					reader.close();
				}
				else
				{
					Path path = Paths.get(fileTemplate);
					Reader reader = Files.newBufferedReader(path);
					template = IOUtils.toString(reader);
					reader.close();
				}
				System.out.println(template);
				return;
			}
			createVelocityContext();
			program();
			velocityExecute();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException
	{
		App app = new App();
		app.execute(args);
	}
}
