package org.jumpmind.metl.ui.views.deploy;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.metl.core.model.AgentDeployment;
import org.jumpmind.metl.core.model.AgentDeploymentSummary;
import org.jumpmind.metl.core.model.AgentFlowDeploymentParameter;
import org.jumpmind.metl.core.model.AgentResourceSetting;
import org.jumpmind.metl.core.model.Flow;
import org.jumpmind.metl.core.model.FlowName;
import org.jumpmind.metl.core.model.FlowParameter;
import org.jumpmind.metl.core.model.ReleasePackage;
import org.jumpmind.metl.core.model.ReleasePackageProjectVersion;
import org.jumpmind.metl.core.model.ResourceName;
import org.jumpmind.metl.core.persist.IConfigurationService;
import org.jumpmind.metl.core.persist.IOperationsService;
import org.jumpmind.metl.ui.common.ApplicationContext;
import org.jumpmind.metl.ui.views.deploy.ValidateReleasePackageDeploymentPanel.DeploymentLine;
import org.jumpmind.vaadin.ui.common.ConfirmDialog;
import org.jumpmind.vaadin.ui.common.ResizableWindow;

import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.server.Page;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

public class DeployDialog extends ResizableWindow {

    private static final String DEPLOY_BY_FLOW = "By Flow";

    private static final String DEPLOY_BY_PACKAGE = "By Package";

    private static final long serialVersionUID = 1L;

    ApplicationContext context;
    
    IConfigurationService configurationService;
    
    IOperationsService operationsService;

    EditAgentPanel parentPanel;

    VerticalLayout selectDeploymentLayout;

    OptionGroup deployByOptionGroup;

    Button actionButton;

    Button backButton;

    SelectFlowsPanel selectFlowsPanel;
    
    SelectPackagePanel selectPackagePanel;
    
    ValidateReleasePackageDeploymentPanel validateReleasePackageDeploymentPanel;

    public DeployDialog(ApplicationContext context, EditAgentPanel parentPanel) {
        super("Deploy");
        this.context = context;
        this.configurationService = context.getConfigurationService();
        this.operationsService = context.getOperationsSerivce();
        this.parentPanel = parentPanel;
        this.context = context;

        final float DESIRED_WIDTH = 1000;
        float width = DESIRED_WIDTH;
        float maxWidth = (float) (Page.getCurrent().getBrowserWindowWidth() * .8);
        if (maxWidth < DESIRED_WIDTH) {
            width = maxWidth;
        }
        setWidth(width, Unit.PIXELS);
        setHeight(600.0f, Unit.PIXELS);

        VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        layout.setSizeFull();
        addComponent(layout, 1);

        deployByOptionGroup = new OptionGroup("Deployment Type:");
        deployByOptionGroup.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);
        deployByOptionGroup.addItem(DEPLOY_BY_PACKAGE);
        deployByOptionGroup.addItem(DEPLOY_BY_FLOW);
        layout.addComponent(deployByOptionGroup);

        selectDeploymentLayout = new VerticalLayout();
        selectDeploymentLayout.setSizeFull();
        selectDeploymentLayout.setMargin(new MarginInfo(true, false));
        layout.addComponent(selectDeploymentLayout);
        layout.setExpandRatio(selectDeploymentLayout, 1);

        backButton = new Button("Cancel", e -> back());
        actionButton = new Button("Deploy", e -> takeAction());
        actionButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
        actionButton.setClickShortcut(KeyCode.ENTER);
        addComponent(buildButtonFooter(backButton, actionButton));

