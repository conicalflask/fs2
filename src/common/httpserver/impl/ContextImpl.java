package common.httpserver.impl;

import java.util.LinkedList;
import java.util.List;

import common.httpserver.Filter;
import common.httpserver.HttpContext;
import common.httpserver.HttpHandler;

public class ContextImpl extends HttpContext {

	final LinkedList<Filter> filters = new LinkedList<Filter>();
	String path;
	final HttpHandler handler;
	
	public ContextImpl(String path, HttpHandler handler) {
		this.path = path;
		this.handler = handler;
	}
	
	@Override
	public List<Filter> getFilters() {
		return filters;
	}

	@Override
	public String getPath() {
		return path;
	}
	
	public HttpHandler getHandler() {
		return handler;
	}

}
