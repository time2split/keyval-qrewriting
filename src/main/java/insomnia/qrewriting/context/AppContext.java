package insomnia.qrewriting.context;

import insomnia.qrewriting.query.LabelFactory;

public class AppContext implements Context
{
	private LabelFactory labelFactory = new TheLabelFactory();
	
	@Override
	public LabelFactory getLabelFactory()
	{
		return labelFactory;
	}
}
