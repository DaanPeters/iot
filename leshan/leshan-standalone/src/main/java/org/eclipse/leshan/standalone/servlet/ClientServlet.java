/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.standalone.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mSingleResource;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.request.WriteRequest.Mode;
import org.eclipse.leshan.core.request.exception.RequestFailedException;
import org.eclipse.leshan.core.request.exception.ResourceAccessException;
import org.eclipse.leshan.core.response.CreateResponse;
import org.eclipse.leshan.core.response.DeleteResponse;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.server.LwM2mServer;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.standalone.servlet.json.ClientSerializer;
import org.eclipse.leshan.standalone.servlet.json.LwM2mNodeDeserializer;
import org.eclipse.leshan.standalone.servlet.json.LwM2mNodeSerializer;
import org.eclipse.leshan.standalone.servlet.json.ResponseSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

/**
 * Service HTTP REST API calls.
 */
public class ClientServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ClientServlet.class);

    private static final long TIMEOUT = 5000; // ms

    private static final long serialVersionUID = 1L;

    private final LwM2mServer server;

    private final Gson gson;

    public ClientServlet(LwM2mServer server, int securePort) {
        this.server = server;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(Client.class, new ClientSerializer(securePort));
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mResponse.class, new ResponseSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeSerializer());
        gsonBuilder.registerTypeHierarchyAdapter(LwM2mNode.class, new LwM2mNodeDeserializer());
        gsonBuilder.setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        this.gson = gsonBuilder.create();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	resp.addHeader("Access-Control-Allow-Origin", "*");
        // all registered clients
        if (req.getPathInfo() == null) {
            Collection<Client> clients = server.getClientRegistry().allClients();

            String json = this.gson.toJson(clients.toArray(new Client[] {}));
            resp.setContentType("application/json");
            resp.getOutputStream().write(json.getBytes("UTF-8"));
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        String[] path = StringUtils.split(req.getPathInfo(), '/');
        if (path.length < 1) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid path");
            return;
        }
        String clientEndpoint = path[0];

        // /endPoint : get client
        if (path.length == 1) {
        	if(clientEndpoint.equals("FreeParkingSpots")){
        		Collection<Client> clients = server.getClientRegistry().allClients();
        		JsonObject json = new JsonObject();
        		ArrayList<String> freeclients = new ArrayList();
        		for(Client c : clients){
        			
        			ReadResponse response = server.send(c, new ReadRequest("32700/0/32801"));
        			if(((LwM2mSingleResource)response.getContent()).getValue().equals("free")){
        				freeclients.add(c.getEndpoint());
        			}
        			System.out.println(c.getEndpoint()+" " +((LwM2mSingleResource)response.getContent()).getValue());
        		}
        		json.addProperty("NumFreeSpots", freeclients.size()+"");
        		JsonArray ids = new JsonArray();
        		for(String s: freeclients){
        			ids.add(new JsonPrimitive(s));
        		}
        		
        		json.add("IDs", ids);
        		Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
                
        		resp.setContentType("application/json");
        		resp.getOutputStream().write(gson.toJson(json).getBytes());
        		return;
        	}
            Client client = server.getClientRegistry().get(clientEndpoint);
            if (client != null) {
                resp.setContentType("application/json");
                resp.getOutputStream().write(this.gson.toJson(client).getBytes("UTF-8"));
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().format("no registered client with id '%s'", clientEndpoint).flush();
            }
            return;
        }

        // /clients/endPoint/LWRequest : do LightWeight M2M read request on a given client.
        try {
            String target = StringUtils.removeStart(req.getPathInfo(), "/" + clientEndpoint);
            Client client = server.getClientRegistry().get(clientEndpoint);
            if (client != null) {
                ReadRequest request = new ReadRequest(target);
                ReadResponse cResponse = server.send(client, request, TIMEOUT);
                processDeviceResponse(req, resp, cResponse);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().format("No registered client with id '%s'", clientEndpoint).flush();
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid request", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().append(e.getMessage()).flush();
        } catch (ResourceAccessException | RequestFailedException e) {
            LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().append(e.getMessage()).flush();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] path = StringUtils.split(req.getPathInfo(), '/');
        String clientEndpoint = path[0];

        // at least /endpoint/objectId/instanceId
        if (path.length < 3) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid path");
            return;
        }

        try {
            String target = StringUtils.removeStart(req.getPathInfo(), "/" + clientEndpoint);
            Client client = server.getClientRegistry().get(clientEndpoint);
            if (client != null) {
                WriteResponse cResponse = this.writeRequest(client, target, req, resp);
                processDeviceResponse(req, resp, cResponse);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().format("No registered client with id '%s'", clientEndpoint).flush();
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid request", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().append(e.getMessage()).flush();
        } catch (ResourceAccessException | RequestFailedException e) {
            LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().append(e.getMessage()).flush();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] path = StringUtils.split(req.getPathInfo(), '/');
        String clientEndpoint = path[0];
        if(req.getPathInfo().equals("/ReserveSpot")){
        	Client spot = server.getClientRegistry().get(req.getParameter("SpotID"));
        	String driver = req.getParameter("DriverID");
        	if(spot != null){
        		ReadResponse response = server.send(spot, new ReadRequest("32700/0/32801"));
            	if(((LwM2mSingleResource)response.getContent()).getValue().equals("free")){
            		WriteResponse serverresponse = server.send(spot, new WriteRequest(Mode.REPLACE, 32700, 0, 32801, "reserved"));
            		serverresponse = server.send(spot, new WriteRequest(Mode.REPLACE, 32700, 0, 32802, driver ));
            		serverresponse = server.send(spot, new WriteRequest(Mode.REPLACE, 3341, 0, 5527, "orange"));
    				System.out.println(serverresponse.getErrorMessage());
    			}
        	}
        	
        	
        }
        // /clients/endPoint/LWRequest/observe : do LightWeight M2M observe request on a given client.
        if (path.length >= 4 && "observe".equals(path[path.length - 1])) {
            try {
                String target = StringUtils.substringBetween(req.getPathInfo(), clientEndpoint, "/observe");
                Client client = server.getClientRegistry().get(clientEndpoint);
                if (client != null) {
                    ObserveRequest request = new ObserveRequest(target);
                    ObserveResponse cResponse = server.send(client, request, TIMEOUT);
                    processDeviceResponse(req, resp, cResponse);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().format("no registered client with id '%s'", clientEndpoint).flush();
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid request", e);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().append(e.getMessage()).flush();
            } catch (ResourceAccessException | RequestFailedException e) {
                LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().append(e.getMessage()).flush();
            }
            return;
        }

        String target = StringUtils.removeStart(req.getPathInfo(), "/" + clientEndpoint);

        // /clients/endPoint/LWRequest : do LightWeight M2M execute request on a given client.
        if (path.length == 4) {
            try {
                Client client = server.getClientRegistry().get(clientEndpoint);
                if (client != null) {
                    ExecuteRequest request = new ExecuteRequest(target, IOUtils.toString(req.getInputStream()));
                    ExecuteResponse cResponse = server.send(client, request, TIMEOUT);
                    processDeviceResponse(req, resp, cResponse);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().format("no registered client with id '%s'", clientEndpoint).flush();
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid request", e);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().append(e.getMessage()).flush();
            } catch (ResourceAccessException | RequestFailedException e) {
                LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().append(e.getMessage()).flush();
            }
            return;
        }

        // /clients/endPoint/LWRequest : do LightWeight M2M create request on a given client.
        if (2 <= path.length && path.length <= 3) {
            try {
                Client client = server.getClientRegistry().get(clientEndpoint);
                if (client != null) {
                    CreateResponse cResponse = this.createRequest(client, target, req, resp);
                    processDeviceResponse(req, resp, cResponse);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().format("no registered client with id '%s'", clientEndpoint).flush();
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid request", e);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().append(e.getMessage()).flush();
            } catch (ResourceAccessException | RequestFailedException e) {
                LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().append(e.getMessage()).flush();
            }
            return;
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] path = StringUtils.split(req.getPathInfo(), '/');
        String clientEndpoint = path[0];

        // /clients/endPoint/LWRequest/observe : cancel observation for the given resource.
        if (path.length >= 4 && "observe".equals(path[path.length - 1])) {
            try {
                String target = StringUtils.substringsBetween(req.getPathInfo(), clientEndpoint, "/observe")[0];
                Client client = server.getClientRegistry().get(clientEndpoint);
                if (client != null) {
                    server.getObservationRegistry().cancelObservation(client, target);
                    resp.setStatus(HttpServletResponse.SC_OK);
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().format("no registered client with id '%s'", clientEndpoint).flush();
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid request", e);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().append(e.getMessage()).flush();
            } catch (ResourceAccessException | RequestFailedException e) {
                LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().append(e.getMessage()).flush();
            }
            return;
        }

        // /clients/endPoint/LWRequest/ : delete instance
        try {
            String target = StringUtils.removeStart(req.getPathInfo(), "/" + clientEndpoint);
            Client client = server.getClientRegistry().get(clientEndpoint);
            if (client != null) {
                DeleteRequest request = new DeleteRequest(target);
                DeleteResponse cResponse = server.send(client, request, TIMEOUT);
                processDeviceResponse(req, resp, cResponse);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().format("no registered client with id '%s'", clientEndpoint).flush();
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid request", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().append(e.getMessage()).flush();
        } catch (ResourceAccessException | RequestFailedException e) {
            LOG.warn(String.format("Error accessing resource %s%s.", req.getServletPath(), req.getPathInfo()), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().append(e.getMessage()).flush();
        }
    }

    private void processDeviceResponse(HttpServletRequest req, HttpServletResponse resp, LwM2mResponse cResponse)
            throws IOException {
        String response = null;
        if (cResponse == null) {
            LOG.warn(String.format("Request %s%s timed out.", req.getServletPath(), req.getPathInfo()));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().append("Request timeout").flush();
        } else {
            response = this.gson.toJson(cResponse);
            resp.setContentType("application/json");
            resp.getOutputStream().write(response.getBytes());
            resp.setStatus(HttpServletResponse.SC_OK);
        }

    }

    // TODO refactor the code to remove this method.
    private WriteResponse writeRequest(Client client, String target, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        Map<String, String> parameters = new HashMap<String, String>();
        String contentType = HttpFields.valueParameters(req.getContentType(), parameters);

        if ("text/plain".equals(contentType)) {
            String content = IOUtils.toString(req.getInputStream(), parameters.get("charset"));
            int rscId = Integer.valueOf(target.substring(target.lastIndexOf("/") + 1));
            WriteRequest writeRequest = new WriteRequest(Mode.REPLACE, ContentFormat.TEXT, target,
                    LwM2mSingleResource.newStringResource(rscId, content));
            return server.send(client, writeRequest, TIMEOUT);

        } else if ("application/json".equals(contentType)) {
            String content = IOUtils.toString(req.getInputStream(), parameters.get("charset"));
            LwM2mNode node = null;
            try {
                node = gson.fromJson(content, LwM2mNode.class);
            } catch (JsonSyntaxException e) {
                throw new IllegalArgumentException("unable to parse json to tlv:" + e.getMessage(), e);
            }
            return server.send(client, new WriteRequest(Mode.REPLACE, null, target, node), TIMEOUT);

        } else {
            throw new IllegalArgumentException("content type " + req.getContentType()
                    + " not supported for write requests");
        }
    }

    // TODO refactor the code to remove this method.
    private CreateResponse createRequest(Client client, String target, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        Map<String, String> parameters = new HashMap<String, String>();
        String contentType = HttpFields.valueParameters(req.getContentType(), parameters);
        if ("application/json".equals(contentType)) {
            String content = IOUtils.toString(req.getInputStream(), parameters.get("charset"));
            LwM2mNode node;
            try {
                node = gson.fromJson(content, LwM2mNode.class);
            } catch (JsonSyntaxException e) {
                throw new IllegalArgumentException("unable to parse json to tlv:" + e.getMessage(), e);
            }
            if (!(node instanceof LwM2mObjectInstance)) {
                throw new IllegalArgumentException("payload must contain an object instance");
            }
            return server.send(client, new CreateRequest(target, ((LwM2mObjectInstance) node).getResources().values()),
                    TIMEOUT);
        } else {
            throw new IllegalArgumentException("content type " + req.getContentType()
                    + " not supported for write requests");
        }
    }

}
