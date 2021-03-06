/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.sp.jobmanager.core.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.analytics.permissions.PermissionProvider;
import org.wso2.carbon.analytics.permissions.bean.Permission;
import org.wso2.carbon.cluster.coordinator.commons.node.NodeDetail;
import org.wso2.carbon.cluster.coordinator.service.ClusterCoordinator;
import org.wso2.carbon.sp.jobmanager.core.api.ApiResponseMessage;
import org.wso2.carbon.sp.jobmanager.core.api.ManagersApiService;
import org.wso2.carbon.sp.jobmanager.core.api.NotFoundException;
import org.wso2.carbon.sp.jobmanager.core.bean.ManagerConfigurationDetails;
import org.wso2.carbon.sp.jobmanager.core.dbhandler.StatusDashboardManagerDBHandler;
import org.wso2.carbon.sp.jobmanager.core.exception.RDBMSTableException;
import org.wso2.carbon.sp.jobmanager.core.impl.utils.Constants;
import org.wso2.carbon.sp.jobmanager.core.internal.ManagerDataHolder;
import org.wso2.carbon.sp.jobmanager.core.internal.ServiceDataHolder;
import org.wso2.carbon.sp.jobmanager.core.internal.services.DatasourceServiceComponent;
import org.wso2.carbon.sp.jobmanager.core.model.Manager;
import org.wso2.carbon.sp.jobmanager.core.model.ManagerDetails;
import org.wso2.carbon.sp.jobmanager.core.model.SiddhiAppDetails;
import org.wso2.carbon.sp.jobmanager.core.model.SiddhiAppHolder;
import org.wso2.carbon.sp.jobmanager.core.util.ResourceManagerConstants;
import org.wso2.msf4j.Request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * Distributed Siddhi Service Implementataion Class
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaMSF4JServerCodegen",
                            date = "2018-01-29T08:19:07.148Z")
@Component(service = ManagersApiService.class, immediate = true)

public class ManagersApiServiceImpl extends ManagersApiService {
    private static final Log logger = LogFactory.getLog(ManagersApiServiceImpl.class);
    private static final String MANAGE_SIDDHI_APP_PERMISSION_STRING = "siddhiApp.manage";
    private static final String VIEW_SIDDHI_APP_PERMISSION_STRING = "siddhiApp.view";
    private static StatusDashboardManagerDBHandler managerDashboard;

    /**
     * This is the activation method of ConfigServiceComponent. This will be called when it's references are fulfilled
     *
     * @throws Exception this will be thrown if an issue occurs while executing the activate method
     */
    @Activate
    protected void start() {
        if (logger.isDebugEnabled()) {
            logger.debug("@Reference(bind) Status Dashboard ManagerApiServiceImpl API");
        }
        managerDashboard = new StatusDashboardManagerDBHandler();
    }

    /**
     * This is the deactivation method of ConfigServiceComponent. This will be called when this component
     * is being stopped or references are satisfied during runtime.
     *
     * @throws Exception this will be thrown if an issue occurs while executing the de-activate method
     */
    @Deactivate
    protected void stop() {

        if (logger.isDebugEnabled()) {
            logger.debug("@Reference(unbind) Status Dashboard ManagerApiServiceImpl API");
        }
    }

    private static String getUserName(Request request) {
        Object username = request.getProperty("username");
        return username != null ? username.toString() : null;
    }

    /**
     * This method is to add user specified manager's host and the port.
     *
     * @param manager
     * @return
     */

    public Response addManager(Manager manager) {
        if (manager.getHost() != null) {
            String managerId = manager.getHost() + Constants.MANAGER_KEY_GENERATOR + String.valueOf(manager.getPort());
            ManagerConfigurationDetails managerConfigurationDetails =
                    new ManagerConfigurationDetails(managerId, manager.getHost(), Integer.valueOf(manager.getPort()));
            try {
                managerDashboard.insertManagerConfiguration(managerConfigurationDetails);
                return Response.ok().entity(
                        new ApiResponseMessage(ApiResponseMessage.OK, "managerId " + "\n" + managerId + "\n" +
                                "successfully " + " added")).build();
            } catch (RDBMSTableException e) {
                logger.error("Error occured while inserting the Manager due to " + e.getMessage(), e);
                return Response.serverError().entity(new ApiResponseMessage(ApiResponseMessage.ERROR, "Error "
                        + "occured while inserting the Manager due to " + e.getMessage())).build();
            }
        } else {
            logger.error("There is no manager node specified:" + manager.toString());
            return Response.status(Response.Status.BAD_REQUEST).entity("There is no manager nodes. Please add a "
                                                                               + "manager: " + manager.toString())
                    .build();
        }
    }

