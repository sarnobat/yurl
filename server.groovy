import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("yurl")
	public static class HelloWorldResource { // Must be public

		final String CYPHER_URI = "http://localhost:7474/db/data/cypher";

		@GET
		@Path("keys")
		@Produces("application/json")
		public String keys() throws JSONException, IOException {
		System.out.println("A");
			JSONObject json = queryNeo4j("start n=node(*) where has(n.name) and has (n.key) return n.name,n.key");
			return json.get("data").toString();
		}

		private JSONObject queryNeo4j(String cypherQuery) throws IOException, JSONException {
		System.out.println("0");
//		try {
			WebResource resource = Client.create().resource(CYPHER_URI);
//			}catch(Exception e) {
//			print e;
//			e.printStackTrace();
//			}
System.out.println("1");
Map map = new HashMap();
map.put("query", cypherQuery);
			// POST {} to the node entry point URI
			ClientResponse response = resource
					.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON)
					.entity("{ }")
					.post(ClientResponse.class,map);
//							"{\"query\" : \"" + cypherQuery + "\", \"params\" : {}}");
System.out.println("2");
			String neo4jResponse = IOUtils.toString(response.getEntityInputStream());
System.out.println("3");
			System.out.println(neo4jResponse);
System.out.println("4");
			response.getEntityInputStream().close();
System.out.println("5");
			response.close();
System.out.println("6");
			JSONObject json = new JSONObject(neo4jResponse);
System.out.println("7");
			return json;
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:4447/"), new ResourceConfig(HelloWorldResource.class));
	}
}