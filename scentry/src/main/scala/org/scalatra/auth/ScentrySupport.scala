package org.scalatra.auth

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import util.DynamicVariable
import javax.servlet.{FilterConfig, ServletConfig}
import org.scalatra.{CookieSupport, Initializable, Handler, ScalatraKernel}

trait ScentryConfig {
  val login = "/login"
  val returnTo = "/"
  val returnToKey = "returnTo"
  val failureUrl = "/unauthenticated"
}

trait ScentrySupport[TypeForUser <: AnyRef] extends Handler with Initializable with CookieSupport {
  self : ScalatraKernel =>

  type UserType = TypeForUser
  type ScentryConfiguration <: ScentryConfig

  protected def fromSession: PartialFunction[String, UserType]
  protected def toSession: PartialFunction[UserType, String]
  protected val scentryConfig: ScentryConfiguration

  private val _scentry = new DynamicVariable[Scentry[UserType]](null)
  private var _strategiesFromConfig = List[String]()

  abstract override def initialize(config: Config) {
    super.initialize(config)
    readStrategiesFromConfig(config)
  }

  abstract override def handle(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    val app = ScalatraKernelProxy(session, params, uri => redirect(uri), request, response, cookies)
    _scentry.withValue(new Scentry[UserType](app, toSession, fromSession)) {
      registerStrategiesFromConfig
      registerAuthStrategies
      super.handle(servletRequest, servletResponse)
    }
  }

  private def readStrategiesFromConfig(config: Config) = _strategiesFromConfig = {
    val strats = (config match {
      case servletConfig: ServletConfig => {
        servletConfig.getInitParameter("scentry.strategies")
      }
      case filterConfig: FilterConfig =>
        filterConfig.getInitParameter("scentry.strategies")
      case _ => ""
    })
    if(strats != null && strats.trim.length > 0) (strats split ";").toList else Nil
  }

  private def registerStrategiesFromConfig = _strategiesFromConfig foreach { strategyClassName =>
    val strategy = Class.forName(strategyClassName).newInstance.asInstanceOf[ScentryStrategy[UserType]]
    strategy registerWith scentry
  }



  /**
   * Override this method to register authentication strategies specific to this servlet.
   *     registerAuthStrategy('UserPassword, app => new UserPasswordStrategy(app))
   */
  protected def registerAuthStrategies = {

  }

  protected def scentry: Scentry[UserType] = _scentry.value
  protected def scentryOption: Option[Scentry[UserType]] = Option(scentry)
  protected def user: UserType = scentry.user
  protected def user_=(user: UserType) = scentry.user = user
  protected def authenticated_? : Boolean = session(Scentry.scentryAuthKey).isDefined
  protected def unAuthenticated_? : Boolean = !authenticated_?

  protected def authenticate() = {
    scentry.authenticate()
  }

  protected def logOut_! = scentry.logout


}