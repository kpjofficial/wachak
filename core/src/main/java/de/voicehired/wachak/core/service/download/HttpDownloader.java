package de.voicehired.wachak.core.service.download;

import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;

import de.voicehired.wachak.core.ClientConfig;
import de.voicehired.wachak.core.R;
import de.voicehired.wachak.core.feed.FeedImage;
import de.voicehired.wachak.core.util.DateUtils;
import de.voicehired.wachak.core.util.DownloadError;
import de.voicehired.wachak.core.util.StorageUtils;
import de.voicehired.wachak.core.util.URIUtil;
import okio.ByteString;

public class HttpDownloader extends Downloader {
    private static final String TAG = "HttpDownloader";

    private static final int BUFFER_SIZE = 8 * 1024;

    public HttpDownloader(DownloadRequest request) {
        super(request);
    }

    @Override
    protected void download() {
        File destination = new File(request.getDestination());
        final boolean fileExists = destination.exists();

        if (request.isDeleteOnFailure() && fileExists) {
            Log.w(TAG, "File already exists");
            if (request.getFeedfileType() != FeedImage.FEEDFILETYPE_FEEDIMAGE) {
                onFail(DownloadError.ERROR_FILE_EXISTS, null);
                return;
            } else {
                onSuccess();
                return;
            }
        }

        OkHttpClient httpClient = AntennapodHttpClient.getHttpClient();
        RandomAccessFile out = null;
        InputStream connection;
        ResponseBody responseBody = null;

        try {
            final URI uri = URIUtil.getURIFromRequestUrl(request.getSource());
            Request.Builder httpReq = new Request.Builder().url(uri.toURL())
                    .header("User-Agent", ClientConfig.USER_AGENT);
            if(!TextUtils.isEmpty(request.getLastModified())) {
                String lastModified = request.getLastModified();
                Date lastModifiedDate = DateUtils.parse(lastModified);
                if(lastModifiedDate != null) {
                    long threeDaysAgo = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 3;
                    if (lastModifiedDate.getTime() > threeDaysAgo) {
                        Log.d(TAG, "addHeader(\"If-Modified-Since\", \"" + lastModified + "\")");
                        httpReq.addHeader("If-Modified-Since", lastModified);
                    }
                } else {
                    String eTag = lastModified;
                    Log.d(TAG, "addHeader(\"If-None-Match\", \"" + eTag + "\")");
                    httpReq.addHeader("If-None-Match", eTag);
                }
            }

            // add authentication information
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                String[] parts = userInfo.split(":");
                if (parts.length == 2) {
                    String credentials = encodeCredentials(parts[0], parts[1], "ISO-8859-1");
                    httpReq.header("Authorization", credentials);
                }
            } else if (!TextUtils.isEmpty(request.getUsername()) && request.getPassword() != null) {
                String credentials = encodeCredentials(request.getUsername(), request.getPassword(),
                        "ISO-8859-1");
                httpReq.header("Authorization", credentials);
            }

            // add range header if necessary
            if (fileExists) {
                request.setSoFar(destination.length());
                httpReq.addHeader("Range", "bytes=" + request.getSoFar() + "-");
                Log.d(TAG, "Adding range header: " + request.getSoFar());
            }

            Response response = null;
            try {
                response = httpClient.newCall(httpReq.build()).execute();
            } catch(IOException e) {
                Log.e(TAG, e.toString());
                if(e.getMessage().contains("PROTOCOL_ERROR")) {
                    httpClient.setProtocols(Arrays.asList(Protocol.HTTP_1_1));
                    response = httpClient.newCall(httpReq.build()).execute();
                }
                else {
                    throw e;
                }
            }
            responseBody = response.body();
            String contentEncodingHeader = response.header("Content-Encoding");
            boolean isGzip = false;
            if(!TextUtils.isEmpty(contentEncodingHeader)) {
                isGzip = TextUtils.equals(contentEncodingHeader.toLowerCase(), "gzip");
            }

            Log.d(TAG, "Response code is " + response.code());

            if(!response.isSuccessful() && response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Log.d(TAG, "Authorization failed, re-trying with UTF-8 encoding");
                if (userInfo != null) {
                    String[] parts = userInfo.split(":");
                    if (parts.length == 2) {
                        String credentials = encodeCredentials(parts[0], parts[1], "UTF-8");
                        httpReq.header("Authorization", credentials);
                    }
                } else if (!TextUtils.isEmpty(request.getUsername()) && request.getPassword() != null) {
                    String credentials = encodeCredentials(request.getUsername(), request.getPassword(),
                            "UTF-8");
                    httpReq.header("Authorization", credentials);
                }
                response = httpClient.newCall(httpReq.build()).execute();
                responseBody = response.body();
                contentEncodingHeader = response.header("Content-Encoding");
                if(!TextUtils.isEmpty(contentEncodingHeader)) {
                    isGzip = TextUtils.equals(contentEncodingHeader.toLowerCase(), "gzip");
                }
            }

            if(!response.isSuccessful() && response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.d(TAG, "Feed '" + request.getSource() + "' not modified since last update, Download canceled");
                onCancelled();
                return;
            }

            if (!response.isSuccessful() || response.body() == null) {
                final DownloadError error;
                final String details;
                if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    error = DownloadError.ERROR_UNAUTHORIZED;
                    details = String.valueOf(response.code());
                } else {
                    error = DownloadError.ERROR_HTTP_DATA_ERROR;
                    details = String.valueOf(response.code());
                }
                onFail(error, details);
                return;
            }

