package insomnia.qrewriting;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import insomnia.reader.ReaderException;
import insomnia.reader.TextReader;

public class App
{
	protected String			version;
	protected String			deft;
	protected String			defq;
	protected String			defr;
	protected String			pathTemplate;	// Chemin vers la ressource
												// dossier 'template'
	private String				fileTemplate;

	protected Options			options;
	protected CommandLine		coml;
	protected VelocityContext	vcontext;

	{
		version = "1.0-SNAPSHOT";
		deft = "@default";
		defq = "query";
		defr = "rules";
		pathTemplate = "/insomnia/qrewriting/template/";
	}

	final public Options getOptions()
	{
		return options;
	}

	final public CommandLine getCommandLine()
	{
		return coml;
	}

	/**
	 * 
	 * Récupère les noms des fichiers internes de template
	 * 
	 * @return
	 */
	protected String[] getTemplateFiles()
	{
		try (TextReader reader = new TextReader(
			App.class.getResourceAsStream(pathTemplate));)
		{
			reader.setModeLine();
			String[] ret = reader.read().toArray(new String[0]);
			Arrays.sort(ret);
			return ret;
		}
		catch (IOException | ReaderException e)
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
		ret.addOption(Option.builder("h").longOpt("help").desc("Help").build());
		ret.addOption(Option.builder().longOpt("display-template")
				.desc("Display the template and exit").build());
		ret.addOption(Option.builder("O").hasArgs().valueSeparator()
				.desc("Set an option of the program").build());
		ret.addOptionGroup(template);
		return ret;
	}

	protected void createVelocityContext()
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
		format.printHelp("Query rewriting " + version + "\n\n",
			"Use query rewriting with norl(1) rules\n", options,
			"\nContact : webzuri@gmail.com\n");
	}

	public static void main(String[] args)
	{
		App app = new App();
		app.execute(args);
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
			fileTemplate = coml.getOptionValue('t', deft);

			if (fileTemplate.charAt(0) == '@')
			{
				String name = fileTemplate.substring(1);

				if (!name.endsWith(".vm"))
					name += ".vm";

				fileTemplate = pathTemplate + name;
				internalTemplate = true;
			}

			if (coml.hasOption("display-template"))
			{
				TextReader reader = new TextReader();
				
				if(internalTemplate)
				{
					reader.setSource(App.class.getResourceAsStream(fileTemplate));
				}
				else
				{
					reader.setSource(new File(fileTemplate));
				}
				reader.setModeAll();
				String buff = reader.read().get(0);
				reader.close();
				System.out.println(buff);
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
}
