package cromwell.database.sql

import java.sql.Timestamp

import cromwell.database.sql.joins.MetadataJobQueryValue
import cromwell.database.sql.tables.{MetadataEntry, WorkflowMetadataSummaryEntry}
import slick.basic.DatabasePublisher

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

trait MetadataSqlDatabase extends SqlDatabase {

  /*
  The following section relates to:
.___  ___.  _______ .___________.    ___       _______       ___   .___________.    ___
|   \/   | |   ____||           |   /   \     |       \     /   \  |           |   /   \
|  \  /  | |  |__   `---|  |----`  /  ^  \    |  .--.  |   /  ^  \ `---|  |----`  /  ^  \
|  |\/|  | |   __|      |  |      /  /_\  \   |  |  |  |  /  /_\  \    |  |      /  /_\  \
|  |  |  | |  |____     |  |     /  _____  \  |  '--'  | /  _____  \   |  |     /  _____  \
|__|  |__| |_______|    |__|    /__/     \__\ |_______/ /__/     \__\  |__|    /__/     \__\
   */

  def existsMetadataEntries()(implicit ec: ExecutionContext): Future[Boolean]

  /**
    * Add metadata events to the database transactionally.
    */
  def addMetadataEntries(metadataEntries: Iterable[MetadataEntry],
                         startMetadataKey: String,
                         endMetadataKey: String,
                         nameMetadataKey: String,
                         statusMetadataKey: String,
                         submissionMetadataKey: String,
                         parentWorkflowIdKey: String,
                         rootWorkflowIdKey: String,
                         labelMetadataKey: String)(implicit ec: ExecutionContext): Future[Unit]

  def metadataEntryExists(workflowExecutionUuid: String)(implicit ec: ExecutionContext): Future[Boolean]

  def metadataSummaryEntryExists(workflowExecutionUuid: String)(implicit ec: ExecutionContext): Future[Boolean]

  def queryMetadataEntries(workflowExecutionUuid: String,
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Seq[MetadataEntry]]

  def streamMetadataEntries(workflowExecutionUuid: String,
                            fetchSize: Int): DatabasePublisher[MetadataEntry]

  def countMetadataEntries(workflowExecutionUuid: String,
                           expandSubWorkflows: Boolean,
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Int]

  def queryMetadataEntries(workflowExecutionUuid: String,
                           metadataKey: String,
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Seq[MetadataEntry]]

  def countMetadataEntries(workflowExecutionUuid: String,
                           metadataKey: String,
                           expandSubWorkflows: Boolean,
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Int]

  def queryMetadataEntries(workflowExecutionUuid: String,
                           callFullyQualifiedName: String,
                           jobIndex: Option[Int],
                           jobAttempt: Option[Int],
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Seq[MetadataEntry]]

  def countMetadataEntries(workflowExecutionUuid: String,
                           callFullyQualifiedName: String,
                           jobIndex: Option[Int],
                           jobAttempt: Option[Int],
                           expandSubWorkflows: Boolean,
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Int]

  def queryMetadataEntries(workflowUuid: String,
                           metadataKey: String,
                           callFullyQualifiedName: String,
                           jobIndex: Option[Int],
                           jobAttempt: Option[Int],
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Seq[MetadataEntry]]

  def countMetadataEntries(workflowUuid: String,
                           metadataKey: String,
                           callFullyQualifiedName: String,
                           jobIndex: Option[Int],
                           jobAttempt: Option[Int],
                           expandSubWorkflows: Boolean,
                           timeout: Duration)
                          (implicit ec: ExecutionContext): Future[Int]

  def queryMetadataEntryWithKeyConstraints(workflowExecutionUuid: String,
                                           metadataKeysToFilterFor: List[String],
                                           metadataKeysToFilterAgainst: List[String],
                                           metadataJobQueryValue: MetadataJobQueryValue,
                                           timeout: Duration)
                                          (implicit ec: ExecutionContext): Future[Seq[MetadataEntry]]

  def countMetadataEntryWithKeyConstraints(workflowExecutionUuid: String,
                                           metadataKeysToFilterFor: List[String],
                                           metadataKeysToFilterAgainst: List[String],
                                           metadataJobQueryValue: MetadataJobQueryValue,
                                           expandSubWorkflows: Boolean,
                                           timeout: Duration)
                                          (implicit ec: ExecutionContext): Future[Int]