    /**
     * This method is to delete requested manager node
     *
     * @param managerId
     * @return
     */
    public Response deleteManager(String managerId) {
        try {
            managerDashboard.deleteManagerConfiguration(managerId);
            return Response.ok()
                    .entity(new ApiResponseMessage(ApiResponseMessage.OK, managerId + " Successfully deleted"))
                    .build();
        } catch (RDBMSTableException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
                    new ApiResponseMessage(ApiResponseMessage.ERROR, "Error occured while deleting the "
                            + "manager" + "\n" + managerId + "\n" + ex.getMessage())).build();
        }
    }

    /***
     * This method is to list down all the manager's details that are belongs to the same cluster
     * @return :Manager's host and the port
     */

    public Response getManagerDetails() {
        List<ManagerDetails> connectedManagers = new ArrayList<>();
        ClusterCoordinator clusterCoordinator = ServiceDataHolder.getCoordinator();
        if (clusterCoordinator != null) {
            for (NodeDetail nodeDetail : clusterCoordinator.getAllNodeDetails()) {
                if (nodeDetail.getPropertiesMap() != null) {
                    Map<String, Object> propertiesMap = nodeDetail.getPropertiesMap();
                    String httpInterfaceHost = (String) propertiesMap.get(ResourceManagerConstants.KEY_NODE_HOST);
                    int httpInterfacePort = (int) propertiesMap.get(ResourceManagerConstants.KEY_NODE_PORT);
                    ManagerDetails managerDetails = new ManagerDetails();
                    managerDetails.setHost(httpInterfaceHost);
                    managerDetails.setPort(httpInterfacePort);
                    if (clusterCoordinator.getLeaderNode().getNodeId().equals(nodeDetail.getNodeId())) {
                        managerDetails.setHaStatus("Active");
                    } else {
                        managerDetails.setHaStatus("Pasive");
                    }
                    connectedManagers.add(managerDetails);
                }
            }
            return Response.ok().entity(connectedManagers).build();
        } else {
            return Response.status(Response.Status.NO_CONTENT).entity(
                    new ApiResponseMessage(ApiResponseMessage.ERROR, "There is no manager nodes found in the "
                            + "cluster")).build();
        }
    }

    /**
     * Returns all the deployed siddhi application details
     *
     * @return
     */

    public Response getSiddhiAppDetails() {
        Map<String, List<SiddhiAppHolder>> deployedSiddhiAppHolder = ServiceDataHolder.getResourcePool()
                .getSiddhiAppHoldersMap();
        Map<String, List<SiddhiAppHolder>> waitingToDeploy = ServiceDataHolder.getResourcePool()
                .getAppsWaitingForDeploy();

        if (deployedSiddhiAppHolder.isEmpty() && waitingToDeploy.isEmpty()) {
            logger.info("There is no siddhi apps");
            return Response.status(Response.Status.NO_CONTENT).entity(
                    new ApiResponseMessage(ApiResponseMessage.ERROR, "There is no siddhi app  in the manager "
                            + "node")).build();
        } else {
            Map<String, List<SiddhiAppHolder>> siddhapps = new HashMap<>();
            siddhapps.putAll(deployedSiddhiAppHolder);
            siddhapps.putAll(waitingToDeploy);
            List<SiddhiAppDetails> appList = new ArrayList<>();
            for (Map.Entry<String, List<SiddhiAppHolder>> en : siddhapps.entrySet()) {
                for (SiddhiAppHolder obj : en.getValue()) {
                    SiddhiAppDetails appHolder = new SiddhiAppDetails();
                    appHolder.setParentAppName(obj.getParentAppName());
                    appHolder.setAppName(obj.getAppName());
                    appHolder.setGroupName(obj.getGroupName());
                    appHolder.setSiddhiApp(obj.getSiddhiApp());
                    if (obj.getDeployedNode() != null) {
                        appHolder.setId(obj.getDeployedNode().getId());
                        appHolder.setState(obj.getDeployedNode().getState());
                        appHolder.setHost(obj.getDeployedNode().getHttpInterface().getHost());
                        appHolder.setPort(Integer.toString(obj.getDeployedNode().getHttpInterface().getPort()));
                        appHolder
                                .setFailedPingAttempts(Integer.toString(obj.getDeployedNode().getFailedPingAttempts()));
                        appHolder.setLastPingTimestamp(Long.toString(obj.getDeployedNode().getLastPingTimestamp()));
                    }
                    appList.add(appHolder);
                }
            }
            return Response.ok().entity(appList).build();
        }

    }

    /**
     * This method is to display the text view of the siddhi application
     * @param appName
     * @return
     */

    public Response getSiddhiAppExecutionPlan(String appName) {
        Map<String, List<SiddhiAppHolder>> deployedSiddhiAppHolder = ServiceDataHolder.getResourcePool()
                .getSiddhiAppHoldersMap();
        Map<String, List<SiddhiAppHolder>> waitingToDeploy = ServiceDataHolder.getResourcePool()
                .getAppsWaitingForDeploy();
        Map<String, List<SiddhiAppHolder>> siddhapps = new HashMap<>();
        siddhapps.putAll(deployedSiddhiAppHolder);
        siddhapps.putAll(waitingToDeploy);
        if (siddhapps.containsKey(appName)) {
            String definedApp = ServiceDataHolder.getUserDefinedSiddhiApp(appName);
            return Response.ok().entity(definedApp).build();
        } else {
            return Response.status(Response.Status.NO_CONTENT).entity(
                    new ApiResponseMessage(ApiResponseMessage.ERROR, "There is no such siddhi application deployed "
                            + "in the manager node")).build();
        }
    }

    /**
     * This method is to list down the child app details of the given parent siddhi application
     *
     * @param appName
     * @return
     * @throws NotFoundException
     */

    public Response getChildSiddhiApps(String appName) throws NotFoundException {
        Map<String, List<SiddhiAppHolder>> deployedSiddhiAppHolder = ServiceDataHolder.getResourcePool()
                .getSiddhiAppHoldersMap();
        Map<String, List<SiddhiAppHolder>> waitingToDeploy = ServiceDataHolder.getResourcePool()
                .getAppsWaitingForDeploy();
        if (waitingToDeploy.containsKey(appName)) {
            List<SiddhiAppHolder> holder = waitingToDeploy.get(appName);
            List<SiddhiAppDetails> appList = new ArrayList<>();
            holder.forEach((siddhiAppHolder -> {
                SiddhiAppDetails appHolder = new SiddhiAppDetails();
                appHolder.setParentAppName(siddhiAppHolder.getParentAppName());
                appHolder.setAppName(siddhiAppHolder.getAppName());
                appHolder.setGroupName(siddhiAppHolder.getGroupName());
                appHolder.setSiddhiApp(siddhiAppHolder.getSiddhiApp());
                appHolder.setAppStatus("waiting");
                appList.add(appHolder);
            }));
            return Response.ok().entity(appList).build();
        } else {
            List<SiddhiAppHolder> activeHolder = deployedSiddhiAppHolder.get(appName);
            List<SiddhiAppDetails> appList = new ArrayList<>();
            activeHolder.forEach((siddhiAppHolder -> {
                SiddhiAppDetails appHolder = new SiddhiAppDetails();
                appHolder.setParentAppName(siddhiAppHolder.getParentAppName());
                appHolder.setAppName(siddhiAppHolder.getAppName());
                appHolder.setGroupName(siddhiAppHolder.getGroupName());
                appHolder.setSiddhiApp(siddhiAppHolder.getSiddhiApp());
                appHolder.setId(siddhiAppHolder.getDeployedNode().getId());
                appHolder.setState(siddhiAppHolder.getDeployedNode().getState());
                appHolder.setHost(siddhiAppHolder.getDeployedNode().getHttpInterface().getHost());
                appHolder.setPort(Integer.toString(siddhiAppHolder.getDeployedNode().getHttpInterface().getPort()));
                appHolder.setFailedPingAttempts(
                        Integer.toString(siddhiAppHolder.getDeployedNode().getFailedPingAttempts()));
                appHolder.setLastPingTimestamp(Long.toString(siddhiAppHolder.getDeployedNode().getLastPingTimestamp()));
                appHolder.setAppStatus("Active");
                appList.add(appHolder);
            }));
            return Response.ok().entity(appList).build();
        }
    }


    /**
     * Add new manager nodes : User can add one or manager nodes
     * @param manager  : Manager object that need to be added
     * @param username : username of the user
     * @return : Response whether the manager is successfully added or not.
     * @throws NotFoundException
     */

    @Override
    public Response addManager(Manager manager, Request request) throws NotFoundException {
        if (getUserName(request) != null && !getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, MANAGE_SIDDHI_APP_PERMISSION_STRING))) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient permissions to add Manager Node")
                    .build();
        } else {
            return addManager(manager);
        }
    }

    /**
     * Delete an existing manager.
     *
     * @param managerId : id of the manager node (format : managerhost_managerport)
     * @param username  :username of the user
     * @return Response whether the manager successfully deleted or not.
     * @throws NotFoundException
     */

    @Override
    public Response deleteManager(String managerId, Request request) throws NotFoundException {
        if (getUserName(request) != null && !getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, MANAGE_SIDDHI_APP_PERMISSION_STRING))) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient permissions to delete manager "
                                                                             + "node").build();
        } else {
            return deleteManager(managerId);
        }
    }

    /**
     * This method returns all the manager's details in the cluster (manager's host, manager's port, HA
     * details)
     *
     * @return manager object with relevant details
     * @throws NotFoundException
     */

    @Override
    public Response getAllManagers(Request request) throws NotFoundException {
        if (getUserName(request) != null && !(getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, VIEW_SIDDHI_APP_PERMISSION_STRING)) || getPermissionProvider()
                .hasPermission(getUserName(request), new Permission(Constants.PERMISSION_APP_NAME,
                                                                    MANAGE_SIDDHI_APP_PERMISSION_STRING)))) {
            return Response.status(Response.Status.FORBIDDEN).entity(
                    "Insufficient permissions to get the manager's details").build();
        } else {
            return getManagerDetails();
        }

    }


    /**
     * Returns all the deployed siddhi application in the given manager node
     *
     * @return
     * @throws NotFoundException
     */

    @Override
    public Response getSiddhiApps(Request request) throws NotFoundException {
        if (getUserName(request) != null && !(getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, VIEW_SIDDHI_APP_PERMISSION_STRING)) || getPermissionProvider()
                .hasPermission(getUserName(request), new Permission(Constants.PERMISSION_APP_NAME,
                                                                    MANAGE_SIDDHI_APP_PERMISSION_STRING)))) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient permissions to get the "
                                                                             + "details of the Siddhi Apps").build();
        } else {
            return getSiddhiAppDetails();
        }

    }


    /**
     * This method returns the text view of the given siddhi application
     *
     * @param appName
     * @return
     */

    @Override
    public Response getSiddhiAppExecution(String appName, Request request) {
        if (getUserName(request) != null && !(getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, VIEW_SIDDHI_APP_PERMISSION_STRING)) || getPermissionProvider()
                .hasPermission(getUserName(request), new Permission(Constants.PERMISSION_APP_NAME,
                                                                    MANAGE_SIDDHI_APP_PERMISSION_STRING)))) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient permissions to get the "
                                                                             + "execution plan").build();
        } else {
            return getSiddhiAppExecutionPlan(appName);
        }
    }

    /**
     * We can get all the details of given parent siddhi application
     * If it is in the waiting mode we can get all the details except deployed worker node details
     *
     * @param appName
     * @return
     * @throws NotFoundException
     */

    @Override
    public Response getChildSiddhiAppDetails(String appName,
                                             Request request) throws NotFoundException {
        if (getUserName(request) != null && !(getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, VIEW_SIDDHI_APP_PERMISSION_STRING)) || getPermissionProvider()
                .hasPermission(getUserName(request), new Permission(Constants.PERMISSION_APP_NAME,
                                                                    MANAGE_SIDDHI_APP_PERMISSION_STRING)))) {
            return Response.status(Response.Status.FORBIDDEN).entity("Insufficient permissions to get child app "
                                                                             + "details").build();
        } else {
            return getChildSiddhiApps(appName);
        }
    }

    @Override
    public Response getRolesByUsername(Request request, String permissionSuffix) {
        boolean isAuthorized = getPermissionProvider().hasPermission(getUserName(request), new
                Permission(Constants.PERMISSION_APP_NAME, Constants.PERMISSION_APP_NAME + "." + permissionSuffix));

        if (getUserName(request) != null && isAuthorized) {
            return Response.ok()
                    .entity(isAuthorized)
                    .build();
        } else {
            return Response.ok()
                    .entity(isAuthorized)
                    .build();
        }
    }

    private PermissionProvider getPermissionProvider() {
        return ManagerDataHolder.getInstance().getPermissionProvider();
    }

    @Reference(
            name = "org.wso2.carbon.sp.jobmanager.core.internal.services.DatasourceServiceComponent",
            service = DatasourceServiceComponent.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unregisterServiceDatasource"
    )
    public void regiterServiceDatasource(DatasourceServiceComponent datasourceServiceComponent) {
        if (logger.isDebugEnabled()) {
            logger.debug("@Reference(bind) DatasourceServiceComponent");
        }
    }

    public void unregisterServiceDatasource(DatasourceServiceComponent datasourceServiceComponent) {
        if (logger.isDebugEnabled()) {
            logger.debug("@Reference(unbind) DatasourceServiceComponent");
        }
    }
}
