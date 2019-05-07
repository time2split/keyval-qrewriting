package insomnia.qrewriting.context;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import insomnia.qrewriting.query.Label;
import insomnia.qrewriting.query.LabelFactory;

class TheLabelFactory implements LabelFactory
{
	private Map<Set<String>, WeakReference<MyLabel>> allocatedLabels = new HashMap<>(1024);
	private ReferenceQueue<MyLabel>                  referenceQueue  = new ReferenceQueue<>();

	private static final int initialStep = 1000;
	private int              cleanStep   = initialStep;

	public TheLabelFactory()
	{

	}

	@Override
	public MyLabel from(String label)
	{
		return from(new String[] { label });
	}

	@Override
	public MyLabel from(String[] labels)
	{
		Set<String> key = Set.of(labels);
		checkAllocatedLabel:
		{
			WeakReference<MyLabel> allocated = allocatedLabels.get(key);

			if (allocated == null)
				break checkAllocatedLabel;

			MyLabel ret = allocated.get();

			if (ret != null)
				return ret;
		}
		MyLabel theLabel = new MyLabel(labels);
		allocatedLabels.put(key, new WeakReference<>(theLabel, referenceQueue));
		cleanUnusedLabels();
		return theLabel;
	}

	@Override
	public Label from(Label label)
	{
		return label;
	}

	@Override
	public Label emptyLabel()
	{
		return from("");
	}

	private void cleanUnusedLabels()
	{
		if (--cleanStep == 0)
		{
			cleanStep = initialStep;
			Set<Reference<? extends MyLabel>> refToClean = new HashSet<>();
			{
				Reference<? extends MyLabel> a;

				while ((a = referenceQueue.poll()) != null)
				{
					refToClean.add(a);
				}
			}

			if (refToClean.isEmpty())
				return;

			List<Entry<Set<String>, WeakReference<MyLabel>>> entryToClean = allocatedLabels.entrySet().stream().filter(n -> refToClean.contains(n.getValue())).collect(Collectors.toList());

			for (Entry<Set<String>, WeakReference<MyLabel>> entry : entryToClean)
			{
				allocatedLabels.remove(entry.getKey());
			}
		}
	}

//	private static boolean __compare(Label a, Label b)
//	{
//		if (a.size() != b.size())
//			return false;
//
//		final int c = a.size();
//
//		for (int i = 0; i < c; i++)
//		{
//			if (!a.get(i).equals(b.get(i)))
//				return false;
//		}
//		return true;
//	}

	// ========================================================================

	static class MyLabel extends TreeSet<String> implements Label
	{
		private static final long serialVersionUID = 1L;

		public MyLabel(String[] labels)
		{
			super(Arrays.asList(labels));
		}

		@Override
		public boolean equals(Object o)
		{
			return this == o;

//			if (o instanceof Label)
//				return TheLabelFactory.__compare(this, (Label) o);
//
//			return false;
		}
	}
}
