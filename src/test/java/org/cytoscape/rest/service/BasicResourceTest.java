package org.cytoscape.rest.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.NetworkViewRenderer;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.command.AvailableCommands;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.ding.DVisualLexicon;
import org.cytoscape.ding.Justification;
import org.cytoscape.ding.NetworkViewTestSupport;
import org.cytoscape.ding.ObjectPosition;
import org.cytoscape.ding.Position;
import org.cytoscape.ding.customgraphics.CustomGraphicsManager;
import org.cytoscape.ding.impl.ObjectPositionImpl;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupFactory;
import org.cytoscape.group.CyGroupManager;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.io.write.CyNetworkViewWriterFactory;
import org.cytoscape.io.write.CyWriter;
import org.cytoscape.io.write.PresentationWriterFactory;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableFactory;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CyRootNetworkManager;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.property.CyProperty;
import org.cytoscape.rest.internal.BundleResourceProvider;
import org.cytoscape.rest.internal.CyActivator.LevelOfDetails;
import org.cytoscape.rest.internal.CyActivator.WriterListener;
import org.cytoscape.rest.internal.CyNetworkViewWriterFactoryManager;
import org.cytoscape.rest.internal.EdgeBundler;
import org.cytoscape.rest.internal.GraphicsWriterManager;
import org.cytoscape.rest.internal.MappingFactoryManager;
import org.cytoscape.rest.internal.TaskFactoryManager;
import org.cytoscape.rest.internal.commands.resources.CommandResource;
import org.cytoscape.rest.internal.reader.EdgeListReaderFactory;
import org.cytoscape.rest.internal.resource.AlgorithmicResource;
import org.cytoscape.rest.internal.resource.CollectionResource;
import org.cytoscape.rest.internal.resource.CyRESTCommandSwagger;
import org.cytoscape.rest.internal.resource.CyRESTSwagger;
import org.cytoscape.rest.internal.resource.GlobalTableResource;
import org.cytoscape.rest.internal.resource.GroupResource;
import org.cytoscape.rest.internal.resource.MiscResource;
import org.cytoscape.rest.internal.resource.NetworkFullResource;
import org.cytoscape.rest.internal.resource.NetworkNameResource;
import org.cytoscape.rest.internal.resource.NetworkResource;
import org.cytoscape.rest.internal.resource.NetworkViewResource;
import org.cytoscape.rest.internal.resource.RootResource;
import org.cytoscape.rest.internal.resource.SessionResource;
import org.cytoscape.rest.internal.resource.StyleResource;
import org.cytoscape.rest.internal.resource.SwaggerUIResource;
import org.cytoscape.rest.internal.resource.TableResource;
import org.cytoscape.rest.internal.resource.UIResource;
import org.cytoscape.rest.internal.task.CoreServiceModule;
import org.cytoscape.rest.internal.task.HeadlessTaskMonitor;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.CySessionManager;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.task.create.NewNetworkSelectedNodesAndEdgesTaskFactory;
import org.cytoscape.task.create.NewSessionTaskFactory;
import org.cytoscape.task.read.LoadNetworkURLTaskFactory;
import org.cytoscape.task.read.OpenSessionTaskFactory;
import org.cytoscape.task.select.SelectFirstNeighborsTaskFactory;
import org.cytoscape.task.write.ExportNetworkViewTaskFactory;
import org.cytoscape.task.write.SaveSessionAsTaskFactory;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.presentation.RenderingEngine;
import org.cytoscape.view.presentation.RenderingEngineFactory;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.ArrowShapeVisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.LineTypeVisualProperty;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.vizmap.VisualMappingFunction;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.view.vizmap.internal.VisualStyleFactoryImpl;
import org.cytoscape.view.vizmap.internal.mappings.ContinuousMappingFactory;
import org.cytoscape.view.vizmap.internal.mappings.DiscreteMappingFactory;
import org.cytoscape.view.vizmap.internal.mappings.PassthroughMappingFactory;
import org.cytoscape.view.vizmap.mappings.BoundaryRangeValues;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;

import org.cytoscape.work.util.BoundedDouble;
import org.cytoscape.work.util.ListSingleSelection;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.spi.TestContainer;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.util.tracker.ServiceTracker;

