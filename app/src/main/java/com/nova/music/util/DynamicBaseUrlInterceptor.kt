package com.nova.music.util

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Interceptor that dynamically changes the base URL for API requests.
 * This allows changing the server URL without rebuilding the app.
 */
class DynamicBaseUrlInterceptor(
    private val getBaseUrl: () -> String
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val baseUrl = getBaseUrl()
        
        // Normalize base URL to not end with slash to avoid double slashes
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) {
            baseUrl.removeSuffix("/")
        } else {
            baseUrl
        }
        
        // Get the full path from the original URL (including the placeholder domain)
        val fullPath = original.url.encodedPath
        
        // Remove the placeholder domain path and get just the endpoint
        val endpoint = if (fullPath.startsWith("/placeholder.com/")) {
            fullPath.substring("/placeholder.com/".length)
        } else if (fullPath.startsWith("/")) {
            fullPath.substring(1) // Remove leading slash
        } else {
            fullPath
        }
        
        // Ensure endpoint starts with a slash for proper URL construction
        val normalizedEndpoint = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        
        val newUrl = normalizedBaseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.encodedPath(normalizedEndpoint)
            ?.query(original.url.query)
            ?.build()
            ?: original.url
            
        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()
            
        return chain.proceed(newRequest)
    }
} 