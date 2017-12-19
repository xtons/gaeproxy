package com.xtons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.repackaged.org.apache.commons.codec.net.URLCodec;

enum SkipHeaders {
	INSTANCE;
	private final Set<String> headers;

	SkipHeaders() {
		headers = new HashSet<String>();
		headers.add("Content-Length");
		headers.add("Host");
		headers.add("Vary");
		headers.add("Via");
		headers.add("X-Appengine-Inbound-Appid");
		headers.add("X-Forwarded-For");
		headers.add("X-ProxyUser-IP");
	}

	boolean test(String header) {
		return headers.contains(header);
	}
};

@SuppressWarnings("serial")
@WebServlet(name = "GaeServlet", urlPatterns = { "/*" })
public class GaeServlet extends HttpServlet {
	final static byte[] back = "oldstreams.wordpress.com".getBytes(StandardCharsets.US_ASCII);
	final static byte[] front = "blog.xtons.com".getBytes(StandardCharsets.US_ASCII);
	HttpURLConnection conn = null;

	void filterStream(HttpServletResponse response) throws IOException {
		try (InputStream is = conn.getInputStream(); ServletOutputStream os = response.getOutputStream()) {
			int match = 0;
			int b;
			// 此处需要一个更严谨的字符串匹配回溯算法，目前是个有错的简化版，不能正确处理abcabcabd的情况
			while ((b = is.read()) >= 0) {
				if (b == back[match]) {
					match++;
					if (match == back.length) {
						match = 0;
						os.write(front);
					}
				} else {
					if (match > 0) {
						os.write(back, 0, match);
						match = 0;
					}
					os.write(b);
				}
			}
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		conn.setRequestMethod("GET");
		filterStream(response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");

		try (ServletInputStream is = request.getInputStream(); OutputStream os = conn.getOutputStream();) {
			byte[] chunk = new byte[4096];
			int n;
			while ((n = is.read(chunk)) >= 0) {
				os.write(chunk, 0, n);
			}
		}
		filterStream(response);
	}

	@Override
	public void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
		conn.setDoOutput(true);
		conn.setRequestMethod("PUT");

		try (ServletInputStream is = request.getInputStream(); OutputStream os = conn.getOutputStream();) {
			byte[] chunk = new byte[4096];
			int n;
			while ((n = is.read(chunk)) >= 0) {
				os.write(chunk, 0, n);
			}
		}
		filterStream(response);
	}

	@Override
	public void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
		conn.setRequestMethod("DELETE");
		filterStream(response);
	}

	@Override
	public void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
		conn.setRequestMethod("OPTIONS");
		filterStream(response);
	}

	@Override
	public void doTrace(HttpServletRequest request, HttpServletResponse response) throws IOException {
		conn.setRequestMethod("TRACE");
		filterStream(response);
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		URL url = null;
		String path = req.getServletContext().getContextPath();
		if (req.getQueryString() == null)
			url = new URL(new StringBuilder("https://oldstreams.wordpress.com").append(req.getRequestURI()).toString());
		else
			url = new URL(new StringBuilder("https://oldstreams.wordpress.com").append(req.getRequestURI()).append("?")
					.append(req.getQueryString()).toString());
		conn = (HttpURLConnection) url.openConnection();
		for (Enumeration<String> names = req.getHeaderNames(); names.hasMoreElements();) {
			String name = names.nextElement();
			if (SkipHeaders.INSTANCE.test(name))
				continue;
			for (Enumeration<String> values = req.getHeaders(name); values.hasMoreElements();) {
				String value = values.nextElement();
				conn.setRequestProperty(name, value);
			}
		}

		super.service(req, resp);
	}

}