  /**
    * Retrieves next summarizable block of metadata satisfying the specified criteria.
    *
    * @param buildUpdatedSummary Takes in the optional existing summary and the metadata, returns the new summary.
    * @return A `Future` with the number of rows summarized by the invocation, and the number of rows still to summarize.
    */
  def summarizeIncreasing(labelMetadataKey: String,
                          limit: Int,
                          buildUpdatedSummary:
                          (Option[WorkflowMetadataSummaryEntry], Seq[MetadataEntry])
                            => WorkflowMetadataSummaryEntry)
                         (implicit ec: ExecutionContext): Future[Long]

  /**
    * Retrieves a window of summarizable metadata satisfying the specified criteria.
    *
    * @param buildUpdatedSummary Takes in the optional existing summary and the metadata, returns the new summary.
    * @return A `Future` with the number of rows summarized by this invocation, and the number of rows still to summarize.
    */
  def summarizeDecreasing(summaryNameDecreasing: String,
                          summaryNameIncreasing: String,
                          labelMetadataKey: String,
                          limit: Int,
                          buildUpdatedSummary:
                          (Option[WorkflowMetadataSummaryEntry], Seq[MetadataEntry])
                            => WorkflowMetadataSummaryEntry)
                         (implicit ec: ExecutionContext): Future[(Long, Long)]

  def updateMetadataArchiveStatus(workflowExecutionUuid: String, newArchiveStatus: Option[String]): Future[Int]

  def getWorkflowStatus(workflowExecutionUuid: String)(implicit ec: ExecutionContext): Future[Option[String]]

  def getWorkflowLabels(workflowExecutionUuid: String)(implicit ec: ExecutionContext): Future[Map[String, String]]

  def getRootAndSubworkflowLabels(rootWorkflowExecutionUuid: String)(implicit ec: ExecutionContext): Future[Map[String, Map[String, String]]]

  def queryWorkflowSummaries(parentIdWorkflowMetadataKey: String,
                             workflowStatuses: Set[String],
                             workflowNames: Set[String],
                             workflowExecutionUuids: Set[String],
                             labelAndKeyLabelValues: Set[(String,String)],
                             labelOrKeyLabelValues: Set[(String,String)],
                             excludeLabelAndValues: Set[(String,String)],
                             excludeLabelOrValues: Set[(String,String)],
                             submissionTimestamp: Option[Timestamp],
                             startTimestampOption: Option[Timestamp],
                             endTimestampOption: Option[Timestamp],
                             metadataArchiveStatus: Set[Option[String]],
                             includeSubworkflows: Boolean,
                             page: Option[Int],
                             pageSize: Option[Int],
                             newestFirst: Boolean)
                             (implicit ec: ExecutionContext): Future[Traversable[WorkflowMetadataSummaryEntry]]

  def countWorkflowSummaries(parentIdWorkflowMetadataKey: String,
                             workflowStatuses: Set[String], workflowNames: Set[String],
                             workflowExecutionUuids: Set[String],
                             labelAndKeyLabelValues: Set[(String,String)],
                             labelOrKeyLabelValues: Set[(String,String)],
                             excludeLabelAndValues: Set[(String,String)],
                             excludeLabelOrValues: Set[(String,String)],
                             submissionTimestamp: Option[Timestamp],
                             startTimestampOption: Option[Timestamp],
                             endTimestampOption: Option[Timestamp],
                             metadataArchiveStatus: Set[Option[String]],
                             includeSubworkflows: Boolean)
                             (implicit ec: ExecutionContext): Future[Int]

  def deleteAllMetadataForWorkflowAndUpdateArchiveStatus(rootWorkflowId: String, newArchiveStatus: Option[String])(implicit ec: ExecutionContext): Future[Int]

  def isRootWorkflow(rootWorkflowId: String)(implicit ec: ExecutionContext): Future[Option[Boolean]]

  def getRootWorkflowId(workflowId: String)(implicit ec: ExecutionContext): Future[Option[String]]

  def queryWorkflowIdsByArchiveStatusAndEndedOnOrBeforeThresholdTimestamp(archiveStatus: Option[String], thresholdTimestamp: Timestamp, batchSizeOpt: Long)(implicit ec: ExecutionContext): Future[Seq[String]]

  def countRootWorkflowIdsByArchiveStatusAndEndedOnOrBeforeThresholdTimestamp(archiveStatus: Option[String], thresholdTimestamp: Timestamp)(implicit ec: ExecutionContext): Future[Int]

  def getSummaryQueueSize()(implicit ec: ExecutionContext): Future[Int]

  def getMetadataArchiveStatus(workflowId: String)(implicit ec: ExecutionContext): Future[Option[String]]
}
