package insomnia.qrewriting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

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
import org.apache.velocity.exception.MethodInvocationException;

import insomnia.resource.ResourceUtils;

public class App
{
	protected final Properties	properties;

	private String				fileTemplate;

	protected Options			options;
	protected CommandLine		coml;
	protected VelocityContext	vcontext;
	protected AppDriverManager	driverManager;

	public App() throws Exception
	{
		properties = new Properties();
		properties.load(IOUtils.buffer(new InputStreamReader(
			App.class.getResourceAsStream("default.properties"))));

	}

	public String getDefault(String name)
	{
		assert properties.containsKey(
			name) : "The default property file must have the property '" + name
					+ "'";
		return properties.getProperty(name);

	}

	public String getOption(String name)
	{
		return coml.getOptionValue(name, getDefault(name));
	}

	private ArrayList<URL> getDriversLocations() throws Exception
	{
		ArrayList<URL> ret = new ArrayList<>();
		String[] paths = getOption("drivers.paths").split(":");
		FileSystem fs = FileSystems.getDefault();
		PathMatcher matcher = fs.getPathMatcher("glob:*.{jar,zip}");

		for (String path : paths)
		{
			Path cpath = Paths.get(path);
			cpath.normalize();

			if (Files.exists(cpath))
			{
				if (Files.isDirectory(cpath))
				{
					List<URL> tmp = Files.list(cpath).filter(p -> matcher.matches(p.getFileName()))
							.map(p ->
							{
								try
								{
									return p.toUri().toURL();
								}
								catch (MalformedURLException e)
								{
									e.printStackTrace();
								}
								return null;
							}).collect(Collectors.toList());
					ret.addAll(tmp);
				}
				else
				{
					ret.add(cpath.toUri().toURL());
				}
			}
		}
		return ret;
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
			return ResourceUtils.getResourcesOf(App.class,
				getDefault("sys.path.templates"));
		}
		catch (URISyntaxException | IOException e)
		{
			e.printStackTrace();
			System.exit(1);
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
		template.addOption(Option.builder("t").longOpt("template")
				.desc("Template of the output \n" + templates + "\n").hasArg()
				.build());
		ret.addOption(Option.builder("q").longOpt("file.query")
				.desc("Query file").hasArg().build());
		ret.addOption(Option.builder("r").longOpt("file.rules")
				.desc("Rules file").hasArg().build());
		ret.addOption(Option.builder("d").longOpt("driver")
				.desc("Database driver").hasArg().build());
		ret.addOption(Option.builder("D").longOpt("drivers.paths")
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
		format.printHelp(
			"Query rewriting " + properties.getProperty("version") + "\n\n",
			"Use query rewriting with norl(1) rules\n", options,
			"\nContact : webzuri@gmail.com\n");
	}

	protected void execute(String[] args) throws Exception
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
			fileTemplate = getOption("template");

			if (fileTemplate.charAt(0) == '@')
			{
				String name = fileTemplate.substring(1);

				if (!name.endsWith(".vm"))
					name += ".vm";

				fileTemplate = getDefault("sys.path.templates") + name;
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
			driverManager = new AppDriverManager(
				getDriversLocations().toArray(new URL[0]));
			createVelocityContext();
			program();
			velocityExecute();
		}
	}

	public static void main(String[] args) throws Exception
	{
		App app = new App();

		try
		{
			app.execute(args);
		}
		catch (MethodInvocationException e)
		{
			System.err.println(e.getLocalizedMessage());
			e.getCause().printStackTrace();
		}
	}
}
