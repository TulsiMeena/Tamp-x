package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface MailTmApiService {

    @GET("domains")
    suspend fun getDomains(): MailTmDomainCollection

    @POST("accounts")
    suspend fun createAccount(
        @Body request: MailTmAccountRequest
    ): MailTmAccountResponse

    @POST("token")
    suspend fun getToken(
        @Body request: MailTmAccountRequest
    ): MailTmTokenResponse

    @GET("messages")
    suspend fun getMessages(
        @Header("Authorization") token: String
    ): MailTmMessageCollection

    @GET("messages/{id}")
    suspend fun getMessageDetail(
        @Path("id") id: String,
        @Header("Authorization") token: String
    ): MailTmMessageDetail

    companion object {
        private const val BASE_URL = "https://api.mail.tm/"

        fun create(): MailTmApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(MailTmApiService::class.java)
        }
    }
}
