package comapp.cloud;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import comapp.SystemParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class TrackId implements Cloneable, Serializable {

	private static final long serialVersionUID = 1L;

	private String userId = "";
	private String userName = "";
	private String sessionId = "-";
	private Set<String> strings = new CopyOnWriteArraySet<>();
	private String tree="";
	private String environment;
	
	public String getTree() {
		return tree;
	}

	public void setTree(String tree) {
		this.tree = tree;
	}

	 

	

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public TrackId(String session) {
		this.sessionId = session;
		this.environment = SystemParameters.getParameter("environment", "v");
	}

	 
	 
	 

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public void add(String s) {
		if (StringUtils.isNotBlank(s)) {
			strings.add(s);
		}
	}

	public boolean remove(String s) {
		return strings.remove(s);
	}

	public int size() {
		return strings.size();
	}

	@Override
	public TrackId clone() {
		try {
			TrackId copy = (TrackId) super.clone();
			copy.strings = new CopyOnWriteArraySet<>(this.strings);

			return copy;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError("Clone failed", e);
		}
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder("[");
		 res.append( sessionId);
		 
		if (StringUtils.isNotBlank(environment)) {
			res.append(","+ environment);
		}
		if (StringUtils.isNotBlank(tree)) {
			res.append(","+  tree);
		}
		if (StringUtils.isNotBlank(userName)) {
			res.append(","+  userName);
		}else {
			res.append(","+  userId);
		}

		
		if (!strings.isEmpty()) {
			res.append(","+  String.join(", ", strings));
		}

		res.append("]");
		return res.toString();
	}

	 

	public static TrackId get(HttpSession session) {
		return get( session, false); 
	}
	public static TrackId get(HttpSession session, boolean create) {
		TrackId trackId = (TrackId) session.getAttribute("TrackId");
		if (trackId == null && create) {
			trackId = new TrackId(session.getId());		 
			session.setAttribute("TrackId", trackId);
		}
		return trackId;
	}
	public void logHeadersParameters(HttpServletRequest request, Logger log) {
		if (request == null || log == null) {
			return;
		}

		log.info("{} Header ********", this);
		Enumeration<String> headers = request.getHeaderNames();
		while (headers.hasMoreElements()) {
			String key = headers.nextElement();
			String value = request.getHeader(key);
			log.info("{} Header: {} = {}", this, key, value);
		}

		log.info("{} Parameters ********", this);
		Enumeration<String> params = request.getParameterNames();
		while (params.hasMoreElements()) {
			String key = params.nextElement();
			String value = request.getParameter(key);
			log.info("{} Parameter: {} = {}", this, key, value);
		}
	}

	public boolean contains(String value) {
		return strings.contains(value);
	}
}