package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TempMailApiService {

    @GET("api/v1/?action=getDomainList")
    suspend fun getDomainList(): List<String>

    @GET("api/v1/?action=getMessages")
    suspend fun getMessages(
        @Query("login") login: String,
        @Query("domain") domain: String
    ): List<SecMailMessageDto>

    @GET("api/v1/?action=readMessage")
    suspend fun readMessage(
        @Query("login") login: String,
        @Query("domain") domain: String,
        @Query("id") id: Int
    ): SecMailDetailDto

    companion object {
        private const val BASE_URL = "https://www.1secmail.com/"

        fun create(): TempMailApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
                .create(TempMailApiService::class.java)
        }
    }
}
