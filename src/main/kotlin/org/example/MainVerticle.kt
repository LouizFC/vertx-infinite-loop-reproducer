package org.example

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.OAuth2AuthHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import java.util.*

class MainVerticle : AbstractVerticle() {

    private val mapper: ObjectMapper = ObjectMapper()

    override fun start() {

        val authHandler = createAuthHandler(
            JsonObject(
                """
                    {
                      "realm": "test-realm",
                      "auth-server-url": "http://localhost:8180/auth",
                      "ssl-required": "external",
                      "resource": "vertx",
                      "credentials": {
                        "secret": "faa1e5b2-97d6-47e1-a78a-5d3016a00a7b"
                      },
                      "confidential-port": 0
                    }
                """.trimIndent()
            )
        )

        val server = vertx.createHttpServer()

        val router = Router.router(vertx)

        router.enableSessions()

        val callbackRoute = router.get("/auth/callback")
        authHandler.setupCallback(callbackRoute)

        router.route().handler(authHandler)
        router.get("/user").handler(::currentUser)

        server.requestHandler(router).listen(8080)
    }

    private fun Router.enableSessions(): Route {
        return route().handler(SessionHandler.create(LocalSessionStore.create(vertx, "maestro.user.sessions")))
    }

    private fun currentUser(context: RoutingContext) {
        val user = context.user().principal()
        val accessToken = user.getString("access_token")
        val username = accessToken.splitToSequence('.')
            .drop(1)
            .take(1)
            .map { Base64.getDecoder().decode(it) }
            .map { mapper.readTree(it) }
            .firstOrNull()?.get("preferred_username")

        context.response().end("User $username")
    }

    private fun createAuthHandler(keycloakConfig: JsonObject): OAuth2AuthHandler {
        val authProvider = KeycloakAuth.create(
            vertx, OAuth2FlowType.AUTH_CODE, keycloakConfig
        )

        return OAuth2AuthHandler.create(authProvider)
    }


    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val main = Vertx.vertx()
            main.deployVerticle(MainVerticle())
        }
    }
}