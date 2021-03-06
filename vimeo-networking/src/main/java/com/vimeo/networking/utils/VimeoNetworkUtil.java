/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Vimeo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.vimeo.networking.utils;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vimeo.networking.VimeoClient;
import com.vimeo.networking.model.error.VimeoError;
import com.vimeo.stag.generated.Stag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * A utility class that can eventually be shared across various retrofit/vimeo-api reliant libraries
 * <p>
 * Created by kylevenn on 10/30/15.
 */
@SuppressWarnings("unused")
public class VimeoNetworkUtil {

    @Nullable
    private static Gson sGson;

    /**
     * Static helper method that automatically applies the VimeoClient Gson preferences
     * <p>
     * This includes formatting for dates as well as a LOWER_CASE_WITH_UNDERSCORES field naming policy
     *
     * @return Gson object that can be passed into a {@link GsonConverterFactory} create() method
     */
    @NotNull
    public static Gson getGson() {
        if (sGson == null) {
            sGson = getGsonBuilder().create();
        }
        return sGson;
    }

    /**
     * Static helper method that automatically applies the VimeoClient Gson preferences
     * <p>
     * This includes formatting for dates as well as a LOWER_CASE_WITH_UNDERSCORES field naming policy
     *
     * @return GsonBuilder that can be built upon and then created
     */
    @NotNull
    public static GsonBuilder getGsonBuilder() {
        // Example date: "2015-05-21T14:24:03+00:00"
        return new GsonBuilder().registerTypeAdapterFactory(new Stag.Factory())
                .registerTypeAdapter(Date.class, ISO8601Wrapper.getDateSerializer())
                .registerTypeAdapter(Date.class, ISO8601Wrapper.getDateDeserializer())
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
    }

    /**
     * Returns a simple query map from a provided uri. The simple map enforces there is exactly one value for
     * every name (multiple values for the same name are regularly allowed in a set of parameters)
     *
     * @param uri a uri, optionally with a query
     * @return a query map with a one to one mapping of names to values or empty {@link HashMap}
     * if no parameters are found on the uri
     * @see <a href="http://stackoverflow.com/a/13592567/1759443">StackOverflow</a>
     */
    @NotNull
    public static Map<String, String> getSimpleQueryMap(@NotNull String uri) {
        final Map<String, String> queryPairs = new LinkedHashMap<>();
        try {
            String query = uri.split("\\?")[1];
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                               URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
            return queryPairs;
        } catch (UnsupportedEncodingException e) {
            // Just print the trace, we don't want to crash the app. If you ever get an empty query params
            // map back, then we know there was a malformed URL returned from the api (or a failure) 1/27/16 [KV]
            e.printStackTrace();
        }

        return queryPairs;
    }

    /**
     * Return a builder of the given cache control because for some reason this doesn't exist already.
     * Useful for adding more attributes to an already defined {@link CacheControl}
     *
     * @param cacheControl The CacheControl to convert to a builder
     * @return A builder with the same attributes as the CacheControl passed in
     */
    @NotNull
    public static CacheControl.Builder getCacheControlBuilder(@NotNull CacheControl cacheControl) {
        CacheControl.Builder builder = new CacheControl.Builder();
        if (cacheControl.maxAgeSeconds() > -1) {
            builder.maxAge(cacheControl.maxAgeSeconds(), TimeUnit.SECONDS);
        }
        if (cacheControl.maxStaleSeconds() > -1) {
            builder.maxStale(cacheControl.maxStaleSeconds(), TimeUnit.SECONDS);
        }
        if (cacheControl.minFreshSeconds() > -1) {
            builder.minFresh(cacheControl.minFreshSeconds(), TimeUnit.SECONDS);
        }

        if (cacheControl.noCache()) {
            builder.noCache();
        }
        if (cacheControl.noStore()) {
            builder.noStore();
        }
        if (cacheControl.noTransform()) {
            builder.noTransform();
        }
        if (cacheControl.onlyIfCached()) {
            builder.onlyIfCached();
        }
        return builder;
    }

    /** A helper which cancels an array of {@link Call} objects. */
    public static void cancelCalls(@NotNull final ArrayList<Call> callsToCancel) {
        final List<Call> callList = new CopyOnWriteArrayList<>(callsToCancel);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Call call : callList) {
                    if (call != null) {
                        call.cancel();
                    }
                }
            }
        }).start();
    }

    /**
     * This utility method takes a Retrofit response and extracts a {@link VimeoError} object out of it if
     * applicable. It will return null in the case where there has been a successful response.
     *
     * @param response A non-null response from the Vimeo API
     * @return a {@link VimeoError} object extracted from the response or null
     */
    @Nullable
    public static <ResponseType_T> VimeoError getErrorFromResponse(@Nullable final Response<ResponseType_T> response) {
        if (response != null && response.isSuccessful()) {
            return null;
        }
        VimeoError vimeoError = null;
        if (response != null && response.errorBody() != null) {
            try {
                final Converter<ResponseBody, VimeoError> errorConverter = VimeoClient.getInstance()
                        .getRetrofit()
                        .responseBodyConverter(VimeoError.class, new Annotation[0]);
                vimeoError = errorConverter.convert(response.errorBody());
            } catch (final Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }
        if (vimeoError == null) {
            vimeoError = new VimeoError();
        }
        vimeoError.setResponse(response);
        return vimeoError;
    }
}
