package org.cytoscape.rest.internal.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.cytoscape.io.write.CyWriter;
import org.cytoscape.io.write.VizmapWriterFactory;
import org.cytoscape.rest.internal.CyActivator.WriterListener;
import org.cytoscape.rest.internal.MappingFactoryManager;
import org.cytoscape.rest.internal.datamapper.VisualStyleMapper;
import org.cytoscape.rest.internal.model.Count;
import org.cytoscape.rest.internal.serializer.VisualStyleModule;
import org.cytoscape.rest.internal.serializer.VisualStyleSerializer;
import org.cytoscape.rest.internal.task.HeadlessTaskMonitor;
import org.cytoscape.view.model.DiscreteRange;
import org.cytoscape.view.model.Range;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.vizmap.VisualMappingFunction;
import org.cytoscape.view.vizmap.VisualPropertyDependency;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.work.TaskMonitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(tags = {CyRESTSwagger.CyRESTSwaggerConfig.VISUAL_STYLES_TAG})
@Singleton
@Path("/v1/styles")
public class StyleResource extends AbstractResource {

	private final VisualStyleSerializer styleSerializer = new VisualStyleSerializer();

	@Inject
	private WriterListener writerListener;

	@Inject
	private TaskMonitor tm;

	@Inject
	private VisualStyleFactory vsFactory;

	@Inject
	private MappingFactoryManager factoryManager;

	private final ObjectMapper styleMapper;
	private final VisualStyleMapper visualStyleMapper;
	private final VisualStyleSerializer visualStyleSerializer;

	public StyleResource() {
		super();
		this.styleMapper = new ObjectMapper();
		this.styleMapper.registerModule(new VisualStyleModule());

		this.visualStyleMapper = new VisualStyleMapper();
		this.visualStyleSerializer = new VisualStyleSerializer();
	}

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get list of Visual Style titles", notes="Returns a list of Visual Style titles")
	public String getStyleNames() {
		final Collection<VisualStyle> styles = vmm.getAllVisualStyles();
		final List<String> styleNames = new ArrayList<String>();
		for (final VisualStyle style : styles) {
			styleNames.add(style.getTitle());
		}
		try {
			return getNames(styleNames);
		} catch (IOException e) {
			throw getError("Could not get style names.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}

	}

	@GET
	@Path("/count")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Get number of Visual Styles",
    notes = "Returns the number of Visual Styles available in current session",
    response = Count.class)
	public Count getStyleCount() {
		return new Count((long)vmm.getAllVisualStyles().size());
	}

	@DELETE
	@Path("/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Delete a Visual Style")
	public Response deleteStyle(
			@ApiParam(value="Visual Style Name")@PathParam("name") String name) {
		vmm.removeVisualStyle(getStyleByName(name));
		return Response.ok().build();
	}
	
	@DELETE
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Delete all Visual Styles except default style")
	public Response deleteAllStyles() {
		Set<VisualStyle> styles = vmm.getAllVisualStyles();
		Set<VisualStyle> toBeDeleted = new HashSet<VisualStyle>();
		for(final VisualStyle style: styles) {
			if(style.getTitle().equals("default") == false) {
				toBeDeleted.add(style);
			}
		}
		toBeDeleted.stream()
			.forEach(style->vmm.removeVisualStyle(style));
		return Response.ok().build();
	}

	@DELETE
	@Path("/{name}/mappings/{vpName}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Delete a Visual Mapping from a Visual Style")
	public Response deleteMapping(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name, 
			@ApiParam(value="Visual Property name associated with the mapping") @PathParam("vpName") String vpName) {
		final VisualStyle style = getStyleByName(name);
		final VisualProperty<?> vp = getVisualProperty(vpName);
		if(vp == null) {
			throw new NotFoundException("Could not find Visual Property: " + vpName);
		}
		final VisualMappingFunction<?,?> mapping = style.getVisualMappingFunction(vp);
		if(mapping == null) {
			throw new NotFoundException("Could not find mapping for: " + vpName);
		}
		style.removeVisualMappingFunction(vp);
		
		return Response.ok().build();
	}

	@GET
	@Path("/{name}/defaults")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get all default values for the Visual Style", notes="Returns a list of all default values.")
	public String getDefaults(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name) {
		return serializeDefaultValues(name);
	}

	@GET
	@Path("/{name}/defaults/{vp}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get a default value for the Visual Property")
	public String getDefaultValue(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name, 
			@ApiParam(value="Visual Property ID") @PathParam("vp") String vp) {
		return serializeDefaultValue(name, vp);
	}

	@GET
	@Path("/visualproperties/{vp}/values")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get all available range values for the Visual Property",
			notes="Returns a list of all available values for the visual property. This method is only for Visual Properties with DiscreteRange, such as NODE_SHAPE or EDGE_LINE_TYPE.")
	public String getRangeValues(
			@ApiParam(value="Visual Property ID") @PathParam("vp") String vp) {
		return serializeRangeValue(vp);
	}