            if (!StorageUtils.storageAvailable()) {
                onFail(DownloadError.ERROR_DEVICE_NOT_FOUND, null);
                return;
            }

            connection = new BufferedInputStream(responseBody.byteStream());

            String contentRangeHeader = (fileExists) ? response.header("Content-Range") : null;

            if (fileExists && response.code() == HttpURLConnection.HTTP_PARTIAL
                    && !TextUtils.isEmpty(contentRangeHeader)) {
                String start = contentRangeHeader.substring("bytes ".length(),
                        contentRangeHeader.indexOf("-"));
                request.setSoFar(Long.valueOf(start));
                Log.d(TAG, "Starting download at position " + request.getSoFar());

                out = new RandomAccessFile(destination, "rw");
                out.seek(request.getSoFar());
            } else {
                destination.delete();
                destination.createNewFile();
                out = new RandomAccessFile(destination, "rw");
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            int count = 0;
            request.setStatusMsg(R.string.download_running);
            Log.d(TAG, "Getting size of download");
            request.setSize(responseBody.contentLength() + request.getSoFar());
            Log.d(TAG, "Size is " + request.getSize());
            if (request.getSize() < 0) {
                request.setSize(DownloadStatus.SIZE_UNKNOWN);
            }

            long freeSpace = StorageUtils.getFreeSpaceAvailable();
            Log.d(TAG, "Free space is " + freeSpace);

            if (request.getSize() != DownloadStatus.SIZE_UNKNOWN
                    && request.getSize() > freeSpace) {
                onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null);
                return;
            }

            Log.d(TAG, "Starting download");
            try {
                while (!cancelled
                        && (count = connection.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    request.setSoFar(request.getSoFar() + count);
                    request.setProgressPercent((int) (((double) request
                            .getSoFar() / (double) request
                            .getSize()) * 100));
                }
            } catch(IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            if (cancelled) {
                onCancelled();
            } else {
                // check if size specified in the response header is the same as the size of the
                // written file. This check cannot be made if compression was used
                if (!isGzip && request.getSize() != DownloadStatus.SIZE_UNKNOWN &&
                        request.getSoFar() != request.getSize()) {
                    onFail(DownloadError.ERROR_IO_ERROR,
                            "Download completed but size: " +
                                    request.getSoFar() +
                                    " does not equal expected size " +
                                    request.getSize()
                    );
                    return;
                } else if(request.getSize() > 0 && request.getSoFar() == 0){
                    onFail(DownloadError.ERROR_IO_ERROR, "Download completed, but nothing was read");
                    return;
                }
                String lastModified = response.header("Last-Modified");
                if(lastModified != null) {
                    request.setLastModified(lastModified);
                } else {
                    request.setLastModified(response.header("ETag"));
                }
                onSuccess();
            }

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_MALFORMED_URL, e.getMessage());
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_CONNECTION_ERROR, e.getMessage());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_UNKNOWN_HOST, e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_IO_ERROR, e.getMessage());
        } catch (NullPointerException e) {
            // might be thrown by connection.getInputStream()
            e.printStackTrace();
            onFail(DownloadError.ERROR_CONNECTION_ERROR, request.getSource());
        } finally {
            IOUtils.closeQuietly(out);
            AntennapodHttpClient.cleanup();
            IOUtils.closeQuietly(responseBody);
        }
    }

    private void onSuccess() {
        Log.d(TAG, "Download was successful");
        result.setSuccessful();
    }

    private void onFail(DownloadError reason, String reasonDetailed) {
        Log.d(TAG, "Download failed");
        result.setFailed(reason, reasonDetailed);
        if (request.isDeleteOnFailure()) {
            cleanup();
        }
    }

    private void onCancelled() {
        Log.d(TAG, "Download was cancelled");
        result.setCancelled();
        cleanup();
    }

    /**
     * Deletes unfinished downloads.
     */
    private void cleanup() {
        if (request.getDestination() != null) {
            File dest = new File(request.getDestination());
            if (dest.exists()) {
                boolean rc = dest.delete();
                Log.d(TAG, "Deleted file " + dest.getName() + "; Result: "
                            + rc);
            } else {
                Log.d(TAG, "cleanup() didn't delete file: does not exist.");
            }
        }
    }

    public static String encodeCredentials(String username, String password, String charset) {
        try {
            String credentials = username + ":" + password;
            byte[] bytes = credentials.getBytes(charset);
            String encoded = ByteString.of(bytes).base64();
            return "Basic " + encoded;
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError();
        }
    }

}
