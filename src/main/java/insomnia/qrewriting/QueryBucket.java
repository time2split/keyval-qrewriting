package insomnia.qrewriting;

import insomnia.qrewriting.query_rewriting.code.Code;

/**
 * Stocke les informations d'une requête pour Velocity
 * @author zuri
 *
 */
public class QueryBucket
{
	public String	q;
	public long		id;
	public Code		code;

	public QueryBucket(String q, long id, Code code)
	{
		this.q = q;
		this.id = id;
		this.code = code;
	}

	public Code getCode()
	{
		return code;
	}

	public String getQ()
	{
		return q;
	}

	public long getId()
	{
		return id;
	}
}
