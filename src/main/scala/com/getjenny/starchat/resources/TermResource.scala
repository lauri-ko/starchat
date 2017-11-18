package com.getjenny.starchat.resources

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 12/03/17.
  */

import akka.http.scaladsl.server.Route
import com.getjenny.starchat.entities._
import com.getjenny.starchat.routing._

import scala.concurrent.{Await, Future}
import akka.http.scaladsl.model.StatusCodes
import com.getjenny.starchat.services.{BasicHttpAuthenticatorElasticSearch, TermService}
import akka.pattern.CircuitBreaker

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait TermResource extends MyResource {

  def termRoutes: Route =
    pathPrefix("""^(index_(?:[A-Za-z0-9_]+))$""".r ~ Slash ~ "term") { index_name =>
      val termService = TermService
      path(Segment) { operation: String =>
        post {
          operation match {
            case "index" =>
              authenticateBasicPFAsync(realm = "starchat",
                authenticator = BasicHttpAuthenticatorElasticSearch.authenticator) { user =>
                authorizeAsync(_ =>
                  BasicHttpAuthenticatorElasticSearch.hasPermissions(user, index_name, Permissions.create)) {
                  parameters("refresh".as[Int] ? 0) { refresh =>
                    entity(as[Terms]) { request_data =>
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(termService.index_term(index_name, request_data, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + index_name + ") route=termRoutes method=POST function=index : " +
                            e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 100, message = e.getMessage)
                            })
                      }
                    }
                  }
                }
              }
            case "get" =>
              authenticateBasicPFAsync(realm = "starchat",
                authenticator = BasicHttpAuthenticatorElasticSearch.authenticator) { user =>
                authorizeAsync(_ =>
                  BasicHttpAuthenticatorElasticSearch.hasPermissions(user, index_name, Permissions.create)) {
                  entity(as[TermIdsRequest]) { request_data =>
                    val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(Future {
                      termService.get_term(index_name, request_data)
                    }) {
                      case Success(t) =>
                        completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                      case Failure(e) =>
                        log.error("index(" + index_name + ") route=termRoutes method=POST function=get : " +
                          e.getMessage)
                        completeResponse(StatusCodes.BadRequest,
                          Option {
                            ReturnMessageData(code = 101, message = e.getMessage)
                          })
                    }
                  }
                }
              }
            case _ => completeResponse(StatusCodes.BadRequest,
              Option{IndexManagementResponse(message = "index(" + index_name + ") Operation not supported: " +
                operation)})
          }
        }
      } ~
        pathEnd {
          delete {
            authenticateBasicPFAsync(realm = "starchat",
              authenticator = BasicHttpAuthenticatorElasticSearch.authenticator) { user =>
              authorizeAsync(_ =>
                BasicHttpAuthenticatorElasticSearch.hasPermissions(user, index_name, Permissions.delete)) {
                parameters("refresh".as[Int] ? 0) { refresh =>
                  entity(as[TermIdsRequest]) { request_data =>
                    val termService = TermService
                    if (request_data.ids.nonEmpty) {
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(termService.delete(index_name, request_data, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + index_name + ") route=termRoutes method=DELETE : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 102, message = e.getMessage)
                            })
                      }
                    } else {
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(termService.deleteAll(index_name)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + index_name + ") route=termRoutes method=DELETE : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 103, message = e.getMessage)
                            })
                      }
                    }
                  }
                }
              }
            }
          } ~
            put {
              authenticateBasicPFAsync(realm = "starchat",
                authenticator = BasicHttpAuthenticatorElasticSearch.authenticator) { user =>
                authorizeAsync(_ =>
                  BasicHttpAuthenticatorElasticSearch.hasPermissions(user, index_name, Permissions.update)) {
                  parameters("refresh".as[Int] ? 0) { refresh =>
                    entity(as[Terms]) { request_data =>
                      val termService = TermService
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(termService.update_term(index_name, request_data, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + index_name + ") route=termRoutes method=PUT : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest, Option {
                            IndexManagementResponse(message = e.getMessage)
                          })
                      }
                    }
                  }
                }
              }
            }
        } ~
        path(Segment) { operation: String =>
          get {
            authenticateBasicPFAsync(realm = "starchat",
              authenticator = BasicHttpAuthenticatorElasticSearch.authenticator) { user =>
              authorizeAsync(_ =>
                BasicHttpAuthenticatorElasticSearch.hasPermissions(user, index_name, Permissions.read)) {
                operation match {
                  case "term" =>
                    entity(as[Term]) { request_data =>
                      val termService = TermService
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(termService.search_term(index_name, request_data)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + index_name + ") route=termRoutes method=GET function=term : " +
                            e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              IndexManagementResponse(message = e.getMessage)
                            })
                      }
                    }
                  case "text" =>
                    entity(as[String]) { request_data =>
                      val termService = TermService
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(termService.search(index_name, request_data)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + index_name + ") route=termRoutes method=GET function=text : " +
                            e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              IndexManagementResponse(message = e.getMessage)
                            })
                      }
                    }
                }
              }
            }
          }
        }
    }
}
