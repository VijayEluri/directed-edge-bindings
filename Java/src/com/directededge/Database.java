/*
 * Copyright (C) 2009 Directed Edge, Inc.
 */

package com.directededge;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.restlet.Client;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.StringRepresentation;

public class Database
{
    private String name;
    private String password;
    private String host;
    private String protocol;
    private Client client;

    public class ResourceException extends Exception
    {
        public Method method;
        public String url;

        ResourceException(Method method, String url)
        {
            this.method = method;
            this.url = url;
        }
    }

    public Database(String username, String password)
    {
        protocol = "http";
        name = username;
        this.password = password;

        host = System.getenv("DIRECTEDEDGE_HOST");
        
        if(host == null)
        {
            // host = "webservices.directededge.com";
            host = "localhost";
        }

        client = new Client(Protocol.HTTP);
    }

    public void importFromFile(String fileName)
    {
        Request request = new Request(Method.PUT, url(""),
                new FileRepresentation(fileName, MediaType.TEXT_XML));
        request.setChallengeResponse(
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, name, password));
        client.handle(request);
    }

    public String get(String resource) throws ResourceException
    {
        Request request = new Request(Method.GET, url(resource));
        request.setChallengeResponse(
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, name, password));
        Response response = client.handle(request);

        if(response.getStatus().isSuccess())
        {
            try
            {
                return response.getEntity().getText();
            }
            catch (IOException ex)
            {
                Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }
        else
        {
            throw new ResourceException(Method.GET, url(resource));
        }
    }

    public void put(String resource, String data)
    {
        Request request = new Request(Method.PUT, url(resource),
                new StringRepresentation(data, MediaType.TEXT_XML));
        request.setChallengeResponse(
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, name, password));
        Response response = client.handle(request);

    }

    private String url(String resource)
    {
        try
        {
            URL url = new URL(protocol, host, "/api/v1/" + name + "/" + resource);
            return url.toString();
        }
        catch (MalformedURLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
