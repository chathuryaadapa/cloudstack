//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.agent.direct.download;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NoHttpResponseException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Map;

public class HttpDirectTemplateDownloader extends DirectTemplateDownloaderImpl {

    private HttpClient client;
    private static final MultiThreadedHttpConnectionManager s_httpClientManager = new MultiThreadedHttpConnectionManager();
    private static final int CHUNK_SIZE = 1024 * 1024; //1M
    protected HttpMethodRetryHandler myretryhandler;
    public static final Logger s_logger = Logger.getLogger(HttpDirectTemplateDownloader.class.getName());
    protected GetMethod request;

    public HttpDirectTemplateDownloader(String url, Long templateId, String destPoolPath, String checksum, Map<String, String> headers) {
        super(url, destPoolPath, templateId, checksum);
        client = new HttpClient(s_httpClientManager);
        myretryhandler = createRetryTwiceHandler();
        request = createRequest(url, headers);
        String downloadDir = getDirectDownloadTempPath(templateId);
        createTemporaryDirectoryAndFile(downloadDir);
    }

    protected void createTemporaryDirectoryAndFile(String downloadDir) {
        createFolder(getDestPoolPath() + File.separator + downloadDir);
        File f = new File(getDestPoolPath() + File.separator + downloadDir + File.separator + getFileNameFromUrl());
        setDownloadedFilePath(f.getAbsolutePath());
    }

    protected GetMethod createRequest(String downloadUrl, Map<String, String> headers) {
        GetMethod request = new GetMethod(downloadUrl);
        request.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, myretryhandler);
        request.setFollowRedirects(true);
        if (MapUtils.isNotEmpty(headers)) {
            for (String key : headers.keySet()) {
                request.setRequestHeader(key, headers.get(key));
            }
        }
        return request;
    }

    protected HttpMethodRetryHandler createRetryTwiceHandler() {
        return new HttpMethodRetryHandler() {
            @Override
            public boolean retryMethod(final HttpMethod method, final IOException exception, int executionCount) {
                if (executionCount >= 2) {
                    // Do not retry if over max retry count
                    return false;
                }
                if (exception instanceof NoHttpResponseException) {
                    // Retry if the server dropped connection on us
                    return true;
                }
                if (!method.isRequestSent()) {
                    // Retry if the request has not been sent fully or
                    // if it's OK to retry methods that have been sent
                    return true;
                }
                // otherwise do not retry
                return false;
            }
        };
    }

    @Override
    public boolean downloadTemplate() {
        File f = new File(getDownloadedFilePath());
        try (
                InputStream in = request.getResponseBodyAsStream();
                RandomAccessFile out = new RandomAccessFile(f, "rw");
        ) {
            client.executeMethod(request);
            out.seek(0);
            copyBytes(in, out);
        } catch (IOException e) {
            s_logger.error("Error downloading template " + getTemplateId() + e.getMessage());
            return false;
        }
        return true;
    }

    protected boolean copyBytes(InputStream in, RandomAccessFile out) throws IOException {
        int bytes;
        byte[] block = new byte[CHUNK_SIZE];
        long offset = 0;
        boolean done = false;
        while (!done) {
            if ((bytes = in.read(block, 0, CHUNK_SIZE)) > -1) {
                offset = writeBlock(bytes, out, block, offset);
            } else {
                done = true;
            }
        }
        out.getFD().sync();
        return false;
    }

    protected long writeBlock(int bytes, RandomAccessFile out, byte[] block, long offset) throws IOException {
        out.write(block, 0, bytes);
        offset += bytes;
        out.seek(offset);
        return offset;
    }
}