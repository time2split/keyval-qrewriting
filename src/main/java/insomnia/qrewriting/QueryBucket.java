package insomnia.qrewriting;

import insomnia.qrewriting.code.Code;
import insomnia.qrewriting.query.Query;
import insomnia.qrewriting.query.QueryManager;

/**
 * Stocke les informations d'une requête pour Velocity
 * 
 * @author zuri
 *
 */
public class QueryBucket
{
	private Query			q;
	private QueryManager	queryManager;
	public long				id;
	public Code				code;

	public QueryBucket(Query q, long id, Code code, QueryManager queryManager)
	{
		this.q = q;
		this.id = id;
		this.code = code;
		this.queryManager = queryManager;
	}

	public Code getCode()
	{
		return code;
	}

	public String getQ()
	{
		try
		{
			return queryManager.getStrFormat(q)[0];
		}
		catch (Exception e)
		{
			return e.getMessage();
		}
	}

	public long getId()
	{
		return id;
	}
}
