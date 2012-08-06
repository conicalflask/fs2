package common.httpserver;

import java.util.List;

/**
 * A representation of a context (a subtree of paths) on an HttpServer
 * 
 * @author gp
 *
 */
public abstract class HttpContext {

	public abstract List<Filter> getFilters();

	/**
	 * Returns the path that this context was created for.
	 * @return
	 */
	public abstract String getPath();
	
}