        deployByOptionGroup.addValueChangeListener(e -> deployByChanged());
        deployByOptionGroup.setValue(DEPLOY_BY_PACKAGE);

    }

    protected boolean isDeployByFlow() {
        Object deployByChoice = deployByOptionGroup.getValue();
        return deployByOptionGroup.isVisible() && DEPLOY_BY_FLOW.equals(deployByChoice);
    }

    protected boolean isDeployByPackage() {
        Object deployByChoice = deployByOptionGroup.getValue();
        return deployByOptionGroup.isVisible() && DEPLOY_BY_PACKAGE.equals(deployByChoice);
    }

    protected void deployByChanged() {
        selectDeploymentLayout.removeAllComponents();
        Component toAdd = null;
        if (isDeployByFlow()) {
            toAdd = buildDeployByFlow();
        } else {
            toAdd = buildDeployByPackage();
        }
        selectDeploymentLayout.addComponent(toAdd);
    }

    protected Component buildDeployByFlow() {
        if (selectFlowsPanel == null) {
            String introText = "Select one or more flows for deployment to this agent:";
            selectFlowsPanel = new SelectFlowsPanel(context, introText, parentPanel.getAgent().isAllowTestFlows());
        }
        actionButton.setCaption("Deploy");
        return selectFlowsPanel;
    }

    protected Component buildDeployByPackage() {
        if (selectPackagePanel == null) {
            String introText = "Select a package for deployment to this agent:";
            selectPackagePanel = new SelectPackagePanel(context, introText);            
        }
        actionButton.setCaption("Next");
        return selectPackagePanel;        
    }
    
    protected Component buildValidatePackageDeploymentAction() {
        if (validateReleasePackageDeploymentPanel == null) {
            String introText = "Validate deployment actions";
            validateReleasePackageDeploymentPanel = new ValidateReleasePackageDeploymentPanel(
                    context, introText, selectPackagePanel.getSelectedPackages(),
                    parentPanel.getAgent().getId());
        }
        return validateReleasePackageDeploymentPanel;
    }

    protected void takeAction() {
        if (isDeployByFlow()) {
            Collection<FlowName> flows = selectFlowsPanel.getSelectedFlows(parentPanel.getAgent().isAllowTestFlows());
            verfiyDeployFlows(flows);
        } else if (isDeployByPackage()) {
            deployByOptionGroup.setVisible(false);
            backButton.setCaption("Previous");
            actionButton.setCaption("Deploy");
            selectDeploymentLayout.removeAllComponents();
            selectDeploymentLayout.addComponent(buildValidatePackageDeploymentAction());
        } else {
            deployReleasePackage();
            close();
        }
    }

    protected void deployReleasePackage() {
        BeanItemContainer<DeploymentLine> container = validateReleasePackageDeploymentPanel.getContainer();
        for (int i=0; i<container.size();i++) {
            DeploymentLine line = container.getIdByIndex(i);
            Flow flow = configurationService.findFlow(line.getNewFlowId());
            AgentDeployment existingDeployment = operationsService.findAgentDeployment(line.getExistingDeploymentId());                
            deployFlow(flow, line.newDeployName, line.upgrade, existingDeployment);                
        }   
        deployResourceSettings(selectPackagePanel.getSelectedPackages());
    }
    
    protected void deployResourceSettings(List<ReleasePackage> releasePackages) {        
        for (ReleasePackage releasePackage : releasePackages) {
            releasePackage = configurationService.findReleasePackage(releasePackage.getId());
            processReleasePackageResources(releasePackage);
        }
    }
    
    protected void processReleasePackageResources(ReleasePackage releasePackage) {
        for (ReleasePackageProjectVersion rppv : releasePackage.getProjectVersions()) {
            processProjectVersionResources(rppv);
        }
    }
    
    protected void processProjectVersionResources(ReleasePackageProjectVersion rppv) {
        List<ResourceName> newResources = configurationService.findResourcesInProject(rppv.getProjectVersionId());
        Map<String, List<AgentResourceSetting>> agentResourceSettingsMap = buildAgentResourceSettingsMap(newResources);        
        for (ResourceName newResource : newResources) {
            List<AgentResourceSetting> agentResourceSettings = agentResourceSettingsMap.get(newResource.getRowId());
            for (AgentResourceSetting agentResourceSetting : agentResourceSettings) {
                agentResourceSetting.setResourceId(newResource.getId());
                configurationService.save(agentResourceSetting);
            }
        }   
    }
    
    protected Map<String, List<AgentResourceSetting>> buildAgentResourceSettingsMap(List<ResourceName> newResources) {
        Map<String, List<AgentResourceSetting>> resourceSettingsMap = new HashMap<String, List<AgentResourceSetting>>();
        for (ResourceName newResource : newResources) {
            resourceSettingsMap.put(newResource.getRowId(), 
                    operationsService.findMostRecentDeployedResourceSettings(parentPanel.getAgent().getId(), newResource.getId()));
        }
        return resourceSettingsMap;
    }
    
    protected void back() {
        if (deployByOptionGroup.isVisible()) {
            close();
        } else {
            deployByOptionGroup.setVisible(true);
            backButton.setCaption("Cancel");
            deployByChanged();
        }
    }

    protected void verfiyDeployFlows(Collection<FlowName> flowCollection) {
        //TODO this should go away in lieu of similar thing as validatereleasepackagedeployment panel
        StringBuilder alreadyDeployedFlows = new StringBuilder();
        List<AgentDeploymentSummary> summaries = parentPanel.getAgentDeploymentSummary();
        for (FlowName flowName : flowCollection) {
            for (AgentDeploymentSummary agentDeploymentSummary : summaries) {
                if (flowName.getId().equals(agentDeploymentSummary.getArtifactId())) {
                    if (alreadyDeployedFlows.length() > 0) {
                        alreadyDeployedFlows.append(", ");
                    }
                    alreadyDeployedFlows.append("'").append(flowName.getName()).append("'");
                }
            }
        }

        if (alreadyDeployedFlows.length() > 0) {
            ConfirmDialog.show("Flows already deployed",
                    String.format(
                            "There are flows that have already been deployed.  Press OK to deploy another version. The following flows are already deployed: %s",
                            alreadyDeployedFlows),
                    () -> {
                        deployFlows(flowCollection);
                        return true;
                    });
        } else {
            deployFlows(flowCollection);
        }
    }

    protected void deployFlow(Flow flow, String deployName, boolean upgrade, 
            AgentDeployment existingDeployment) {
        
        AgentDeployment newDeploy = new AgentDeployment();
        newDeploy.setAgentId(parentPanel.getAgent().getId());
        newDeploy.setName(deployName);
        newDeploy.setFlowId(flow.getId());
        List<AgentFlowDeploymentParameter> newDeployParams = newDeploy.getAgentDeploymentParameters();
        //initialize from the flow.  If upgrading replace with agent values
        for (FlowParameter flowParam : flow.getFlowParameters()) {
            AgentFlowDeploymentParameter deployParam = new AgentFlowDeploymentParameter();
            deployParam.setFlowId(flowParam.getFlowId());
            deployParam.setAgentDeploymentId(newDeploy.getId());
            deployParam.setName(flowParam.getName());
            deployParam.setValue(flowParam.getDefaultValue());
            newDeployParams.add(deployParam);
        }            
        if (upgrade) {
            List<AgentFlowDeploymentParameter> existingDeployParams = existingDeployment.getAgentDeploymentParameters();
            Map<String, String> existingDeployParamsMap = new HashMap<String, String>();
            for (AgentFlowDeploymentParameter existingDeployParam : existingDeployParams) {
                existingDeployParamsMap.put(existingDeployParam.getName(), existingDeployParam.getValue());                
            }
            for (AgentFlowDeploymentParameter newDeployParam : newDeployParams) {
                newDeployParam.setValue(existingDeployParamsMap.get(newDeployParam.getName()));
            }
            operationsService.delete(existingDeployment);
        }
        operationsService.save(newDeploy);
    }
    
    protected void deployFlows(Collection<FlowName> flowCollection) {
        for (FlowName flowName : flowCollection) {
            IConfigurationService configurationService = context.getConfigurationService();
            Flow flow = configurationService.findFlow(flowName.getId());
            deployFlow(flow, flow.getName(), false, null);
        }
        parentPanel.refresh();
        close();
    }

    protected String getName(String name) {
        for (Object deployment : parentPanel.getAgentDeploymentSummary()) {
            if (deployment instanceof AgentDeployment) {
                AgentDeployment agentDeployment = (AgentDeployment) deployment;
                if (name.equals(agentDeployment.getName())) {
                    if (name.matches(".*\\([0-9]+\\)$")) {
                        String num = name.substring(name.lastIndexOf("(") + 1, name.lastIndexOf(")"));
                        name = name.replaceAll("\\([0-9]+\\)$", "(" + (Integer.parseInt(num) + 1) + ")");
                    } else {
                        name += " (1)";
                    }
                    return getName(name);
                }
            }
        }
        return name;
    }

}
