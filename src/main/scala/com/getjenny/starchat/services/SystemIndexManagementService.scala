package com.getjenny.starchat.services

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 22/11/17.
  */

import com.getjenny.starchat.entities.{IndexManagementResponse, _}

import scala.concurrent.{Future}
import org.elasticsearch.client.transport.TransportClient
import scala.io.Source
import java.io._
import scala.collection.JavaConverters._

import akka.event.{Logging, LoggingAdapter}
import com.getjenny.starchat.SCActorSystem
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse
import org.elasticsearch.common.xcontent.XContentType

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Implements functions, eventually used by IndexManagementResource, for ES index management
  */
object SystemIndexManagementService {
  val elastic_client: SystemIndexManagementElasticClient.type = SystemIndexManagementElasticClient
  val log: LoggingAdapter = Logging(SCActorSystem.system, this.getClass.getCanonicalName)

  val schemaFiles: List[JsonIndexFiles] = List[JsonIndexFiles](
    JsonIndexFiles(path = "/index_management/json_index_spec/system/user.json",
      updatePath = "/index_management/json_index_spec/system/update/user.json",
      indexSuffix = elastic_client.userIndexSuffix),
    JsonIndexFiles(path = "/index_management/json_index_spec/system/refresh_decisiontable.json",
      updatePath = "/index_management/json_index_spec/system/update/refresh_decisiontable.json",
      indexSuffix = elastic_client.systemRefreshDtIndexSuffix)
  )

  def createIndex() : Future[Option[IndexManagementResponse]] = Future {
    val client: TransportClient = elastic_client.getClient()

    val operations_message: List[String] = schemaFiles.map(item => {
      val json_in_stream: InputStream = getClass.getResourceAsStream(item.path)
      if (json_in_stream == None.orNull) {
        val message = "Check the file: (" + item.path + ")"
        throw new FileNotFoundException(message)
      }

      val schemaJson: String = Source.fromInputStream(json_in_stream, "utf-8").mkString
      val fullIndexName = elastic_client.indexName + "." + item.indexSuffix

      val createIndexRes: CreateIndexResponse =
        client.admin().indices().prepareCreate(fullIndexName)
          .setSource(schemaJson, XContentType.JSON).get()

      item.indexSuffix + "(" + fullIndexName + ", " + createIndexRes.isAcknowledged.toString + ")"
    })

    val message = "IndexCreation: " + operations_message.mkString(" ")

    Option { IndexManagementResponse(message) }
  }

  def removeIndex() : Future[Option[IndexManagementResponse]] = Future {
    val client: TransportClient = elastic_client.getClient()

    if (! elastic_client.enableDeleteIndex) {
      val message: String = "operation is not allowed, contact system administrator"
      throw new Exception(message)
    }

    val operationsMessage: List[String] = schemaFiles.map(item => {
      val fullIndexName = elastic_client.indexName + "." + item.indexSuffix

      val deleteIndexRes: DeleteIndexResponse =
        client.admin().indices().prepareDelete(fullIndexName).get()

      item.indexSuffix + "(" + fullIndexName + ", " + deleteIndexRes.isAcknowledged.toString + ")"

    })

    val message = "IndexDeletion: " + operationsMessage.mkString(" ")

    Option { IndexManagementResponse(message) }
  }

  def checkIndex() : Future[Option[IndexManagementResponse]] = Future {
    val client: TransportClient = elastic_client.getClient()

    val operationsMessage: List[String] = schemaFiles.map(item => {
      val fullIndexName = elastic_client.indexName + "." + item.indexSuffix
      val getMappingsReq = client.admin.indices.prepareGetMappings(fullIndexName).get()
      val check = getMappingsReq.mappings.containsKey(fullIndexName)
      item.indexSuffix + "(" + fullIndexName + ", " + check + ")"
    })

    val message = "IndexCheck: " + operationsMessage.mkString(" ")

    Option { IndexManagementResponse(message) }
  }

  def updateIndex() : Future[Option[IndexManagementResponse]] = Future {
    val client: TransportClient = elastic_client.getClient()

    val operationsMessage: List[String] = schemaFiles.map(item => {
      val jsonInStream: InputStream = getClass.getResourceAsStream(item.updatePath)

      if (jsonInStream == None.orNull) {
        val message = "Check the file: (" + item.path + ")"
        throw new FileNotFoundException(message)
      }

      val schemaJson: String = Source.fromInputStream(jsonInStream, "utf-8").mkString
      val fullIndexName = elastic_client.indexName + "." + item.indexSuffix

      val updateIndexRes  =
        client.admin().indices().preparePutMapping().setIndices(fullIndexName)
          .setType(item.indexSuffix)
          .setSource(schemaJson, XContentType.JSON).get()

      item.indexSuffix + "(" + fullIndexName + ", " + updateIndexRes.isAcknowledged.toString + ")"
    })

    val message = "IndexUpdate: " + operationsMessage.mkString(" ")

    Option { IndexManagementResponse(message) }
  }

  def refreshIndexes() : Future[Option[RefreshIndexResults]] = Future {
    val operationsResults: List[RefreshIndexResult] = schemaFiles.map(item => {
      val fullIndexName = elastic_client.indexName + "." + item.indexSuffix
      val refreshIndexRes: RefreshIndexResult = elastic_client.refreshIndex(fullIndexName)
      if (refreshIndexRes.failed_shards_n > 0) {
        val indexRefreshMessage = item.indexSuffix + "(" + fullIndexName + ", " + refreshIndexRes.failed_shards_n + ")"
        throw new Exception(indexRefreshMessage)
      }

      refreshIndexRes
    })

    Option { RefreshIndexResults(results = operationsResults) }
  }

  def getIndices: Future[List[String]] = Future {
    val indicesRes = elastic_client.getClient()
      .admin.cluster.prepareState.get.getState.getMetaData.getIndices.asScala
    indicesRes.map(x => x.key).toList
  }


}
