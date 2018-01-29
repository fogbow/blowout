package org.fogbowcloud.blowout.infrastructure.http;

import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.order.OrderConstants;

public class HttpWrapper {

	private static final int SERVER_SIDE_ERRO_MAX = 505;
	private static final int CLIENT_SIDE_CODE_ERRO_INIT = 400;

	public String doRequest(String method, String endpoint, String authToken,
			List<Header> additionalHeaders) throws Exception {

		CloseableHttpClient client = HttpClients.createMinimal();

		HttpUriRequest request = this.getRequestType(method, endpoint);

		this.addHeaders(authToken, additionalHeaders, request);

		CloseableHttpResponse response = null;
		try {
			response = client.execute(request);
			HttpEntity entity = response.getEntity();
			
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
				Header locationHeader = getLocationHeader(response.getAllHeaders());

				if (locationHeader != null
						&& locationHeader.getValue().contains(OrderConstants.TERM)) {
					return generateLocationHeaderResponse(locationHeader);
				} else {
					return EntityUtils.toString(entity);
				}
				
			} else if (statusCode >= CLIENT_SIDE_CODE_ERRO_INIT
					&& statusCode <= SERVER_SIDE_ERRO_MAX) {
				throw new Exception("Erro on request - Method [" + method + "] Endpoint: ["
						+ endpoint + "] - Status: " + statusCode + " -  Msg: "
						+ response.getStatusLine().toString());
			} else {
				return response.getStatusLine().toString();
			}
		} finally {
			if(response != null) {
				response.close();
			}
			if(client != null) {
				client.close();				
			}
		}
	}

	private void addHeaders(String authToken, List<Header> additionalHeaders,
			HttpUriRequest request) {
		request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		if (authToken != null) {
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
		}
		for (Header header : additionalHeaders) {
			request.addHeader(header);
		}
	}

	protected static Header getLocationHeader(Header[] headers) {
		Header locationHeader = null;
		for (Header header : headers) {
			if (header.getName().equals("Location")) {
				locationHeader = header;
			}
		}
		return locationHeader;
	}

	protected static String generateLocationHeaderResponse(Header header) {
		String[] locations = header.getValue().split(",");
		String response = "";
		for (String location : locations) {
			response += HeaderUtils.X_OCCI_LOCATION_PREFIX + location + System.lineSeparator();
		}
		return response.trim();
	}

	private HttpUriRequest getRequestType(String method, String endpoint) {
		HttpUriRequest request = null;
		if (method.equals("get")) {
			request = new HttpGet(endpoint);
		} else if (method.equals("delete")) {
			request = new HttpDelete(endpoint);
		} else if (method.equals("post")) {
			request = new HttpPost(endpoint);
		}
		return request;
	}
}
