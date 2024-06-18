package example.endpoint

import zio._
import zio.http._
import zio.http.codec.PathCodec._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.openapi._
import zio.schema.{DeriveSchema, Schema}

object CookieEndpointExample extends ZIOAppDefault {

  case class LoginRequest(email: String, password: String)

  object LoginRequest {
    implicit val schema: Schema[LoginRequest] = DeriveSchema.gen[LoginRequest]
  }

  case class RefreshTokenCookie(refreshToken: String, maxAge: Option[Duration] = None)

  def generateRefreshToken(email: String): String =
    // Generate a real refresh token
    email + "Secret!"

  val setRefreshTokenCookieCodec: HttpCodec[HttpCodecType.Header, RefreshTokenCookie] =
    HeaderCodec.setCookie.transformOrFail[RefreshTokenCookie](cookie =>
      Right(RefreshTokenCookie(cookie.value.content, cookie.value.maxAge)),
    ) { refreshTokenCookie =>
      Right(
        Header.SetCookie(
          Cookie.Response("refreshToken", refreshTokenCookie.refreshToken, maxAge = refreshTokenCookie.maxAge),
        ),
      )
    }

  val loginEndpoint: Endpoint[Unit, LoginRequest, ZNothing, RefreshTokenCookie, EndpointMiddleware.None] =
    Endpoint(Method.POST / "api" / "authentication" / "login")
      .in[LoginRequest]
      .outCodec[RefreshTokenCookie](setRefreshTokenCookieCodec)

  val loginRoute: Route[Any, Response] =
    loginEndpoint.implement { (request: LoginRequest) =>
      // Perform authentication
      ZIO.succeed(RefreshTokenCookie(generateRefreshToken(request.email), Some(Duration.Infinity)))
    }

  val openAPI       = OpenAPIGen.fromEndpoints(title = "Library API", version = "1.0", loginEndpoint)
  val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openAPI)
  val routes        = Routes(loginRoute) ++ swaggerRoutes

  def run = Server.serve(routes).provide(Server.default)
}