import com.eclipsesource.jaxrs.provider.gson.GsonProvider;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class BasicResourceTest extends JerseyTest {

	protected NetworkTestSupport nts = new NetworkTestSupport();
	protected NetworkViewTestSupport nvts = new NetworkViewTestSupport();

	protected final CoreServiceModule binder;

	CyGroup cyGroup;
	CyNode cyGroupNode;
	
	protected CyRootNetworkManager rootNetworkManager;
	
	protected CyNetwork network;
	protected CyNetworkView view;

	protected VisualStyle style;
	protected VisualLexicon lexicon;

	private PassthroughMappingFactory passthroughFactory;
	private ContinuousMappingFactory continuousFactory;
	private DiscreteMappingFactory discreteFactory;

	protected TaskFactoryManager tfManager;
	protected InputStreamTaskFactory inputStreamCXTaskFactory;
	
	protected CyNetworkReader inputStreamCXNetworkReader;
	
	protected CyNetworkManager networkManager = nts.getNetworkManager();

	protected LoadNetworkURLTaskFactory loadNetworkURLTaskFactory;
	
	protected RenderingEngine<?> renderingEngine;
	
	protected SaveSessionAsTaskFactory saveSessionAsTaskFactory;
	protected OpenSessionTaskFactory openSessionTaskFactory;
	protected NewSessionTaskFactory newSessionTaskFactory;
	protected SelectFirstNeighborsTaskFactory selectFirstNeighborsTaskFactory;

	protected GraphicsWriterManager graphicsWriterManager;
	
	protected ExportNetworkViewTaskFactory exportNetworkViewTaskFactory;
	
	protected PresentationWriterFactory presentationWriterFactory;
	
	protected MappingFactoryManager mappingFactoryManager = new MappingFactoryManager();

	protected final String DUMMY_NAMESPACE = "dummyNamespace";
	protected final String DUMMY_COMMAND = "dummyCommand";
	protected final String DUMMY_ARGUMENT_NAME = "dummyArgument";
	protected final String DUMMY_ARGUMENT_DESCRIPTION = "dummyArgumentDescription";
	protected final Class DUMMY_ARGUMENT_CLASS = int.class;
	protected final boolean DUMMY_ARGUMENT_REQUIRED = false;
	
	protected CyRESTSwagger cyRESTSwagger;
	
	protected final String cyRESTPort = "1234";
	
	protected final String logLocation = "dummyLogLocation";

	protected interface DummyCyWriter extends CyWriter
	{
		public BoundedDouble getZoom();
		public ListSingleSelection<String> getUnits();
		public void setHeight(Double height);
	}
	
	public BasicResourceTest() {
		CyLayoutAlgorithm def = mock(CyLayoutAlgorithm.class);
		Object context = new Object();
		when(def.createLayoutContext()).thenReturn(context);
		when(def.getDefaultLayoutContext()).thenReturn(context);
		when(def.getName()).thenReturn("grid");
		TaskIterator gridLayoutTaskIterator = new TaskIterator();
		gridLayoutTaskIterator.append(mock(Task.class));
		when(def.createTaskIterator(any(CyNetworkView.class), anyObject(), any(Set.class), any(String.class))).thenReturn(gridLayoutTaskIterator);

		Collection<CyLayoutAlgorithm> algorithms = new ArrayList<>();
		algorithms.add(def);
		CyLayoutAlgorithmManager layouts = mock(CyLayoutAlgorithmManager.class);
		when(layouts.getDefaultLayout()).thenReturn(def);
		when(layouts.getAllLayouts()).thenReturn(algorithms);
		when(layouts.getLayout("grid")).thenReturn(def);

		CyNetworkFactory netFactory = nts.getNetworkFactory();
		this.network = createNetwork("network1");
		this.view = nvts.getNetworkViewFactory().createNetworkView(network);
		networkManager.addNetwork(network);

		CyNetwork network2 = createNetwork("network2");
		networkManager.addNetwork(network2);

		rootNetworkManager = nts.getRootNetworkFactory();
		CyNetworkViewManager viewManager = mock(CyNetworkViewManager.class);
		Collection<CyNetworkView> views = new HashSet<>();
		views.add(view);
		when(viewManager.getNetworkViews(network)).thenReturn(views);

		CyApplicationManager cyApplicationManager = mock(CyApplicationManager.class);
		CyNetworkViewFactory viewFactory = nvts.getNetworkViewFactory();

		tfManager = mock(TaskFactoryManager.class);
		inputStreamCXTaskFactory = mock(InputStreamTaskFactory.class);
		
		TaskIterator inputStreamTaskIterator = new TaskIterator();
		
		// Start Mocks for CX URL Reading
		inputStreamCXNetworkReader = mock(CyNetworkReader.class);
	
		CyRootNetwork cyRootNetwork = mock(CyRootNetwork.class);//, withSettings().extraInterfaces(CySubNetwork.class));
		CySubNetwork cySubNetwork = mock(CySubNetwork.class);
		
		when(inputStreamCXNetworkReader.buildCyNetworkView(cyRootNetwork)).thenReturn(mock(CyNetworkView.class));
		when(inputStreamCXNetworkReader.buildCyNetworkView(cySubNetwork)).thenReturn(mock(CyNetworkView.class));
		
		when(cyRootNetwork.getSUID()).thenReturn(1l);
		when(cySubNetwork.getSUID()).thenReturn(2l);
		when(cyRootNetwork.getDefaultNetworkTable()).thenReturn(mock(CyTable.class));
		when(cySubNetwork.getDefaultNetworkTable()).thenReturn(mock(CyTable.class));
		when(cySubNetwork.getRootNetwork()).thenReturn(cyRootNetwork);
		CyRow cyRow = mock(CyRow.class);
		when(cyRow.get(CyNetwork.NAME, String.class)).thenReturn("dummy cx network name");
		when(cyRootNetwork.getRow(cyRootNetwork)).thenReturn(cyRow);
		when(cySubNetwork.getRow(cySubNetwork)).thenReturn(cyRow);
		CyNetwork[] inputStreamNetworks = new CyNetwork[]{cySubNetwork, cyRootNetwork};
		when(inputStreamCXNetworkReader.getNetworks()).thenReturn(inputStreamNetworks);
		
		inputStreamTaskIterator.append(inputStreamCXNetworkReader);
		
		when(inputStreamCXTaskFactory.createTaskIterator(any(InputStream.class), eq("cx file"))).thenReturn(inputStreamTaskIterator);
		when(tfManager.getInputStreamTaskFactory(eq("cytoscapeCxNetworkReaderFactory"))).thenReturn(inputStreamCXTaskFactory);
		// End Mocks for CX URL Reading
		
		VisualMappingManager vmm = mock(VisualMappingManager.class);
		Set<VisualStyle> styles = new HashSet<VisualStyle>();
		VisualStyle mockStyle = mock(VisualStyle.class);
		when(mockStyle.getTitle()).thenReturn("mock1");
		styles.add(mockStyle);
		try {
			this.style = initStyle();
			styles.add(this.style);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException("Could not init Style.", e);
		}
		when(vmm.getAllVisualStyles()).thenReturn(styles);
		Set<VisualLexicon> lex = new HashSet<>();
		lex.add(lexicon);
		when(vmm.getAllVisualLexicon()).thenReturn(lex);
		when(vmm.getDefaultVisualStyle()).thenReturn(this.style);

		CyNetworkViewWriterFactory cytoscapeJsWriterFactory = mock(CyNetworkViewWriterFactory.class);
		when(cytoscapeJsWriterFactory.createWriter(any(OutputStream.class), any(CyNetworkView.class))).thenReturn(mock(CyWriter.class));
		
		ServiceTracker cytoscapeJsWriterFactoryTracker = mock(ServiceTracker.class);
		when(cytoscapeJsWriterFactoryTracker.getService()).thenReturn(cytoscapeJsWriterFactory);
		
		WriterListener writerListsner = mock(WriterListener.class);
		TaskMonitor headlessTaskMonitor = new HeadlessTaskMonitor();

		CyTableManager tableManager = mock(CyTableManager.class);

		VisualStyleFactory vsFactory = mock(VisualStyleFactory.class);
		VisualStyle emptyStyle = mock(VisualStyle.class);
		when(vsFactory.createVisualStyle(anyString())).thenReturn(emptyStyle);

		CyGroupFactory groupFactory = mock(CyGroupFactory.class);
		cyGroup = mock(CyGroup.class);
		cyGroupNode = mock(CyNode.class);
		when(cyGroupNode.getSUID()).thenReturn(0l);
		when(cyGroup.getGroupNode()).thenReturn(cyGroupNode);
		when(groupFactory.createGroup(any(CyNetwork.class), any(List.class), eq(null), eq(true))).thenReturn(cyGroup);
		CyGroupManager groupManager = mock(CyGroupManager.class);
		loadNetworkURLTaskFactory = mock(LoadNetworkURLTaskFactory.class);
		CyNetworkReader cyNetworkReader = mock(CyNetworkReader.class);
	
		when(cyNetworkReader.getNetworks()).thenReturn(new CyNetwork[]{network});
		when(loadNetworkURLTaskFactory.loadCyNetworks((java.net.URL) anyObject())).thenReturn(new TaskIterator(cyNetworkReader));
		
		CyProperty<Properties> cyPropertyServiceRef = mock(CyProperty.class);
		NewNetworkSelectedNodesAndEdgesTaskFactory networkSelectedNodesAndEdgesTaskFactory = mock(NewNetworkSelectedNodesAndEdgesTaskFactory.class);
		EdgeListReaderFactory edgeListReaderFactory = mock(EdgeListReaderFactory.class);
		
		InputStreamTaskFactory cytoscapeJsReaderFactory = mock(InputStreamTaskFactory.class);
		when(cytoscapeJsReaderFactory.createTaskIterator((InputStream)anyObject(), (String)anyObject())).thenReturn(new TaskIterator(cyNetworkReader));
		ServiceTracker cytoscapeJsReaderFactoryTracker = mock(ServiceTracker.class);
		when(cytoscapeJsReaderFactoryTracker.getService()).thenReturn(cytoscapeJsReaderFactory);
		
		CyTableFactory tableFactory = mock(CyTableFactory.class);
		NetworkTaskFactory fitContentTaskFactory = mock(NetworkTaskFactory.class);
		TaskIterator fitTaskIterator = new TaskIterator();
		fitTaskIterator.append(mock(Task.class));
		
		when(fitContentTaskFactory.createTaskIterator(any(CyNetwork.class))).thenReturn(fitTaskIterator);
		
		EdgeBundler edgeBundler = mock(EdgeBundler.class);
		NetworkTaskFactory edgeBundlerTaskFactory = mock(NetworkTaskFactory.class);
		TaskIterator edgeBundlerTaskIterator = new TaskIterator();
		edgeBundlerTaskIterator.append(mock(Task.class));
		when(edgeBundlerTaskFactory.createTaskIterator(any(CyNetwork.class))).thenReturn(edgeBundlerTaskIterator);
		when(edgeBundler.getBundlerTF()).thenReturn(edgeBundlerTaskFactory);
		
		RenderingEngineManager renderingEngineManager = mock(RenderingEngineManager.class);
		
		renderingEngine = mock(RenderingEngine.class);
		when(renderingEngine.getRendererId()).thenReturn("org.cytoscape.ding");
		Collection<RenderingEngine<?>> renderingEngines = new ArrayList<RenderingEngine<?>>();
		renderingEngines.add(renderingEngine);
		
		when(renderingEngineManager.getRenderingEngines(anyObject())).thenReturn(renderingEngines);
	
		
		CySessionManager sessionManager = mock(CySessionManager.class);
		when(sessionManager.getCurrentSessionFileName()).thenReturn("testSession");

		saveSessionAsTaskFactory = mock(SaveSessionAsTaskFactory.class);
		Task mockTask = mock(Task.class);
		when(saveSessionAsTaskFactory.createTaskIterator((File) anyObject())).thenReturn(new TaskIterator(mockTask));
		openSessionTaskFactory = mock(OpenSessionTaskFactory.class);
		when(openSessionTaskFactory.createTaskIterator((File) anyObject())).thenReturn(new TaskIterator(mockTask));
		newSessionTaskFactory = mock(NewSessionTaskFactory.class);
		when(newSessionTaskFactory.createTaskIterator(true)).thenReturn(new TaskIterator(mockTask));
		CySwingApplication desktop = mock(CySwingApplication.class);
		
		NetworkTaskFactory lodNetworkTaskFactory = mock(NetworkTaskFactory.class);
		TaskIterator lodTaskIterator = new TaskIterator();
		lodTaskIterator.append(mock(Task.class));
		when(lodNetworkTaskFactory.createTaskIterator(null)).thenReturn(lodTaskIterator);
		
		LevelOfDetails lodTF = mock(LevelOfDetails.class);
		when(lodTF.getLodTF()).thenReturn(lodNetworkTaskFactory);
		
		selectFirstNeighborsTaskFactory = mock(SelectFirstNeighborsTaskFactory.class);

		graphicsWriterManager = mock(GraphicsWriterManager.class);
		
		presentationWriterFactory = mock(PresentationWriterFactory.class);
	
		DummyCyWriter cyWriter = mock(DummyCyWriter.class);
		BoundedDouble boundedDouble = new BoundedDouble(0d,1d,10d, true, true);
		
		ListSingleSelection<String> listSingleSelection =  new ListSingleSelection<String>();
	
		when(cyWriter.getUnits()).thenReturn(listSingleSelection);
		when(cyWriter.getZoom()).thenReturn(boundedDouble);
		
		when(presentationWriterFactory.createWriter(anyObject(), anyObject())).thenReturn(cyWriter);
		
		when(graphicsWriterManager.getFactory(anyString())).thenReturn(presentationWriterFactory);
		
		exportNetworkViewTaskFactory = mock(ExportNetworkViewTaskFactory.class);
		TaskIterator exportTaskIterator = new TaskIterator();
		when(exportNetworkViewTaskFactory.createTaskIterator(any(CyNetworkView.class), any(File.class))).thenReturn(exportTaskIterator);

		final AvailableCommands available = mock(AvailableCommands.class);
		
		final List<String> dummynameSpaces = new ArrayList<String>();
		dummynameSpaces.add(DUMMY_NAMESPACE);
		
		final List<String> dummyCommands = new ArrayList<String>();
		dummyCommands.add(DUMMY_COMMAND);
		
		final List<String> dummyArguments = new ArrayList<String>();
		dummyArguments.add(DUMMY_ARGUMENT_NAME);
		
		when(available.getNamespaces()).thenReturn(dummynameSpaces);
		when(available.getCommands(eq(DUMMY_NAMESPACE))).thenReturn(dummyCommands);
		when(available.getArguments(DUMMY_NAMESPACE, DUMMY_COMMAND)).thenReturn(dummyArguments);
		when(available.getArgTypeString(DUMMY_NAMESPACE, DUMMY_COMMAND, DUMMY_ARGUMENT_NAME)).thenReturn(DUMMY_ARGUMENT_CLASS.getName());
		when(available.getArgType(DUMMY_NAMESPACE, DUMMY_COMMAND, DUMMY_ARGUMENT_NAME)).thenReturn(DUMMY_ARGUMENT_CLASS);
		when(available.getArgDescription(DUMMY_NAMESPACE, DUMMY_COMMAND, DUMMY_ARGUMENT_NAME)).thenReturn(DUMMY_ARGUMENT_DESCRIPTION);
		when(available.getArgRequired(DUMMY_NAMESPACE, DUMMY_COMMAND, DUMMY_ARGUMENT_NAME)).thenReturn(false);

		final CommandExecutorTaskFactory ceTaskFactory = mock(CommandExecutorTaskFactory.class);
		TaskIterator dummyTaskIterator = new TaskIterator();
		ObservableTask dummyJsonTask = mock(ObservableTask.class);
		
		//when(dummyJsonTask.)

		when(ceTaskFactory.createTaskIterator(eq(DUMMY_NAMESPACE), eq(DUMMY_COMMAND), any(Map.class), any(TaskObserver.class))).thenReturn(dummyTaskIterator);
		final SynchronousTaskManager<?> synchronousTaskManager = mock(SynchronousTaskManager.class);

		doAnswer(new Answer<Void>() {
			public Void answer(InvocationOnMock invocation) {
				Object[] args = invocation.getArguments();
				if (args[1] instanceof TaskObserver) {
					//TODO make this execute closer to how Commands are actually executed.
					((TaskObserver) args[1]).taskFinished(dummyJsonTask);
					((TaskObserver) args[1]).allFinished(FinishStatus.getSucceeded());
				}
				return null;
			}
		}).when(synchronousTaskManager).execute(any(TaskIterator.class), any(TaskObserver.class));final CyNetworkViewWriterFactoryManager viewWriterFactoryManager = new CyNetworkViewWriterFactoryManager();

		BundleResourceProvider bundleResourceProvider = mock(BundleResourceProvider.class);
		
		try {
			when(bundleResourceProvider.getResourceInputStream("dummyResourcePath")).thenReturn(new ByteArrayInputStream("test data".getBytes()));
		} catch (IOException e) {
			fail();
		}
		
		final String cyRESTPort = this.cyRESTPort;
		
		final String logLocation = this.logLocation;
		
		this.binder = new CoreServiceModule(networkManager, viewManager, netFactory,
				tfManager, cyApplicationManager, vmm, cytoscapeJsWriterFactoryTracker,
				cytoscapeJsReaderFactoryTracker, layouts, writerListsner,
				headlessTaskMonitor, tableManager, vsFactory,
				mappingFactoryManager, groupFactory, groupManager,
				rootNetworkManager, loadNetworkURLTaskFactory,
				cyPropertyServiceRef, networkSelectedNodesAndEdgesTaskFactory,
				edgeListReaderFactory, viewFactory, tableFactory, fitContentTaskFactory,
				edgeBundler, renderingEngineManager, sessionManager, 
				saveSessionAsTaskFactory, openSessionTaskFactory, newSessionTaskFactory, 
				desktop, lodTF, selectFirstNeighborsTaskFactory, graphicsWriterManager, exportNetworkViewTaskFactory,
				available, ceTaskFactory, synchronousTaskManager, viewWriterFactoryManager, 
				bundleResourceProvider,
				cyRESTPort, logLocation);
	}


	private VisualStyle initStyle() throws Exception {
		final CustomGraphicsManager cgManager = mock(CustomGraphicsManager.class);
		lexicon = new DVisualLexicon(cgManager);

		final CyEventHelper eventHelper = mock(CyEventHelper.class);
		
		final CyServiceRegistrar cyServiceRegistrar = mock(CyServiceRegistrar.class);
		when(cyServiceRegistrar.getService(CyEventHelper.class)).thenReturn(mock(CyEventHelper.class));
		passthroughFactory = new PassthroughMappingFactory(cyServiceRegistrar);
		discreteFactory = new DiscreteMappingFactory(cyServiceRegistrar);
		continuousFactory = new ContinuousMappingFactory(cyServiceRegistrar);

		mappingFactoryManager.addFactory(passthroughFactory, null);
		mappingFactoryManager.addFactory(continuousFactory, null);
		mappingFactoryManager.addFactory(discreteFactory, null);

		this.style = generateVisualStyle(lexicon);
		setDefaults();
		setMappings();

		return style;
	}


	private final VisualStyle generateVisualStyle(final VisualLexicon lexicon) {
		final Set<VisualLexicon> lexiconSet = Collections.singleton(lexicon);

		final VisualMappingManager vmMgr = mock(VisualMappingManager.class);
		when(vmMgr.getAllVisualLexicon()).thenReturn(lexiconSet);

		final VisualMappingFunctionFactory ptFactory = mock(VisualMappingFunctionFactory.class);

		CyServiceRegistrar serviceRegistrar = mock(CyServiceRegistrar.class);
		CyEventHelper eventHelper = mock(CyEventHelper.class);

		RenderingEngineFactory engineFactory = mock(RenderingEngineFactory.class);
		when(engineFactory.getVisualLexicon()).thenReturn(lexicon);

		NetworkViewRenderer netViewRenderer = mock(NetworkViewRenderer.class);
		when(netViewRenderer.getRenderingEngineFactory(Mockito.anyString())).thenReturn(engineFactory);

		CyApplicationManager applicationMgr = mock(CyApplicationManager.class);
		when(applicationMgr.getCurrentNetworkViewRenderer()).thenReturn(netViewRenderer);

		when(serviceRegistrar.getService(CyEventHelper.class)).thenReturn(eventHelper);
		when(serviceRegistrar.getService(VisualMappingManager.class)).thenReturn(vmMgr);
		when(serviceRegistrar.getService(CyApplicationManager.class)).thenReturn(applicationMgr);

		VisualStyleFactory vsFactory = new VisualStyleFactoryImpl(serviceRegistrar, ptFactory);

		return vsFactory.createVisualStyle("vs1");
	}

	private final void setDefaults() {
		// Node default values
		style.setDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR, new Color(10, 10, 200));
		style.setDefaultValue(BasicVisualLexicon.NODE_TRANSPARENCY, 200);

		style.setDefaultValue(BasicVisualLexicon.NODE_WIDTH, 40d);
		style.setDefaultValue(BasicVisualLexicon.NODE_HEIGHT, 30d);
		style.setDefaultValue(BasicVisualLexicon.NODE_SIZE, 60d);

		style.setDefaultValue(BasicVisualLexicon.NODE_SHAPE, NodeShapeVisualProperty.ROUND_RECTANGLE);

		style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_PAINT, Color.BLUE);
		style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_WIDTH, 2d);
		style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_TRANSPARENCY, 150);

		style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_COLOR, Color.BLUE);
		style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_SIZE, 18);
		style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_FACE, new Font("Helvetica", Font.PLAIN, 12));
		style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_TRANSPARENCY, 122);
		style.setDefaultValue(DVisualLexicon.NODE_LABEL_POSITION,
				new ObjectPositionImpl(Position.NORTH_EAST, Position.CENTER, Justification.JUSTIFY_CENTER, 0,0));

		// For Selected
		style.setDefaultValue(BasicVisualLexicon.NODE_SELECTED_PAINT, Color.RED);

		// Edge default values
		style.setDefaultValue(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT, new Color(12,100,200));
		style.setDefaultValue(BasicVisualLexicon.EDGE_UNSELECTED_PAINT, new Color(222, 100, 10));

		style.setDefaultValue(BasicVisualLexicon.EDGE_TRANSPARENCY, 100);

		style.setDefaultValue(BasicVisualLexicon.EDGE_LINE_TYPE, LineTypeVisualProperty.DOT);

		style.setDefaultValue(BasicVisualLexicon.EDGE_WIDTH, 3d);

		style.setDefaultValue(BasicVisualLexicon.EDGE_LABEL_COLOR, Color.red);
		style.setDefaultValue(BasicVisualLexicon.EDGE_LABEL_FONT_FACE, new Font("SansSerif", Font.BOLD, 12));
		style.setDefaultValue(BasicVisualLexicon.EDGE_LABEL_FONT_SIZE, 11);
		style.setDefaultValue(BasicVisualLexicon.EDGE_LABEL_TRANSPARENCY, 220);

		style.setDefaultValue(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE, ArrowShapeVisualProperty.DELTA);
		style.setDefaultValue(BasicVisualLexicon.EDGE_SOURCE_ARROW_SHAPE, ArrowShapeVisualProperty.T);

		style.setDefaultValue(DVisualLexicon.EDGE_TARGET_ARROW_UNSELECTED_PAINT, new Color(20, 100, 100));
		style.setDefaultValue(DVisualLexicon.EDGE_SOURCE_ARROW_UNSELECTED_PAINT, new Color(10, 100, 100));

		// For Selected
		style.setDefaultValue(BasicVisualLexicon.EDGE_SELECTED_PAINT, Color.PINK);
		style.setDefaultValue(BasicVisualLexicon.EDGE_STROKE_SELECTED_PAINT, Color.ORANGE);
	}

	private final void setMappings() {
		// Passthrough mappings
		final VisualMappingFunction<String, String> nodeLabelMapping = passthroughFactory.createVisualMappingFunction(
				CyNetwork.NAME, String.class, BasicVisualLexicon.NODE_LABEL);
		final VisualMappingFunction<String, String> edgeLabelMapping = passthroughFactory.createVisualMappingFunction(
				CyEdge.INTERACTION, String.class, BasicVisualLexicon.EDGE_LABEL);
		style.addVisualMappingFunction(nodeLabelMapping);
		style.addVisualMappingFunction(edgeLabelMapping);

		// Continuous mappings
		// Simple two points mapping.
		final ContinuousMapping<Integer, Paint> nodeLabelColorMapping = (ContinuousMapping<Integer, Paint>) continuousFactory
				.createVisualMappingFunction("Degree", Integer.class, BasicVisualLexicon.NODE_LABEL_COLOR);

		final ContinuousMapping<Double, Integer> nodeOpacityMapping = (ContinuousMapping<Double, Integer>) continuousFactory
				.createVisualMappingFunction("Betweenness Centrality", Double.class, BasicVisualLexicon.NODE_TRANSPARENCY);

		final ContinuousMapping<Integer, Double> nodeWidthMapping = (ContinuousMapping<Integer, Double>) continuousFactory
				.createVisualMappingFunction("Degree", Integer.class, BasicVisualLexicon.NODE_WIDTH);
		final ContinuousMapping<Integer, Double> nodeHeightMapping = (ContinuousMapping<Integer, Double>) continuousFactory
				.createVisualMappingFunction("Degree", Integer.class, BasicVisualLexicon.NODE_HEIGHT);

		// Complex multi-point mapping
		final ContinuousMapping<Integer, Paint> nodeColorMapping = (ContinuousMapping<Integer, Paint>) continuousFactory
				.createVisualMappingFunction("Degree", Integer.class, BasicVisualLexicon.NODE_FILL_COLOR);

		final BoundaryRangeValues<Paint> lc1 = new BoundaryRangeValues<Paint>(Color.black, Color.yellow, Color.green);
		final BoundaryRangeValues<Paint> lc2 = new BoundaryRangeValues<Paint>(Color.red, Color.pink, Color.blue);
		nodeLabelColorMapping.addPoint(3, lc1);
		nodeLabelColorMapping.addPoint(10, lc2);
		style.addVisualMappingFunction(nodeLabelColorMapping);

		final BoundaryRangeValues<Paint> color1 = new BoundaryRangeValues<Paint>(Color.black, Color.red, Color.orange);
		final BoundaryRangeValues<Paint> color2 = new BoundaryRangeValues<Paint>(Color.white, Color.white, Color.white);
		final BoundaryRangeValues<Paint> color3= new BoundaryRangeValues<Paint>(Color.green, Color.pink, Color.blue);

		// Shuffle insertion.
		nodeColorMapping.addPoint(2, color1);
		nodeColorMapping.addPoint(5, color2);
		nodeColorMapping.addPoint(10, color3);

		final BoundaryRangeValues<Double> bv0 = new BoundaryRangeValues<Double>(20d, 20d, 20d);
		final BoundaryRangeValues<Double> bv1 = new BoundaryRangeValues<Double>(200d, 200d, 400d);
		nodeWidthMapping.addPoint(1, bv0);
		nodeWidthMapping.addPoint(20, bv1);
		nodeHeightMapping.addPoint(1, bv0);
		nodeHeightMapping.addPoint(20, bv1);

		final BoundaryRangeValues<Integer> trans0 = new BoundaryRangeValues<Integer>(10, 10, 10);
		final BoundaryRangeValues<Integer> trans1 = new BoundaryRangeValues<Integer>(80, 80, 100);
		final BoundaryRangeValues<Integer> trans2 = new BoundaryRangeValues<Integer>(222, 222, 250);
		nodeOpacityMapping.addPoint(0.22, trans0);
		nodeOpacityMapping.addPoint(0.61, trans1);
		nodeOpacityMapping.addPoint(0.95, trans2);

		style.addVisualMappingFunction(nodeWidthMapping);
		style.addVisualMappingFunction(nodeHeightMapping);
		style.addVisualMappingFunction(nodeOpacityMapping);
		style.addVisualMappingFunction(nodeColorMapping);

		// Discrete mappings
		final DiscreteMapping<String, NodeShape> nodeShapeMapping = (DiscreteMapping<String, NodeShape>) discreteFactory
				.createVisualMappingFunction("Node Type", String.class, BasicVisualLexicon.NODE_SHAPE);
		nodeShapeMapping.putMapValue("gene", NodeShapeVisualProperty.DIAMOND);
		nodeShapeMapping.putMapValue("protein", NodeShapeVisualProperty.ELLIPSE);
		nodeShapeMapping.putMapValue("compound", NodeShapeVisualProperty.ROUND_RECTANGLE);
		nodeShapeMapping.putMapValue("pathway", NodeShapeVisualProperty.OCTAGON);

		style.addVisualMappingFunction(nodeShapeMapping);

		final DiscreteMapping<String, ObjectPosition> nodeLabelPosMapping = (DiscreteMapping<String, ObjectPosition>) discreteFactory
				.createVisualMappingFunction("Node Type", String.class, DVisualLexicon.NODE_LABEL_POSITION);
		nodeLabelPosMapping.putMapValue("gene", new ObjectPositionImpl(Position.SOUTH, Position.NORTH_WEST, Justification.JUSTIFY_CENTER, 0,0));
		nodeLabelPosMapping.putMapValue("protein", new ObjectPositionImpl(Position.EAST, Position.WEST, Justification.JUSTIFY_CENTER, 0,0));

		style.addVisualMappingFunction(nodeLabelPosMapping);

		final DiscreteMapping<String, Paint> edgeColorMapping = (DiscreteMapping<String, Paint>) discreteFactory
				.createVisualMappingFunction("interaction", String.class,
						BasicVisualLexicon.EDGE_UNSELECTED_PAINT);
		edgeColorMapping.putMapValue("pp", Color.green);
		edgeColorMapping.putMapValue("pd", Color.red);

		style.addVisualMappingFunction(edgeColorMapping);

		final DiscreteMapping<String, Integer> edgeTransparencyMapping = (DiscreteMapping<String, Integer>) discreteFactory
				.createVisualMappingFunction("interaction", String.class,
						BasicVisualLexicon.EDGE_TRANSPARENCY);
		edgeTransparencyMapping.putMapValue("pp", 222);
		edgeTransparencyMapping.putMapValue("pd", 123);

		style.addVisualMappingFunction(edgeTransparencyMapping);
	}


	/**
	 * Create a simple network for testing.
	 * 
	 * @return sample network
	 */
	private final CyNetwork createNetwork(String networkName) {
		final CyNetwork network = nvts.getNetwork();
		network.getRow(network).set(CyNetwork.NAME, networkName);
		CyNode n1 = network.addNode();
		CyNode n2 = network.addNode();
		CyNode n3 = network.addNode();
		CyNode n4 = network.addNode();

		network.getRow(n1).set(CyNetwork.NAME, "n1");
		network.getRow(n2).set(CyNetwork.NAME, "n2");
		network.getRow(n3).set(CyNetwork.NAME, "n3");
		network.getRow(n4).set(CyNetwork.NAME, "n4");

		// For local table tests
		final CyTable localNodeTable = network.getTable(
				CyNode.class, CyNetwork.LOCAL_ATTRS);

		localNodeTable.createColumn("local1", Double.class, false);
		localNodeTable.getRow(n1.getSUID()).set("local1", 1.0);
		localNodeTable.getRow(n2.getSUID()).set("local1", 2.0);
		localNodeTable.getRow(n3.getSUID()).set("local1", 3.0);
		localNodeTable.getRow(n4.getSUID()).set("local1", 4.0);

		final CyEdge e1 = network.addEdge(n1, n2, true);
		final CyEdge e2 = network.addEdge(n2, n3, true);
		final CyEdge e3 = network.addEdge(n3, n1, true);

		network.getRow(e1).set(CyEdge.INTERACTION, "pp");
		network.getRow(e2).set(CyEdge.INTERACTION, "pp");
		network.getRow(e3).set(CyEdge.INTERACTION, "pd");

		return network;
	}

	@Override
	protected TestContainerFactory getTestContainerFactory()
			throws TestContainerException {
		return new TestContainerFactory() {
			@Override
			public TestContainer create(final URI baseUri,
					DeploymentContext arg1) throws IllegalArgumentException {
				return new TestContainer() {
					private HttpServer server;

					@Override
					public ClientConfig getClientConfig() {
						return null;
					}

					@Override
					public URI getBaseUri() {
						return baseUri;
					}

					@Override
					public void start() {
						try {	
							final Set<Class<?>> resourceClasses = new HashSet<Class<?>>();
							resourceClasses.add(RootResource.class);
							resourceClasses.add(NetworkResource.class);
							resourceClasses.add(NetworkFullResource.class);
							resourceClasses.add(NetworkViewResource.class);
							resourceClasses.add(TableResource.class); 
							resourceClasses.add(MiscResource.class);
							resourceClasses.add(AlgorithmicResource.class);
							resourceClasses.add(StyleResource.class);
							resourceClasses.add(GroupResource.class);
							resourceClasses.add(GlobalTableResource.class);
							resourceClasses.add(SessionResource.class);
							resourceClasses.add(NetworkNameResource.class);
							resourceClasses.add(UIResource.class);
							resourceClasses.add(CollectionResource.class);
							resourceClasses.add(CommandResource.class);
							
							resourceClasses.add(SwaggerUIResource.class);
							
							resourceClasses.add(CyRESTCommandSwagger.class);
							final ResourceConfig rc = new ResourceConfig();

							Injector injector = Guice.createInjector(binder);

							for (Class<?> clazz : resourceClasses){
								Object instance = injector.getInstance(clazz);
								rc.register(instance);
							}
							cyRESTSwagger = injector.getInstance(CyRESTSwagger.class);
							rc.register(cyRESTSwagger);
							// Note: This should match the POJO/JSON serializer we use for the OSGi JAX RS Connector
							//rc.register(JacksonFeature.class); //Old feature
							rc.register(GsonProvider.class);

							this.server = GrizzlyHttpServerFactory
									.createHttpServer(baseUri, rc);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					@Override
					public void stop() {
						this.server.stop();
					}
				};

			}
		};
	}
}
