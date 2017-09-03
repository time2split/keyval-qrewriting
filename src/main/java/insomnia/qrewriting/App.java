package insomnia.qrewriting;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;

import insomnia.reader.ReaderException;
import insomnia.reader.TextReader;

public class App
{
	final static String	version	= "1.0-SNAPSHOT";
	final static String	deft	= "@default";
	final static String	defq	= "query";
	final static String	defr	= "rules";

	/**
	 * Récupère les noms des fichiers internes de template
	 * 
	 * @return
	 */
	private static String[] getTemplateFiles()
	{
		return new File("template/").list();
	}

	private static Options makeOptions()
	{
		final Options ret = new Options();
		final OptionGroup template = new OptionGroup();
		String templates = "";
		
		// Liste des templates disponibles
		for(String s : getTemplateFiles())
		{
			templates += "@" + s + "\n";
		}
		template.addOption(Option.builder("t").longOpt("file-template")
				.desc("Template of the output \n" + templates + "\n").hasArg().build());
//		template.addOption(Option.builder("T").longOpt("template")
//				.desc("Direct input of the template").hasArg().build());
		ret.addOption(Option.builder("q").longOpt("file-query")
				.desc("Query file").hasArg().build());
		ret.addOption(Option.builder("r").longOpt("file-rules")
				.desc("Rules file").hasArg().build());
		ret.addOption(Option.builder("h").longOpt("help").desc("Help").build());
		ret.addOption(Option.builder().longOpt("display-template")
			.desc("Display the template and exit").build());
		ret.addOptionGroup(template);
		return ret;
	}

	public static void main(String[] args)
	{
		Options options = makeOptions();
		DefaultParser parser = new DefaultParser();

		try
		{
			CommandLine coml = parser.parse(options, args);

			// Help
			if (coml.hasOption("h"))
			{
				HelpFormatter format = new HelpFormatter();
				format.printHelp("Query rewriting " + version + "\n\n",
					"Use query rewriting with norl(1) rules\n", options,
					"\nContact : webzuri@gmail.com\n");
				return;
			}

			AppRewriting apprewriting = new AppRewriting(coml);
			String fileTemplate = coml.getOptionValue('t', App.deft);

			if(fileTemplate.charAt(0) == '@')
			{
				String name = fileTemplate.substring(1);
				
				if(!name.endsWith(".vm"))
					name += ".vm";
					
				fileTemplate = "template/" + name;
			}
			
			if(coml.hasOption("display-template"))
			{
				TextReader reader = new TextReader(new File(fileTemplate));
				reader.setModeAll();
				String buff = reader.read().get(0);
				System.out.println(buff);
				reader.close();
				return;
			}
			Velocity.init();
			VelocityContext vcontext = new VelocityContext();
			vcontext.put("r", apprewriting);

			Template template = Velocity.getTemplate(fileTemplate);
			StringWriter sw = new StringWriter();

			template.merge(vcontext, sw);

			System.out.println(sw);
		}
		catch (ResourceNotFoundException
				| org.apache.commons.cli.ParseException | ReaderException | IOException e)
		{
			System.err.println(e.getMessage());
		}
	}
}
