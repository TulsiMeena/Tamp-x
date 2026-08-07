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

interface GuerrillaMailApiService {

    @GET("ajax.php?f=get_email_address")
    suspend fun getEmailAddress(
        @Query("lang") lang: String = "en",
        @Query("sid_token") sidToken: String? = null
    ): GmAddressResponse

    @GET("ajax.php?f=set_email_user")
    suspend fun setEmailUser(
        @Query("email_user") emailUser: String,
        @Query("domain") domain: String? = null,
        @Query("sid_token") sidToken: String? = null
    ): GmSetUserResponse

    @GET("ajax.php?f=get_email_list")
    suspend fun getEmailList(
        @Query("offset") offset: Int = 0,
        @Query("sid_token") sidToken: String
    ): GmListResponse

    @GET("ajax.php?f=check_email")
    suspend fun checkEmail(
        @Query("seq") seq: Long = 0,
        @Query("sid_token") sidToken: String
    ): GmListResponse

    @GET("ajax.php?f=fetch_email")
    suspend fun fetchEmail(
        @Query("email_id") emailId: String,
        @Query("sid_token") sidToken: String
    ): GmMailDetailDto

    @GET("ajax.php?f=del_email")
    suspend fun delEmail(
        @Query("email_ids[]") emailId: String,
        @Query("sid_token") sidToken: String
    ): GmListResponse

    companion object {
        private const val BASE_URL = "https://api.guerrillamail.com/"

        fun create(): GuerrillaMailApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
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
                .create(GuerrillaMailApiService::class.java)
        }
    }
}
