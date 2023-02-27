package io.minchat.rest.service

import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.minchat.common.*
import io.minchat.common.request.*
import io.minchat.rest.*
import org.mindrot.jbcrypt.BCrypt

class ChannelService(baseUrl: String, client: HttpClient) : RestService(baseUrl, client) {

}