	@GET
	@Path("/visualproperties")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get all available Visual Properties")
	public Collection<org.cytoscape.rest.internal.model.VisualProperty> getVisualProperties() {
		Set<VisualProperty<?>> vps = getAllVisualProperties();	
		return vps.stream().map(cyVp -> new org.cytoscape.rest.internal.model.VisualProperty((VisualProperty<Object>) cyVp)).collect(Collectors.toList());
	}

	@GET
	@Path("/visualproperties/{visualProperty}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get a Visual Property", notes="Return a Visual Property as an object")
	public String getSingleVisualProperty(
			@ApiParam(value="Visual Property ID") @PathParam("visualProperty") String visualProperty) {
		final VisualProperty<?> vp = getVisualProperty(visualProperty);
		try {
			return styleSerializer.serializeVisualProperty(vp);
		} catch (IOException e) {
			throw getError("Could not serialize Visual Properties.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@GET
	@Path("/{name}/mappings")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get all Visual Mappings for the Visual Style",
	notes="Returns a list of all Visual Mappings.")
	public String getMappings(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name) {
		final VisualStyle style = getStyleByName(name);
		final Collection<VisualMappingFunction<?, ?>> mappings = style.getAllVisualMappingFunctions();
		if(mappings.isEmpty()) {
			return "[]";
		}
		
		try {
			return styleMapper.writeValueAsString(mappings);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			throw getError("Could not serialize Mappings.", e, Response.Status.INTERNAL_SERVER_ERROR);
		} catch(Exception ex) {
			ex.printStackTrace();
			throw getError("Could not serialize Mappings.", ex, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@GET
	@Path("/{name}/mappings/{vp}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get a Visual Mapping associated with the Visual Property",
	notes="Returns the Visual Mapping assigned to the Visual Property")
	public String getMapping(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name, 
			@ApiParam(value="Visual Property ID") @PathParam("vp") String vp) {
		final VisualStyle style = getStyleByName(name);
		final VisualMappingFunction<?, ?> mapping = getMappingFunction(vp, style);
		
		try {
			return styleMapper.writeValueAsString(mapping);
		} catch (JsonProcessingException e) {
			throw getError("Could not serialize Mapping.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}
	
	private final VisualMappingFunction<?, ?> getMappingFunction(final String vp, final VisualStyle style) {
		final VisualLexicon lexicon = getLexicon();
		final Set<VisualProperty<?>> allVp = lexicon.getAllVisualProperties();
		VisualProperty<?> visualProp = null;
		for (VisualProperty<?> curVp : allVp) {
			if (curVp.getIdString().equals(vp)) {
				visualProp = curVp;
				break;
			}
		}
		if (visualProp == null) {
			throw new NotFoundException("Could not find VisualProperty: " + vp);
		}
		final VisualMappingFunction<?, ?> mapping = style.getVisualMappingFunction(visualProp);
		
		if(mapping == null) {
			throw new NotFoundException("Could not find visual mapping function for " + vp);
		}
		
		return mapping;
	}

	private final String serializeDefaultValues(String name) {
		final VisualStyle style = getStyleByName(name);
		final VisualLexicon lexicon = getLexicon();
		final Collection<VisualProperty<?>> vps = lexicon.getAllVisualProperties();
		try {
			return styleSerializer.serializeDefaults(vps, style);
		} catch (IOException e) {
			throw getError("Could not serialize default values.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}


	private final String serializeDefaultValue(final String styleName, final String vpName) {
		final VisualStyle style = getStyleByName(styleName);
		VisualProperty<Object> vp = (VisualProperty<Object>) getVisualProperty(vpName);
		try {
			return styleSerializer.serializeDefault(vp, style);
		} catch (IOException e) {
			throw getError("Could not serialize default value for " + vpName, e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}


	private final String serializeRangeValue(final String vpName) {
		VisualProperty<Object> vp = (VisualProperty<Object>) getVisualProperty(vpName);
		Range<Object> range = vp.getRange();
		if (range.isDiscrete()) {
			final DiscreteRange<Object> discRange = (DiscreteRange<Object>)range;
			try {
				return styleSerializer.serializeDiscreteRange(vp, discRange);
			} catch (IOException e) {
				throw getError("Could not serialize default value for "
						+ vpName, e, Response.Status.INTERNAL_SERVER_ERROR);
			}
		} else {
			throw new NotFoundException("Range object is not available for " + vpName);
		}
	}

	private final Set<VisualProperty<?>> getAllVisualProperties() {
		final VisualLexicon lexicon = getLexicon();
		return lexicon.getAllVisualProperties();
	}

	private final VisualProperty<?> getVisualProperty(String vpName) {
		final VisualLexicon lexicon = getLexicon();
		final Collection<VisualProperty<?>> vps = lexicon.getAllVisualProperties();
		for (VisualProperty<?> vp : vps) {
			if (vp.getIdString().equals(vpName)) {
				return vp;
			}
		}

		return null;
	}

	// /////////// Update Default Values ///////////////

	@PUT
	@Path("/{name}/defaults")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Update a default value for the Visual Property",
			notes="The body of the request should be a list of key-value pair:\n"
			 +"\n```\n"
	 
	+ "[\n"
	+ "  {\n"
	+ "    \"visualProperty\": VISUAL_PROPERTY_ID\n"
	+ "    \"value\": value\n"
	+ "  }, ...\n"
	+ "]\n"
			
	+"```\n")
	public Response updateDefaults(
			@ApiParam(value="Name of the Visual Style")@PathParam("name") String name, 
			InputStream is) {
			
		final VisualStyle style = getStyleByName(name);
		final ObjectMapper objMapper = new ObjectMapper();
		try {
			final JsonNode rootNode = objMapper.readValue(is, JsonNode.class);
			updateVisualProperties(rootNode, style);
		} catch (Exception e) {
			throw getError("Could not update default values.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
		
		return Response.ok().build();
	}

	@SuppressWarnings("unchecked")
	private final void updateVisualProperties(final JsonNode rootNode, VisualStyle style) {
		for(JsonNode defaultNode: rootNode) {
			final String vpName = defaultNode.get(VisualStyleMapper.MAPPING_VP).textValue();

			@SuppressWarnings("rawtypes")
			final VisualProperty vp = getVisualProperty(vpName);
			if (vp == null) {
				continue;
			}
			final Object value = vp.parseSerializableString(defaultNode.get("value").asText());
			if(value != null) {
				style.setDefaultValue(vp, value);
			}
		}
	}

	@GET
	@Path("/{name}.json")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get a Visual Style in Cytoscape.js CSS format.",
			notes="Visual Style in Cytoscape.js CSS format. This is always in an array.")
	public String getStyle(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name) {
		if(networkViewManager.getNetworkViewSet().isEmpty()) {
			throw getError("You need at least one view object to use this feature."
					, new IllegalStateException(), Response.Status.INTERNAL_SERVER_ERROR);
		}
		final VisualStyle style = getStyleByName(name);
		final VizmapWriterFactory jsonVsFact = this.writerListener.getFactory();
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		final Set<VisualStyle> styleCollection = new HashSet<VisualStyle>();
		styleCollection.add(style);
		try {
			final CyWriter styleWriter = jsonVsFact.createWriter(os, styleCollection);
			styleWriter.run(new HeadlessTaskMonitor());
			String jsonString = os.toString("UTF-8");
			os.close();
			return jsonString;
		} catch (Exception e) {
			throw getError("Could not get Visual Style in Cytoscape.js format.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@GET
	@Path("/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get a Visual Style with full details",
			notes="This returns JSON version of Visual Style object with full details.  Format is simple:\n"
		+ "\n```\n"
	 + "{\n"
	 + 	"  \"title\": (name of this Visual Style),\n"
	 + 	"  \"defaults\": [ default values ],\n"
	+ 	"  \"mappings\": [ Mappings ]\n"
	 + "}\n"
	 + "```\n"
	 + "Essentially, this is a JSON version of the Visual Style XML file\n"
			)
	public String getStyleFull(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name) {
		final VisualStyle style = getStyleByName(name);
		try {
			return styleSerializer.serializeStyle(getLexicon().getAllVisualProperties(), style);
		} catch (IOException e) {
			throw getError("Could not get Visual Style JSON.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Create a new Visual Style from JSON.", notes="Returns the title of the new Visual Style.")
	public Response createStyle(InputStream is) {
		final ObjectMapper objMapper = new ObjectMapper();
		JsonNode rootNode;
		try {
			rootNode = objMapper.readValue(is, JsonNode.class);
			VisualStyle style = this.visualStyleMapper.buildVisualStyle(factoryManager, vsFactory, getLexicon(),
					rootNode);
			vmm.addVisualStyle(style);
			
			// This may be different from the original one if same name exists.
			final String result = "{\"title\": \""+ style.getTitle() + "\"}";
			
			return Response.status(Response.Status.CREATED).entity(result).build();
		} catch (Exception e) {
			throw getError("Could not create new Visual Style.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@POST
	@Path("/{name}/mappings")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Add a new Visual Mapping", notes="Create a new Visual Mapping function from JSON and add it to the Style.\n\n"
				+ "#### Discrete Mapping\n"
				+ "#### Continuous Mapping\n"
				+ "#### Passthrough Mapping\n"
			)
	public Response createMappings(
			@ApiParam(value="Name of the Visual Style") @PathParam("name") String name,
			InputStream is) {
		final VisualStyle style = getStyleByName(name);
		final ObjectMapper objMapper = new ObjectMapper();
		JsonNode rootNode;
		try {
			rootNode = objMapper.readValue(is, JsonNode.class);
			this.visualStyleMapper.buildMappings(style, factoryManager, getLexicon(),rootNode);
		} catch (Exception e) {
			e.printStackTrace();
			throw getError("Could not create new Mapping.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
		return Response.status(Response.Status.CREATED).build();
	}

	@PUT
	@Path("/{name}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Update name of a Visual Style")
	public void updateStyleName(
			@ApiParam(value="Original name of the Visual Style") @PathParam("name") String name, InputStream is) {
		final VisualStyle style = getStyleByName(name);
		final ObjectMapper objMapper = new ObjectMapper();
		JsonNode rootNode;
		try {
			rootNode = objMapper.readValue(is, JsonNode.class);
			this.visualStyleMapper.updateStyleName(style, getLexicon(), rootNode);
		} catch (Exception e) {
			throw getError("Could not update Visual Style title.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@PUT
	@Path("/{name}/mappings/{vp}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Update an existing Visual Mapping",
			notes="Currently, this is same as POST; it simply replaces existing mapping. You need to send complete information for the new mappings.")
	public Response updateMapping(
			@ApiParam(value="Name of visual Style") @PathParam("name") String name,  
			@ApiParam(value="Target Visual Property") @PathParam("vp") String vp, 
			InputStream is) {
		final VisualStyle style = getStyleByName(name);
		final VisualMappingFunction<?, ?> currentMapping = getMappingFunction(vp, style);
	
		final ObjectMapper objMapper = new ObjectMapper();
		JsonNode rootNode;
		try {
			rootNode = objMapper.readValue(is, JsonNode.class);
			this.visualStyleMapper.buildMappings(style, factoryManager, getLexicon(),rootNode);
		} catch (Exception e) {
			e.printStackTrace();
			throw getError("Could not update Mapping.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
		
		return Response.ok().build();
	}


	private final VisualStyle getStyleByName(final String name) {
		final Collection<VisualStyle> styles = vmm.getAllVisualStyles();
		for (final VisualStyle style : styles) {
			if (style.getTitle().equals(name)) {
				return style;
			}
		}

		throw new NotFoundException("Could not find Visual Style: " + name);
	}

	@GET
	@Path("/{name}/dependencies")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Get all Visual Property Dependency status",
			notes="Check status of Visual Property Dependencies.  If a dependency is enabled, it has true for \"enabled.\"\n\nReturns a list of the status of all Visual Property dependencies.")
	public String getAllDependencies(
			@ApiParam("Name of the Visual Style") @PathParam("name") String name) {
		final VisualStyle style = getStyleByName(name);
		
		final Set<VisualPropertyDependency<?>> dependencies = style.getAllVisualPropertyDependencies();
		try {
			return visualStyleSerializer.serializeDependecies(dependencies);
		} catch (IOException e) {
			throw getError("Could not get Visual Property denendencies.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	@PUT
	@Path("/{name}/dependencies")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value="Set Visual Property Dependency flags",
			notes="The body should be the following format:\n\n"
					+ "\n```\n"
					+ "[\n"
	+ "  {\n"
	+ "    \"visualPropertyDependnecy\" : \"DEPENDENCY_ID\",\n"
	+ "    \"enabled\" : true or false\n"
	+ "  }, ... {}\n"
	+ "]\n"
	+ "```\n"
	 )
	public void updateDependencies(
			@ApiParam(value="Name of Visual Style") @PathParam("name") String name, 
			InputStream is) {
		final VisualStyle style = getStyleByName(name);
		
		final ObjectMapper objMapper = new ObjectMapper();
		JsonNode rootNode;
		try {
			rootNode = objMapper.readValue(is, JsonNode.class);
			this.visualStyleMapper.updateDependencies(style, rootNode);
		} catch (Exception e) {
			throw getError("Could not update Visual Style title.", e, Response.Status.INTERNAL_SERVER_ERROR);
		}
	}
}