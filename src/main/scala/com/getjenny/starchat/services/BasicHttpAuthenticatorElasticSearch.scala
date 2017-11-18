package com.getjenny.starchat.services

import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.SecurityDirectives._
import scala.concurrent.{Await, Future}
import com.getjenny.starchat.entities._
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import com.roundeights.hasher.Implicits._

import scala.concurrent.ExecutionContext.Implicits.global

object BasicHttpAuthenticatorElasticSearch extends AbstractAuthenticator {

  def secret(password: String, salt: String): String = {
    password + "#" + salt
  }

  def fetchUser(id: String): Future[User] = {
    Future {
      User(
        id = "test_user", /** user id */
        password = "3c98bf19cb962ac4cd0227142b3495ab1be46534061919f792254b80c0f3e566f7819cae73bdc616af0ff555f7460ac96d88d56338d659ebd93e2be858ce1cf9", /** user password */
        salt = "salt", /** salt for password hashing */
        permissions = Map("index_0" -> Set(Permissions.read, Permissions.delete, Permissions.update, Permissions.create))
      )
    }
  }

  class Hasher(salt: String) {
    def hasher(password: String): String = {
      secret(password, salt).sha512
    }
  }

  val authenticator: AsyncAuthenticatorPF[User] = {
    case p @ Credentials.Provided(id) =>
      val user_request = Await.ready(fetchUser(id), 5.seconds).value.get
      user_request match {
        case Success(user) =>
          val hasher = new Hasher(user.salt)
          if(p.verify(secret = user.password, hasher = hasher.hasher)) {
            Future { user }
          } else {
            val message = "Authentication failed for the user: " + user.id
            throw AuthenticatorException(message)
          }
        case Failure(e) =>
          val message: String = "unable to match credentials"
          throw AuthenticatorException(message, e)
      }
  }
}