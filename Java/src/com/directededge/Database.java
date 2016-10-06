/*
 * Copyright (C) 2009-2016 Directed Edge, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.directededge;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;


/**
 * Represents a database on the Directed Edge servers.  A database is simply
 * a collection of items.  See developer.directededge.com for more information
 * on the concepts in place here and throughout the Directed Edge API.
 */

public class Database
{
    public enum Protocol
    {
        HTTP,
        HTTPS
    }

    public enum Method
    {
        GET,
        PUT,
        POST,
        DELETE
    }

    private String name;
    private String host;
    private Protocol protocol;
    private DefaultHttpClient client;
    private UsernamePasswordCredentials credentials;

    /**
     * This is thrown when a resource cannot be read or written for some reason.
     */
    @SuppressWarnings("serial")
    public class ResourceException extends Exception
    {
        public Method method;
        public String url;

        ResourceException(Method method, String url)
        {
            super("Error doing " + method.toString() + " on " + url);
            this.method = method;
            this.url = url;
        }
    }

    /**
     * Initializes a Directed Edge database.  You should have received a user
     * name and account name from Directed Edge.
     *
     * @param protocol The protocol used in communication - supported protocols
     * are HTTP and HTTPS.
     * @param username The user / database name.
     * @param password Your password.
     */
    public Database(Protocol protocol, String username, String password)
    {
        this.protocol = protocol;
        name = username;
        host = System.getenv("DIRECTEDEDGE_HOST");

        if(host == null)
        {
            host = "webservices.directededge.com";
        }

        client = new DefaultHttpClient();

        if(username != null)
        {
            credentials = new UsernamePasswordCredentials(username, password);
            client.getCredentialsProvider().setCredentials(
                    new AuthScope(host, protocol == Protocol.HTTP ? 80 : 443),
                    credentials);
        }
    }

    /**
     * Initializes a Directed Edge database.  You should have received a user
     * name and account name from Directed Edge.
     *
     * @param username The user / database name.
     * @param password Your password.
     */
    public Database(String username, String password)
    {
        this(Protocol.HTTP, username, password);
    }

    /**
     * Used to import a Directed Edge XML file.  Usually used in conjunction
     * with the Exporter.
     * @param fileName The file path of a Directed Edge XML file.
     * @see Exporter
     */
    public void importFromFile(String fileName) throws ResourceException
    {
        upload(Method.PUT, new ArrayList<String>(), new HashMap<String, Object>(),
                new FileEntity(new File(fileName), "text/xml"));
    }

    /**
     * Grabs the contents of the sub-resources, e.g. "item1/related".  This is
     * primarily for internal usage.
     *
     * @param resources The subresource to fetch.
     * @return The content of the sub resources.
     * @throws ResourceException Throws a ResourceException if the resources
     * cannot be found, or if there is an authentication error.
     * @see ResourceException
     */
    public String get(List<String> resources) throws ResourceException
    {
        return get(resources, new HashMap<String, Object>());
    }

    public String get(List<String> resources, Map<String, Object> options)
            throws ResourceException
    {
        HttpGet request = new HttpGet(url(resources, options));
        addAuthenticationHeader(request);

        try
        {
            HttpResponse response = client.execute(request);
            checkResponseCode(Method.GET, resources, options, response);
            return EntityUtils.toString(response.getEntity());
        }
        catch (IOException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            throw new ResourceException(Method.GET, url(resources, options));
        }
    }

    /**
     * Grabs the contents of the sub-resources, e.g. "item1".  This is
     * primarily for internal usage.
     *
     * @param resources The subresource to write to.
     */
    public void put(List<String> resources, String data) throws ResourceException
    {
        put(resources, data, new HashMap<String, Object>());
    }

    public void put(List<String> resources, String data, Map<String, Object> options)
            throws ResourceException
    {
        try
        {
            upload(Method.PUT, resources, options,
                    new StringEntity(data, "text/xml", "UTF-8"));
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void post(List<String> resources, String data) throws ResourceException
    {
        post(resources, data, new HashMap<String, Object>());
    }

    public void post(List<String> resources, String data, Map<String, Object> options)
            throws ResourceException
    {
        try
        {
            upload(Method.POST, resources, options,
                    new StringEntity(data, "text/xml", "UTF-8"));
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void delete(List<String> resources) throws ResourceException
    {
        HttpDelete request = new HttpDelete(url(resources, null));
        addAuthenticationHeader(request);

        try
        {
            HttpResponse response = client.execute(request);
            checkResponseCode(Method.DELETE, resources, null, response);
            EntityUtils.consume(response.getEntity());
        }
        catch (IOException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            throw new ResourceException(Method.DELETE, url(resources, null));
        }
    }

    /**
     * Sets the connection and socket timeouts.
     * @param milliseconds The number of milliseconds to wait before aborting
     * HTTP operations.
     */

    public void setTimeout(int milliseconds)
    {
        HttpParams params = client.getParams();
        HttpConnectionParams.setConnectionTimeout(params, milliseconds);
        HttpConnectionParams.setSoTimeout(params, milliseconds);
    }

    private void upload(Method method, List<String> resources, Map<String, Object> options, HttpEntity entity) throws ResourceException
    {
        HttpEntityEnclosingRequestBase request;

        if(method == Method.PUT)
        {
            request = new HttpPut(url(resources, options));
        }
        else if(method == Method.POST)
        {
            request = new HttpPost(url(resources, options));
        }
        else
        {
            throw new IllegalArgumentException();
        }

        addAuthenticationHeader(request);
        request.setEntity(entity);

        try
        {
            HttpResponse response = client.execute(request);
            checkResponseCode(Method.PUT, resources, options, response);
            EntityUtils.consume(response.getEntity());
        }
        catch (IOException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            throw new ResourceException(Method.PUT, url(resources, options));
        }
    }

    private void addAuthenticationHeader(HttpRequest request)
    {
        request.addHeader(BasicScheme.authenticate(credentials, "US-ASCII", false));
    }

    private String url(List<String> resources, Map<String, Object> options)
    {
        try
        {
            resources = new ArrayList<String>(resources);

            for(int i = 0; i < resources.size(); i++)
            {
                resources.set(i, encode(resources.get(i)));
            }

            URL url = new URL(String.format("%s://%s/api/v1/%s/%s%s",
                    protocol.toString().toLowerCase(),
                    host,
                    name,
                    StringUtils.join(resources, '/'),
                    queryString(options)));

            return url.toString();
        }
        catch (MalformedURLException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    private void checkResponseCode(Method method, List<String> resources,
            Map<String, Object> options, HttpResponse response)
            throws ResourceException, IOException
    {
        int code = response.getStatusLine().getStatusCode();

        if(code != 200)
        {
            EntityUtils.consume(response.getEntity());
            throw new ResourceException(method, url(resources, options));
        }
    }

    private String queryString(Map<String, Object> options)
            throws UnsupportedEncodingException
    {
        if(options == null)
        {
            return "";
        }

        ArrayList<String> pairs = new ArrayList<String>();

        for(String key : options.keySet())
        {
            pairs.add(encode(key) + "=" + encode(options.get(key)));
        }

        return pairs.size() > 0 ? "?" + StringUtils.join(pairs, "&") : "";
    }

    private String encode(Object s) throws UnsupportedEncodingException
    {
        return URLEncoder.encode(s.toString(), "UTF-8");
    }
}
