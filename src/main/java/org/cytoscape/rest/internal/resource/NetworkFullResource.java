package org.cytoscape.rest.internal.resource;

import java.util.Set;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.cytoscape.model.CyNetwork;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(tags = {CyRESTSwagger.CyRESTSwaggerConfig.NETWORKS_TAG})
@Singleton
@Path("/v1/networks.json")
public class NetworkFullResource extends AbstractResource {

	public NetworkFullResource() {
		super();
	}
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	@ApiOperation(
			value=" Get networks in Cytoscape.js JSON format",
			notes="Returns full list of network data as an array of "
					+ "[Cytoscape.js](http://cytoscape.github.io/cytoscape.js/) entries. If no query parameter is "
					+ "given, returns all networks in current session."
	)
	public String getNetworks(
			@ApiParam(value="Network table column name to be used for search", required=false) @QueryParam("column") String column, 
			@ApiParam(value="Search query string", required=false) @QueryParam("query") String query) {
		Set<CyNetwork> networks;
		
		if (column == null && query == null) {
			networks = networkManager.getNetworkSet();
		} else {
			if(column == null || column.length() == 0) {
				throw getError("Column name parameter is missing.", new IllegalArgumentException(), Response.Status.INTERNAL_SERVER_ERROR);
			}
			if(query == null || query.length() == 0) {
				throw getError("Query parameter is missing.", new IllegalArgumentException(), Response.Status.INTERNAL_SERVER_ERROR);
			}
			networks = getNetworksByQuery(query, column);
		}
		
		return getNetworksAsString(networks);
	}


	private final String getNetworksAsString(final Set<CyNetwork> networks) {
		if (networks.isEmpty()) {
			return "[]";
		}

		final StringBuilder result = new StringBuilder();
		result.append("[");

		for (final CyNetwork network : networks) {
			result.append(getNetworkString(network));
			result.append(",");
		}
		String jsonString = result.toString();
		jsonString = jsonString.substring(0, jsonString.length() - 1);

		return jsonString + "]";
	}
}
