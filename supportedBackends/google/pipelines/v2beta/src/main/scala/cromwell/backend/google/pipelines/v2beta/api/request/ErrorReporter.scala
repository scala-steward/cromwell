package cromwell.backend.google.pipelines.v2beta.api.request

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Reader}
import com.google.api.services.lifesciences.v2beta.model._
import common.validation.ErrorOr.ErrorOr
import cromwell.backend.google.pipelines.common.action.ActionLabels._
import cromwell.backend.google.pipelines.common.PipelinesApiAsyncBackendJobExecutionActor
import cromwell.backend.google.pipelines.common.api.RunStatus.{
  Cancelled,
  Failed,
  Preempted,
  QuotaFailed,
  UnsuccessfulRunStatus
}
import cromwell.backend.google.pipelines.common.errors.isQuotaMessage
import cromwell.backend.google.pipelines.v2beta.api.request.RequestHandler.logger
import cromwell.core.{ExecutionEvent, WorkflowId}
import io.grpc.{Status => GStatus}
import mouse.all._

import scala.jdk.CollectionConverters._

object ErrorReporter {
  type RequestContext = (WorkflowId, Operation)
  type RequestContextReader[A] = Reader[RequestContext, A]

  // This can be used to log non-critical deserialization failures and not fail the task
  implicit class ErrorOrLogger[A](val t: ErrorOr[A]) extends AnyVal {
    private def logErrors(errors: NonEmptyList[String], workflowId: WorkflowId, operation: Operation): Unit =
      logger.error(
        s"[$workflowId] Failed to parse PAPI response. Operation Id: ${operation.getName}" + s"${errors.toList.mkString(", ")}"
      )

    def fallBack: RequestContextReader[Option[A]] = Reader { case (workflowId, operation) =>
      t match {
        case Valid(s) => Option(s)
        case Invalid(f) =>
          logErrors(f, workflowId, operation)
          None
      }
    }

    def fallBackTo(to: A): RequestContextReader[A] = Reader { case (workflowId, operation) =>
      t match {
        case Valid(s) => s
        case Invalid(f) =>
          logErrors(f, workflowId, operation)
          to
      }
    }
  }
}

class ErrorReporter(machineType: Option[String],
                    wasPreemptible: Boolean,
                    executionEvents: List[ExecutionEvent],
                    zone: Option[String],
                    instanceName: Option[String],
                    actions: List[Action],
                    operation: Operation,
                    workflowId: WorkflowId
) {

  def toUnsuccessfulRunStatus(error: Status, events: List[Event]): UnsuccessfulRunStatus = {
    // If for some reason the status is null, set it as UNAVAILABLE
    val statusOption = for {
      errorValue <- Option(error)
      code <- Option(errorValue.getCode)
    } yield GStatus.fromCodeValue(code.toInt)
    val status = statusOption.getOrElse(GStatus.UNAVAILABLE)
    val builder = status match {
      case GStatus.UNAVAILABLE if wasPreemptible => Preempted.apply _
      case GStatus.ABORTED
          if wasPreemptible && Option(error.getMessage).exists(
            _.contains(PipelinesApiAsyncBackendJobExecutionActor.FailedV2Style)
          ) =>
        Preempted.apply _
      case GStatus.FAILED_PRECONDITION if isQuotaMessage(error.getMessage) =>
        QuotaFailed.apply _
      case GStatus.CANCELLED => Cancelled.apply _
      case _ => Failed.apply _
    }

    val failed: Option[String] = summaryFailure(events)
    // Reverse the list because the first failure (likely the most relevant, will appear last otherwise)
    val unexpectedExitEvents: List[String] = unexpectedExitStatusErrorStrings(events, actions).reverse

    builder(status,
            None,
            failed.toList ++ unexpectedExitEvents,
            executionEvents,
            machineType,
            zone,
            instanceName,
            Option.empty
    )
  }

  // There's maybe one FailedEvent per operation with a summary error message
  private def unexpectedExitStatusErrorStrings(events: List[Event], actions: List[Action]): List[String] =
    for {
      event <- events
      unexpectedStatusEvent <- Option(event.getUnexpectedExitStatus)
      stderr = unexpectedStatusEvent.getActionId |> stderrForAction(events)
      // Try to find the action and its tag label. -1 because action ids are 1-based
      action = actions.lift(unexpectedStatusEvent.getActionId.toInt - 1)
      labelTag = action.flatMap(actionLabelTag)
      inputNameTag = action.flatMap(actionLabelInputName)
    } yield unexpectedStatusErrorString(event, stderr, labelTag, inputNameTag)

  // It would probably be good to define a richer error structure than String, but right now that's what the backend interface expects
  private def unexpectedStatusErrorString(event: Event,
                                          stderr: Option[String],
                                          labelTag: Option[String],
                                          inputNameTag: Option[String]
  ) =
    labelTag.map("[" + _ + "] ").getOrElse("") +
      inputNameTag.map("Input name: " + _ + " - ").getOrElse("") +
      Option(event).flatMap(eventValue => Option(eventValue.getDescription)).getOrElse("") +
      stderr.map(": " + _).getOrElse("")

  // There may be one FailedEvent per operation with a summary error message
  private def summaryFailure(events: List[Event]): Option[String] =
    events.collectFirst {
      case event if event.getFailed != null => event.getFailed.getCause
    }

  // Try to find the stderr for the given action ID
  private def stderrForAction(events: List[Event])(actionId: Integer) =
    events.collectFirst {
      case event if event.getContainerStopped != null && event.getContainerStopped.getActionId == actionId =>
        event.getContainerStopped.getStderr
    }

  private def actionLabelValue(action: Action, k: String): Option[String] =
    Option(action).flatMap(actionValue => Option(actionValue.getLabels)).map(_.asScala).flatMap(_.get(k))

  private def actionLabelTag(action: Action) = actionLabelValue(action, Key.Tag)
  private def actionLabelInputName(action: Action) = actionLabelValue(action, Key.InputName)
}
